package com.superpowers.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.superpowers.Superpowers;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

import java.util.ArrayList;
import java.util.List;

/** Owns active effects and draws them (plus auras and projectiles) after translucent terrain. */
public final class FxManager {
	private FxManager() {}

	private static final List<Effect> EFFECTS = new ArrayList<>();
	private static final List<Effect> PENDING = new ArrayList<>();
	private static boolean loggedError;

	public static <T extends Effect> T add(T e) {
		PENDING.add(e);
		return e;
	}

	public static void tick() {
		EFFECTS.addAll(PENDING);
		PENDING.clear();
		EFFECTS.removeIf(e -> {
			e.tick();
			return e.dead();
		});
		Auras.tick();
		HorrorFx.tick();
		Debris.tick();
	}

	public static void clear() {
		HorrorFx.reset();
		Creature.reset();
		EFFECTS.clear();
		PENDING.clear();
		Debris.clear();
	}

	public static int count() {
		return EFFECTS.size();
	}

	public static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Draw.begin(ctx.camera());

		RenderSystem.enableBlend();
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		try {
			// pass 1: normal blending, writes depth (solid / dark shapes)
			RenderSystem.defaultBlendFunc();
			RenderSystem.depthMask(true);
			BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
			Draw.buf = bb;
			for (Effect e : EFFECTS) e.renderAlpha(pt);
			Auras.render(false, pt);
			HorrorFx.renderApparition(false, pt);
			draw(bb);

			// pass 2: additive glow, no depth writes
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
			RenderSystem.depthMask(false);
			bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
			Draw.buf = bb;
			for (Effect e : EFFECTS) e.renderAdd(pt);
			Auras.render(true, pt);
			HorrorFx.renderApparition(true, pt);
			draw(bb);
		} catch (Exception ex) {
			if (!loggedError) {
				loggedError = true;
				Superpowers.LOGGER.error("Superpowers effect rendering failed", ex);
			}
		} finally {
			Draw.buf = null;
			RenderSystem.depthMask(true);
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableBlend();
			RenderSystem.enableCull();
		}
		try {
			Creature.flush();
		} catch (Exception ex) {
			if (!loggedError) {
				loggedError = true;
				Superpowers.LOGGER.error("Superpowers creature rendering failed", ex);
			}
		}
		try {
			HorrorFx.renderProps(pt);
		} catch (Exception ex) {
			if (!loggedError) {
				loggedError = true;
				Superpowers.LOGGER.error("Superpowers prop rendering failed", ex);
			}
		}
		try {
			Debris.render(pt);
		} catch (Exception ex) {
			if (!loggedError) {
				loggedError = true;
				Superpowers.LOGGER.error("Superpowers debris rendering failed", ex);
			}
		}
	}

	private static void draw(BufferBuilder bb) {
		MeshData mesh = bb.build();
		if (mesh != null) BufferUploader.drawWithShader(mesh);
	}
}
