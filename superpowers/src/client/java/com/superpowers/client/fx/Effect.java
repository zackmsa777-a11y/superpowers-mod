package com.superpowers.client.fx;

import net.minecraft.util.Mth;

/** A client-side visual effect with a fixed lifetime. */
public abstract class Effect {
	public int age;
	public final int life;

	protected Effect(int life) {
		this.life = Math.max(1, life);
	}

	public void tick() {
		age++;
	}

	public boolean dead() {
		return age >= life;
	}

	/** Progress 0..1 including partial tick. */
	protected float t(float pt) {
		return Mth.clamp((age + pt) / life, 0f, 1f);
	}

	/** Fade-in over the first {@code in} and fade-out over the last {@code out} fraction of life. */
	protected float fade(float pt, float in, float out) {
		float t = t(pt);
		float a = in > 0 ? Math.min(1f, t / in) : 1f;
		float b = out > 0 ? Math.min(1f, (1f - t) / out) : 1f;
		return Math.max(0f, Math.min(a, b));
	}

	public static float easeOut(float t) {
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	public static float easeIn(float t) {
		return t * t * t;
	}

	/** Normal-blended geometry (dark/solid shapes). */
	public void renderAlpha(float pt) {}

	/** Additive glow geometry. */
	public void renderAdd(float pt) {}
}
