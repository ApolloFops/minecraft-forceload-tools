package net.apollofops.mc.forceloadtools;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.apollofops.mc.forceloadtools.ForceloadToolsConfig.Texture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import net.lugo.overlaylib.Overlay;
import net.lugo.overlaylib.managers.CachedOverlayManager;
import net.lugo.overlaylib.util.OverlayRendererBlockData;
import net.lugo.overlaylib.util.TextureSection;

@Environment(EnvType.CLIENT)
public class ForceloadToolsClient implements ClientModInitializer {
	/**
	 * A record that describes a single forceloaded chunk.
	 * <p>
	 * This is used to organize the internal list of forceloaded chunks.
	 *
	 * @param x
	 *                The X coordinate of the chunk.
	 * @param z
	 *                The Z coordinate of the chunk.
	 */
	public record ForceloadedChunk(int x, int z) {}

	/**
	 * The list of chunks that are currently known to be forceloaded. This gets updated every time the
	 * client recieves a forceload query result.
	 */
	private List<ForceloadedChunk> forceloadedChunks = new ArrayList<ForceloadedChunk>();

	/**
	 * Manager that handles where the overlay should get rendered.
	 */
	private final CachedOverlayManager overlayManager = new CachedOverlayManager((blockPos -> {
		Minecraft client = Minecraft.getInstance();

		// Don't render on just floating air blocks
		if (!client.level.loadedAndEntityCanStandOn(blockPos, client.player)) {
			return OverlayRendererBlockData.NO_RENDER;
		}

		// Don't render outside of the forceloaded chunks
		int chunkX = (int) Math.floor((double) blockPos.getX() / 16);
		int chunkZ = (int) Math.floor((double) blockPos.getZ() / 16);

		if (!forceloadedChunks.stream().anyMatch(obj -> (obj.x == chunkX && obj.z == chunkZ))) {
			return OverlayRendererBlockData.NO_RENDER;
		}

		// Otherwise, render a cross on every forceloaded block
		return new OverlayRendererBlockData(blockPos, 0, 0, 0, 0, TextureSection.SINGULAR);
	}));

	/**
	 * Are we currently waiting for a response from an update query?
	 * <p>
	 * This determines whether or not we allow the chat message from the response through.
	 */
	private boolean updating = false;

	/**
	 * Is the overlay currently active?
	 */
	private boolean isActive = false;

	/**
	 * The OverlayLib overlay object.
	 */
	private Overlay overlay;

	@Override
	public void onInitializeClient() {
		// Load the config
		ForceloadToolsConfig.HANDLER.load();

		// Set up message recieve hook
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			TranslatableContents translatable = null;

			// Check if the contents of the message are translatable - if so, use them
			if (message.getContents() instanceof TranslatableContents contents) {
				translatable = contents;
			}
			// Otherwise, traverse the siblings of the message and find a TranslatableContents among them
			else {
				for (Component sibling : message.getSiblings()) {
					if (sibling.getContents() instanceof TranslatableContents contents) {
						translatable = contents;
						break;
					}
				}
			}

			if (translatable != null) {
				String key = translatable.getKey();

				// Update the chunk list if we get a query response
				if (key.equals("commands.forceload.list.single") || key.equals("commands.forceload.list.multiple") || key.equals("commands.forceload.added.none")) {
					// Grab the chunklist arg from the message
					String chunkList = "";

					if (key.endsWith("single")) {
						chunkList = String.valueOf(translatable.getArgs()[1]);
					} else if (key.endsWith("multiple")) {
						chunkList = String.valueOf(translatable.getArgs()[2]);
					}

					// Try to parse out the chunks that are loaded
					Pattern pattern = Pattern.compile("\\[(\\-?\\d+)\\,\\s(\\-?\\d+)\\]");
					Matcher matcher = pattern.matcher(chunkList);

					forceloadedChunks.clear();

					while (matcher.find()) {
						if (matcher.groupCount() == 2) {
							int x = Integer.parseInt(matcher.group(1));
							int z = Integer.parseInt(matcher.group(2));

							forceloadedChunks.add(new ForceloadedChunk(x, z));
						}
					}

					for (ForceloadedChunk chunk : forceloadedChunks) {
						log(String.format("Chunk [%d, %d]", chunk.x, chunk.z));
					}

					// Clear the overlay chunks so they get reloaded
					// TODO: Be a little more selective with this, since currently it causes every chunk to get cleared
					// every time, causing chunks to flicker on update
					overlayManager.clearAll();

					// Hide the message from the chatbox
					if (updating) {
						updating = false;
						return false;
					} else {
						return true;
					}
				}

				// Send a query if we get a response that a chunk has been added or removed
				else if (key.equals("commands.forceload.added.single") || key.equals("commands.forceload.added.multiple") || key.equals("commands.forceload.removed.single") || key.equals("commands.forceload.removed.multiple") || key.equals("commands.forceload.removed.all")) {
					update();
				}
			}

			return true;
		});

		ForceloadTools.LOGGER.info("Registered forceload query hook");

		// Set up the overlay
		switchTexture(ForceloadToolsConfig.HANDLER.instance().texture);

		ForceloadToolsConfig.registerFieldCallback("texture", (value) -> {
			switchTexture((Texture) value);
		});

		ForceloadTools.LOGGER.info("Set up forceload overlay");

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("forceloadtools")
					.then(Commands.literal("update")
							.executes(context -> {
								update();
								return 1;
							}))
					.then(Commands.literal("enable")
							.executes(context -> {
								enable();
								return 1;
							}))
					.then(Commands.literal("disable")
							.executes(context -> {
								disable();
								return 1;
							})));
		});

		ForceloadTools.LOGGER.info("Set up forceload commands");

		// Disable the overlay so we get into a known state
		disable();

		// Clean up some things when joining
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			// Clear the list of forceloaded chunks
			forceloadedChunks.clear();

			// Enable/disable the overlay depending on whether or not we want to enable on startup
			if (ForceloadToolsConfig.HANDLER.instance().enableOnStartup) {
				enable();
			} else {
				// Disable the overlay
				disable();
			}
		});
	}

	/**
	 * Queries the server for what chunks are forceloaded and sets the client into the updating state.
	 */
	public void update() {
		updating = true;

		Minecraft.getInstance().player.connection.sendCommand("forceload query");
	}

	/**
	 * Enables the overlay and requests an update.
	 */
	public void enable() {
		isActive = true;
		overlay.setActive(true);
		update();
	}

	/**
	 * Disables the overlay.
	 */
	public void disable() {
		isActive = false;
		overlay.setActive(false);
	}

	/**
	 * Switches what Texture is active.
	 *
	 * @param texture
	 *                The new texture to use.
	 */
	private void switchTexture(Texture texture) {
		if (overlay != null) {
			overlay.setActive(false);
		}
		overlay = new Overlay(texture.renderer, 16, 16, overlayManager);
		overlay.setActive(isActive);
		overlay.register();
	}

	/**
	 * Logs a message to the journal and to chat.
	 *
	 * @param message
	 *                The message to log.
	 */
	public static void log(String message) {
		ForceloadTools.LOGGER.info(message);

		if (ForceloadToolsConfig.HANDLER.instance().chatLogging) {
			Minecraft client = Minecraft.getInstance();

			if (client.gui != null) {
				Component logText = Component.literal("[ForceloadTools] " + message)
						.withStyle(ChatFormatting.AQUA);

				client.gui.getChat().addClientSystemMessage(logText);
			}
		}
	}
}
