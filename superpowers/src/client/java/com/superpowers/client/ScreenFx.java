package com.superpowers.client;

import com.superpowers.client.fx.Draw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Camera shake, screen flashes and the sky/fog/screen tint used by ultimates. */
public final class ScreenFx {
	private ScreenFx() {}

	private static float shake;
	private static int shakeLeft, shakeTotal;

	private static int flashColor;
	private static float flashAlpha;
	private static int flashLeft, flashTotal;

	private static int tintColor;
	private static float tintStrength;
	private static int tintAge, tintTotal;

	public static void shake(float intensity, int duration) {
		float current = shakeLeft > 0 ? shake * shakeLeft / (float) shakeTotal : 0;
		if (current > intensity) return;
		shake = intensity;
		shakeLeft = shakeTotal = Math.max(1, duration);
	}

	public static void flash(int rgb, float alpha, int duration) {
		flashColor = rgb;
		flashAlpha = alpha;
		flashLeft = flashTotal = Math.max(1, duration);
	}

	public static void tint(int rgb, float strength, int duration) {
		tintColor = rgb;
		tintStrength = strength;
		tintAge = 0;
		tintTotal = Math.max(1, duration);
	}

	public static void tick() {
		if (shakeLeft > 0) shakeLeft--;
		if (flashLeft > 0) flashLeft--;
		if (tintAge < tintTotal) tintAge++;
	}

	public static void reset() {
		shakeLeft = flashLeft = 0;
		tintAge = tintTotal = 0;
	}

	/** Yaw/pitch offsets for the camera. */
	public static float[] shakeOffset(float pt) {
		if (shakeLeft <= 0) return null;
		float k = (shakeLeft - pt) / shakeTotal;
		float amp = shake * k * k * 2.2f;
		float time = ClientState.ticks + pt;
		return new float[]{
				amp * (Mth.sin(time * 2.7f) + 0.5f * Mth.sin(time * 6.1f)),
				amp * (Mth.cos(time * 3.3f) + 0.5f * Mth.sin(time * 5.3f + 1f))};
	}

	/** Current tint strength (fades in over 15 ticks and out over the last 25). */
	public static float tintAmount(float pt) {
		if (tintAge >= tintTotal) return 0;
		float t = tintAge + pt;
		return tintStrength * Mth.clamp(t / 15f, 0, 1) * Mth.clamp((tintTotal - t) / 25f, 0, 1);
	}

	public static int tintColor() {
		return tintColor;
	}

	/** Blends a color toward the tint. */
	public static Vec3 applyTint(Vec3 col, float pt) {
		float k = tintAmount(pt);
		if (k <= 0) return col;
		return new Vec3(Mth.lerp(k, col.x, Draw.r(tintColor)), Mth.lerp(k, col.y, Draw.g(tintColor)), Mth.lerp(k, col.z, Draw.b(tintColor)));
	}

	/** Full-screen overlays: tint wash, vignette and flash. */
	public static void renderOverlay(GuiGraphics g, float pt) {
		int w = g.guiWidth(), h = g.guiHeight();
		float tint = tintAmount(pt);
		if (tint > 0) {
			g.fill(0, 0, w, h, argb(tintColor, tint * 0.22f));
			vignette(g, w, h, tintColor, tint * 0.75f);
		}
		if (flashLeft > 0) {
			float k = (flashLeft - pt) / flashTotal;
			g.fill(0, 0, w, h, argb(flashColor, flashAlpha * k * k));
		}
	}

	public static void vignette(GuiGraphics g, int w, int h, int rgb, float a) {
		int steps = 12;
		for (int i = 0; i < steps; i++) {
			float k = 1f - i / (float) steps;
			int col = argb(Draw.scale(rgb, 0.35f), a * k * k * 0.35f);
			int bw = (int) (w * 0.18f * (i + 1) / steps);
			int bh = (int) (h * 0.18f * (i + 1) / steps);
			g.fill(0, 0, w, bh, col);
			g.fill(0, h - bh, w, h, col);
			g.fill(0, bh, bw, h - bh, col);
			g.fill(w - bw, bh, w, h - bh, col);
		}
	}

	public static int argb(int rgb, float a) {
		return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
	}
}
