package com.superpowers.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.superpowers.Superpowers;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The SP Book as a real 3D book (Blender model) in hand, in the GUI and on the ground. */
public class TomeRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	private static final ResourceLocation WHITE = Superpowers.id("textures/misc/white.png");

	@Override
	public void render(ItemStack stack, ItemDisplayContext mode, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
		ps.pushPose();
		ps.translate(0.5, 0.5, 0.5);
		emit(Mesh.get("codex_book"), ps.last(), buffers, light, overlay);
		ps.popPose();
	}

	/** Draws a Blender mesh through the normal entity pipeline (lit, fogged, in-hand capable). */
	public static void emit(Mesh mesh, PoseStack.Pose pose, MultiBufferSource buffers, int light, int overlay) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
		float[] d = mesh.solid;
		for (int t = 0; t < d.length; t += Mesh.STRIDE * 3) {
			for (int v = 0; v < 4; v++) {
				int o = t + Math.min(v, 2) * Mesh.STRIDE;
				float e = d[o + 9];
				float boost = 1f + 0.3f * e;
				int r = (int) Math.min(255, d[o + 6] * 255 * boost);
				int g = (int) Math.min(255, d[o + 7] * 255 * boost);
				int b = (int) Math.min(255, d[o + 8] * 255 * boost);
				vc.addVertex(pose, d[o], d[o + 1], d[o + 2])
						.setColor(r, g, b, 255)
						.setUv(0.5f, 0.5f)
						.setOverlay(overlay)
						.setLight(e > 0.6f ? LightTexture.FULL_BRIGHT : light)
						.setNormal(pose, d[o + 3], d[o + 4], d[o + 5]);
			}
		}
	}
}
