package net.apollofops.mc.forceloadtools.renderers;

import net.apollofops.mc.forceloadtools.ForceloadTools;
import net.lugo.overlaylib.renderers.SimpleTextureOverlayRenderer;
import net.minecraft.resources.Identifier;

public class CrossOverlayRenderer extends SimpleTextureOverlayRenderer {
	private static final Identifier CROSS_TEXTURE = Identifier.fromNamespaceAndPath(ForceloadTools.MOD_ID, "textures/cross.png");

	public CrossOverlayRenderer() {
		// TODO: Add a config option for the flicker fix
		super(CROSS_TEXTURE, false);
	}
}
