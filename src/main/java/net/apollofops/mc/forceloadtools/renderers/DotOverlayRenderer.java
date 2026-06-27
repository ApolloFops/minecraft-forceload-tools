package net.apollofops.mc.forceloadtools.renderers;

import net.apollofops.mc.forceloadtools.ForceloadTools;
import net.lugo.overlaylib.renderers.SimpleTextureOverlayRenderer;
import net.minecraft.resources.Identifier;

public class DotOverlayRenderer extends SimpleTextureOverlayRenderer {
	private static final Identifier DOT_TEXTURE = Identifier.fromNamespaceAndPath(ForceloadTools.MOD_ID, "textures/dot.png");

	public DotOverlayRenderer() {
		// TODO: Add a config option for the flicker fix
		super(DOT_TEXTURE, false);
	}
}
