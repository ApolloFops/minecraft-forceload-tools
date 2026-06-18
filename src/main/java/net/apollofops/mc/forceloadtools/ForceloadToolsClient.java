package net.apollofops.mc.forceloadtools;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

@Environment(EnvType.CLIENT)
public class ForceloadToolsClient implements ClientModInitializer {
	/**
	 * A record that describes a single forceloaded chunk.
	 * <p>
	 * This is used to organize the internal list of forceloaded chunks.
	 *
	 * @param x
	 *                The X coordinate of the chunk.
	 * @param y
	 *                The Y coordinate of the chunk.
	 */
	public record ForceloadedChunk(int x, int y) {}

	/**
	 * The list of chunks that are currently known to be forceloaded. This gets updated every time the
	 * client recieves a forceload query result.
	 */
	private List<ForceloadedChunk> forceloadedChunks = new ArrayList<ForceloadedChunk>();

	@Override
	public void onInitializeClient() {
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			if (message.getContents() instanceof TranslatableContents translatable) {
				String key = translatable.getKey();

				// Handle the message if it's related to forceload
				if (key.equals("commands.forceload.list.single") || key.equals("commands.forceload.list.multiple")) {
					// Grab the chunklist arg from the message
					String chunkList = "";

					if (key.endsWith("single")) {
						chunkList = String.valueOf(translatable.getArgs()[1]);
					} else if (key.endsWith("multiple")) {
						chunkList = String.valueOf(translatable.getArgs()[2]);
					}

					// Try to parse out the chunks that are loaded
					Pattern pattern = Pattern.compile("\\[(\\-?\\d+)\\,\\s(\\-?\\d+)\\]");
					// Pattern pattern = Pattern.compile("\\[(\\d+)\\,\\s(\\d+)\\]");
					Matcher matcher = pattern.matcher(chunkList);

					forceloadedChunks.clear();

					while (matcher.find()) {
						if (matcher.groupCount() == 2) {
							int x = Integer.parseInt(matcher.group(1));
							int y = Integer.parseInt(matcher.group(2));

							forceloadedChunks.add(new ForceloadedChunk(x, y));
						}
					}

					for (ForceloadedChunk chunk : forceloadedChunks) {
						log(String.format("Chunk [%d, %d]", chunk.x, chunk.y));
					}

					// Hide the message from the chatbox
					// Should probably do something to make sure that this only happens when we trigger the message
					// (maybe set a flag which gets checked and then unset here?)
					return false;
				}
			}

			return true;
		});

		ForceloadTools.LOGGER.info("Registered forceload query hook");
	}

	/**
	 * Logs a message to the journal and to chat.
	 *
	 * @param message
	 *                The message to log.
	 */
	public static void log(String message) {
		ForceloadTools.LOGGER.info(message);

		Minecraft client = Minecraft.getInstance();

		if (client.gui != null) {
			Component logText = Component.literal("[ForceloadTools] " + message)
					.withStyle(ChatFormatting.AQUA);

			client.gui.getChat().addClientSystemMessage(logText);
		}
	}
}
