package com.superpowers.client;

import com.superpowers.power.Fx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Arm poses for abilities, blended over the vanilla pose. */
public final class PlayerAnims {
	private PlayerAnims() {}

	public static void apply(HumanoidModel<?> m, LivingEntity e) {
		float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		flight(m, e, pt);
		ClientState.Anim a = ClientState.anim(e.getId());
		if (a == null) return;
		float t = Mth.clamp((ClientState.ticks + pt - a.start()) / a.duration(), 0, 1);
		// envelope: fast in, hold, ease out
		float w = Math.min(Mth.clamp(t / 0.2f, 0, 1), Mth.clamp((1 - t) / 0.3f, 0, 1));
		float thrust = Mth.sin((float) Math.PI * Math.min(1f, t * 1.4f));
		ModelPart r = m.rightArm, l = m.leftArm;
		switch (a.id()) {
			case Fx.Anim.PUNCH_R -> pose(r, thrust, -1.65f, -0.25f, 0f);
			case Fx.Anim.PUNCH_L -> pose(l, thrust, -1.65f, 0.25f, 0f);
			case Fx.Anim.UPPERCUT -> {
				float up = t < 0.35f ? t / 0.35f : 1;
				pose(r, w, Mth.lerp(up, -0.6f, -2.8f), -0.2f, 0.1f);
				pose(l, w, 0.4f, 0f, -0.3f);
			}
			case Fx.Anim.CAST_FORWARD -> {
				pose(r, w, -1.55f, -0.15f, 0f);
				pose(l, w, -1.55f, 0.15f, 0f);
			}
			case Fx.Anim.CAST_UP -> {
				pose(r, w, -2.95f, 0f, 0.15f);
				pose(l, w, -0.3f, 0f, -0.35f);
			}
			case Fx.Anim.SPREAD -> {
				float breathe = 0.08f * Mth.sin((ClientState.ticks + pt) * 0.15f);
				pose(r, w, -0.35f, 0f, 1.25f + breathe);
				pose(l, w, -0.35f, 0f, -1.25f - breathe);
			}
			case Fx.Anim.DASH -> {
				pose(r, w, 1.15f, 0f, 0.3f);
				pose(l, w, 1.15f, 0f, -0.3f);
			}
			case Fx.Anim.SLAM -> {
				float x = t < 0.4f ? -2.9f : Mth.lerp((t - 0.4f) / 0.6f, -2.9f, -0.7f);
				pose(r, w, x, -0.15f, 0f);
				pose(l, w, x, 0.15f, 0f);
			}
			case Fx.Anim.SWEEP_R, Fx.Anim.SWEEP_L, Fx.Anim.SWEEP_BOTH -> {
				// arm held out level, raking from its own side all the way across the body
				float se = Mth.clamp((t - 0.08f) / 0.6f, 0, 1);
				se = se * se * (3 - 2 * se);
				float env = Math.min(1f, t / 0.15f) * Math.min(1f, (1 - t) / 0.25f);
				if (a.id() != Fx.Anim.SWEEP_L) pose(r, env, -1.45f, Mth.lerp(se, -1.1f, 1.0f), 0.1f);
				if (a.id() != Fx.Anim.SWEEP_R) pose(l, env, -1.45f, Mth.lerp(se, 1.1f, -1.0f), -0.1f);
			}
			case Fx.Anim.CLAW -> {
				float s = Math.min(1f, t * 1.6f);
				pose(r, w, Mth.lerp(s, -2.4f, -0.5f), Mth.lerp(s, 0.7f, -0.7f), 0f);
			}
			default -> {}
		}
	}

	/** Flight poses: God flies superman style, Space surfs its meteor, winged powers trail their limbs. */
	static void flight(HumanoidModel<?> m, LivingEntity e, float pt) {
		float w = ClientState.flight(e, pt);
		if (w <= 0.001f || ClientState.powerOf(e) == com.superpowers.power.PowerType.ENTITY) return;
		float lean = ClientState.flightLean(e, pt);
		float time = ClientState.ticks + pt;
		float sway = Mth.sin(time * 0.1f);
		ModelPart ra = m.rightArm, la = m.leftArm, rl = m.rightLeg, ll = m.leftLeg;
		switch (ClientState.powerOf(e)) {
			case GOD -> {
				// hover: arms loose at the sides; moving: right fist forward, body straight as an arrow
				pose(ra, w, Mth.lerp(lean, 0.1f, -3.05f), 0f, Mth.lerp(lean, 0.25f, 0.06f));
				pose(la, w, Mth.lerp(lean, 0.1f, 0.12f), 0f, Mth.lerp(lean, -0.25f, -0.06f));
				pose(rl, w, Mth.lerp(lean, 0.12f + 0.04f * sway, 0.02f), 0f, 0.03f);
				pose(ll, w, Mth.lerp(lean, -0.04f - 0.04f * sway, 0.12f), 0f, -0.03f);
			}
			case SPACE -> {
				// surfing the meteor: staggered stance, arms out for balance
				pose(ra, w, -0.25f + 0.08f * sway, 0f, 1.05f + 0.06f * sway);
				pose(la, w, -0.1f - 0.08f * sway, 0f, -1.15f + 0.06f * sway);
				pose(rl, w, -0.3f, 0.25f, 0.18f);
				pose(ll, w, 0.3f, -0.25f, -0.18f);
			}
			default -> {
				// winged flight: arms swept back, legs trailing
				float flap = 0.06f * Mth.sin(time * 0.42f);
				pose(ra, w, Mth.lerp(lean, 0.15f, 0.5f) + flap, 0f, Mth.lerp(lean, 0.3f, 0.35f));
				pose(la, w, Mth.lerp(lean, 0.15f, 0.5f) + flap, 0f, Mth.lerp(lean, -0.3f, -0.35f));
				pose(rl, w, Mth.lerp(lean, 0.1f + 0.05f * sway, 0.28f), 0f, 0.05f);
				pose(ll, w, Mth.lerp(lean, -0.05f - 0.05f * sway, 0.42f), 0f, -0.05f);
			}
		}
		// keep looking where the player looks while the body leans
		m.head.xRot -= ClientState.flightTilt(e, pt) * Mth.DEG_TO_RAD;
		m.hat.copyFrom(m.head);
	}

	static void pose(ModelPart p, float weight, float x, float y, float z) {
		p.xRot = Mth.lerp(weight, p.xRot, x);
		p.yRot = Mth.lerp(weight, p.yRot, y);
		p.zRot = Mth.lerp(weight, p.zRot, z);
	}
}
