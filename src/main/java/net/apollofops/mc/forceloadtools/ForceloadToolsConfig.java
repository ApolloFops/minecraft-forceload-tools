package net.apollofops.mc.forceloadtools;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.Boolean;
import dev.isxander.yacl3.config.v2.api.autogen.EnumCycler;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;

import net.apollofops.mc.forceloadtools.renderers.CrossOverlayRenderer;
import net.apollofops.mc.forceloadtools.renderers.DotOverlayRenderer;

import net.fabricmc.loader.api.FabricLoader;
import net.lugo.overlaylib.OverlayRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import dev.isxander.yacl3.api.NameableEnum;
import dev.isxander.yacl3.api.OptionEventListener;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.utils.OptionUtils;

public class ForceloadToolsConfig {
	public static ConfigClassHandler<ForceloadToolsConfig> HANDLER = ConfigClassHandler.createBuilder(ForceloadToolsConfig.class)
			.id(Identifier.fromNamespaceAndPath(ForceloadTools.MOD_ID, "forceloadtools_config"))
			.serializer(config -> GsonConfigSerializerBuilder.create(config)
					.setPath(FabricLoader.getInstance().getConfigDir().resolve("forceloadtools.json5"))
					.setJson5(true)
					.build())
			.build();

	/**
	 * List of listeners attached to config objects.
	 * <p>
	 * First field is the name of the config field, second field is the list of listeners.
	 */
	private static final Map<String, List<OptionEventListener<?>>> listenersRegistry = new HashMap<>();

	public static YetAnotherConfigLib createBuilder() {
		YetAnotherConfigLib gui = HANDLER.generateGui();

		// Attach the listeners to all the options
		OptionUtils.forEachOptions(gui, option -> {
			String fieldName = findFieldNameForOption(option);
			if (fieldName != null) {
				List<OptionEventListener<?>> listeners = listenersRegistry.get(fieldName);
				if (listeners != null) {
					for (OptionEventListener<?> listener : listeners) {
						// TODO: This is evil and I hate it
						@SuppressWarnings("rawtypes")
						OptionEventListener rawListener = listener;
						option.addEventListener(rawListener);
					}
				}
			}
		});

		return gui;
	}

	/**
	 * Find the field name for an option by comparing against the translation key. The translation key
	 * format is "yacl3.config.<handler-id>.<field-name>".
	 */
	private static String findFieldNameForOption(dev.isxander.yacl3.api.Option<?> option) {
		if (option.name().getContents() instanceof TranslatableContents contents) {
			String handlerId = HANDLER.id().toString();
			String translationKey = contents.getKey();

			// Try and extract the field name from the translation key
			if (translationKey.startsWith("yacl3.config." + handlerId + ".")) {
				return translationKey.substring(("yacl3.config." + handlerId + ".").length());
			}
		}

		return null;
	}

	/**
	 * Register a callback which gets called whenever a specific field changes.
	 *
	 * @param fieldName
	 *                The name of the field to listen to.
	 * @param callback
	 *                Callback to call when the field changes.
	 */
	public static <T> void registerFieldCallback(String fieldName, Consumer<T> callback) {
		listenersRegistry.computeIfAbsent(fieldName, k -> new ArrayList<>())
				.add((option, event) -> {
					if (event == OptionEventListener.Event.STATE_CHANGE) {
						@SuppressWarnings("unchecked")
						T value = (T) option.pendingValue();
						callback.accept(value);
					}
				});
	}

	@AutoGen(category = "general")
	@Boolean(formatter = Boolean.Formatter.CUSTOM, colored = true)
	@SerialEntry
	public boolean enableOnStartup = false;

	@AutoGen(category = "general")
	@EnumCycler
	@SerialEntry
	public Texture texture = Texture.DOT;

	@AutoGen(category = "debug")
	@Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
	@SerialEntry
	public boolean chatLogging = false;

	public enum Texture implements NameableEnum {
		CROSS(new CrossOverlayRenderer()), DOT(new DotOverlayRenderer());

		public final OverlayRenderer renderer;

		Texture(OverlayRenderer renderer) {
			this.renderer = renderer;
		}

		@Override
		public Component getDisplayName() {
			return Component.translatable("forceload-tools.texture." + name().toLowerCase());
		}
	}
}
