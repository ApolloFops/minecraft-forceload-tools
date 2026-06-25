package net.apollofops.mc.forceloadtools;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.Boolean;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import dev.isxander.yacl3.api.YetAnotherConfigLib;

public class ForceloadToolsConfig {
	public static ConfigClassHandler<ForceloadToolsConfig> HANDLER = ConfigClassHandler.createBuilder(ForceloadToolsConfig.class)
			.id(Identifier.fromNamespaceAndPath(ForceloadTools.MOD_ID, "forceloadtools_config"))
			.serializer(config -> GsonConfigSerializerBuilder.create(config)
					.setPath(FabricLoader.getInstance().getConfigDir().resolve("forceloadtools.json5"))
					.setJson5(true)
					.build())
			.build();

	public static YetAnotherConfigLib createBuilder() {
		return HANDLER.generateGui();
	}

	@AutoGen(category = "general")
	@Boolean(formatter = Boolean.Formatter.CUSTOM, colored = true)
	@SerialEntry
	public boolean enableOnStartup = false;
}
