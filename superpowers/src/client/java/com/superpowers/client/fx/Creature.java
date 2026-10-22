package com.superpowers.client.fx;

import com.superpowers.client.model.Mesh;
import com.superpowers.client.model.MeshDraw;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Entity 666 rig: seven Blender parts (torso, head, jaw, upper/lower arm, upper/lower leg) chained
 * joint to joint. Model space is +X right, +Y up, +Z forward; angles are radians.
 */
public final class Creature {
	private Creature() {}

	static final float UPPER_ARM = 0.95f, LOWER_ARM = 1.12f, UPPER_LEG = 0.95f, LOWER_LEG = 1.0f;
	static final Vec3 UP = new Vec3(0, 1, 0);

	public static final class Pose {
		/** Torso pitch forward. */
		public float hunch = 0.5f;
		/** Head angles relative to the torso: pitch (+ = down), yaw (+ = right), roll. */
		public float headPitch, headYaw, headRoll;
		/** Jaw opening. */
		public float jaw = 0.1f;
		/** Arms: forward swing (world-relative, 0 = hanging), outward spread, elbow bend; index 0 = right, 1 = left. */
		public final float[] swing = new float[2], spread = {0.08f, 0.08f}, elbow = {0.15f, 0.15f};
		/** Horizontal sweep of each arm around the body's up axis (+ = out to its own side). */
		public final float[] sweep = new float[2];
		/** World-space hand targets (null = use the angles). */
		public final Vec3[] reach = new Vec3[2];
		/** Legs: forward swing at the hip and knee bend. */
		public final float[] hip = {0.2f, 0.2f}, knee = {0.35f, 0.35f};
		/** How far it is sunk into the ground (rising out of it). */
		public float sink;
		public float scale = 1f;
	}

	/** Where things are this frame, for effects that follow the creature. */
	public static final class Joints {
		public Vec3 mouth = Vec3.ZERO, head = Vec3.ZERO, eyeL = Vec3.ZERO, eyeR = Vec3.ZERO;
		public final Vec3[] hand = {Vec3.ZERO, Vec3.ZERO};
	}

	static Vec3 world(Matrix4f m, float x, float y, float z) {
		Vector3f v = m.transformPosition(x, y, z, new Vector3f());
		return new Vec3(v.x + Draw.cx, v.y + Draw.cy, v.z + Draw.cz);
	}

	/**
	 * Draws the creature standing at {@code feet} facing {@code yaw} (radians, Minecraft body yaw).
	 * {@code add} selects the additive pass (glowing eyes); returns joint positions.
	 */
	public static Joints draw(Vec3 feet, float yaw, Pose ps, float bright, boolean add, float alpha) {
		Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = fwd.cross(UP).normalize();
		Matrix4f root = MeshDraw.frame(feet.subtract(0, ps.sink, 0), right, UP, fwd, ps.scale);
		float legH = 0;
		for (int i = 0; i < 2; i++) {
			legH += UPPER_LEG * Mth.cos(ps.hip[i]) + LOWER_LEG * Mth.cos(ps.hip[i] - ps.knee[i]);
		}
		legH = legH / 2 + 0.04f;
		Matrix4f pelvis = new Matrix4f(root).translate(0, legH, 0);
		Joints j = new Joints();

		// legs
		for (int i = 0; i < 2; i++) {
			float s = i == 0 ? 1 : -1;
			Matrix4f up = new Matrix4f(pelvis).translate(s * 0.13f, 0, 0).rotateX(-ps.hip[i]).rotateZ(s * 0.04f);
			Matrix4f low = new Matrix4f(up).translate(0, -UPPER_LEG, 0).rotateX(ps.knee[i]);
			part("entity_leg_u", up.scale(s, 1, 1), bright, add, alpha);
			part("entity_leg_l", low.scale(s, 1, 1), bright, add, alpha);
		}

		Matrix4f torso = new Matrix4f(pelvis).rotateX(ps.hunch);
		part("entity_torso", torso, bright, add, alpha);

		Matrix4f head = new Matrix4f(torso).translate(0, 1.2f, 0).rotateX(ps.headPitch).rotateY(ps.headYaw).rotateZ(ps.headRoll);
		part("entity_head", head, bright, add, alpha);
		Matrix4f jaw = new Matrix4f(head).translate(0, 0.205f, 0.02f).rotateX(ps.jaw);
		part("entity_jaw", jaw, bright, add, alpha);
		j.head = world(head, 0, 0.25f, 0.05f);
		j.eyeR = world(head, 0.064f, 0.256f, 0.21f);
		j.eyeL = world(head, -0.064f, 0.256f, 0.21f);
		j.mouth = world(head, 0, 0.1f, 0.22f);

		// arms: forward kinematics, or two-bone IK toward a world target
		for (int i = 0; i < 2; i++) {
			float s = i == 0 ? 1 : -1;
			Matrix4f sh = new Matrix4f(torso).translate(s * 0.32f, 1.06f, -0.05f);
			Matrix4f up, low;
			if (ps.reach[i] != null) {
				Vector3f t = new Matrix4f(sh).invert().transformPosition(
						(float) (ps.reach[i].x - Draw.cx), (float) (ps.reach[i].y - Draw.cy), (float) (ps.reach[i].z - Draw.cz), new Vector3f());
				float d = Mth.clamp(t.length(), 0.3f, UPPER_ARM + LOWER_ARM - 0.02f);
				Vector3f dir = t.lengthSquared() > 1e-6f ? new Vector3f(t).normalize() : new Vector3f(0, -1, 0);
				float a = (float) Math.acos(Mth.clamp((UPPER_ARM * UPPER_ARM + d * d - LOWER_ARM * LOWER_ARM) / (2 * UPPER_ARM * d), -1, 1));
				float b = (float) Math.acos(Mth.clamp((LOWER_ARM * LOWER_ARM + d * d - UPPER_ARM * UPPER_ARM) / (2 * LOWER_ARM * d), -1, 1));
				Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, -1, 0), dir);
				up = new Matrix4f(sh).rotate(q).rotateX(-a);
				low = new Matrix4f(up).translate(0, -UPPER_ARM, 0).rotateX(a + b);
			} else {
				up = new Matrix4f(sh).rotateY(s * ps.sweep[i]).rotateX(-ps.hunch - ps.swing[i]).rotateZ(s * ps.spread[i]);
				low = new Matrix4f(up).translate(0, -UPPER_ARM, 0).rotateX(-ps.elbow[i]);
			}
			j.hand[i] = world(low, 0, -LOWER_ARM, 0);
			part("entity_arm_u", up.scale(s, 1, 1), bright, add, alpha);
			part("entity_arm_l", low.scale(s, 1, 1), bright, add, alpha);
		}

		if (add) {
			for (Vec3 e : new Vec3[]{j.eyeL, j.eyeR}) {
				Draw.glow(e, 0.09, 0xFF2010, 0.9f * alpha);
				Draw.glow(e, 0.03, 0xFFFFFF, alpha);
			}
		}
		return j;
	}

	static void part(String name, Matrix4f m, float bright, boolean add, float alpha) {
		Mesh mesh = Mesh.get(name);
		if (add) MeshDraw.drawGlow(mesh, m, 0, 0, alpha);
		else MeshDraw.draw(mesh, m, bright, 0, 0, alpha);
	}

	/** Idle / walk / run / fly pose from the motion of whatever carries the creature. */
	public static Pose locomotion(float time, float walkPos, float walkAmt, boolean sprint, float fly, int seed) {
		Pose p = new Pose();
		float ph = walkPos * 0.6662f;
		float amt = Mth.clamp(walkAmt, 0, 1);
		float breathe = 0.035f * Mth.sin(time * 0.07f);
		p.hunch = 0.5f + breathe + (sprint ? 0.35f * amt : 0.1f * amt);
		for (int i = 0; i < 2; i++) {
			float side = i == 0 ? 0 : (float) Math.PI;
			float step = Mth.sin(ph + side);
			p.hip[i] = 0.22f + step * (sprint ? 0.75f : 0.5f) * amt;
			p.knee[i] = 0.35f + Math.max(0, -Mth.cos(ph + side)) * (sprint ? 0.9f : 0.6f) * amt;
			// arms dangle like dead weight and swing against the legs
			p.swing[i] = -step * (sprint ? 0.25f : 0.45f) * amt + 0.05f * Mth.sin(time * 0.05f + i)
					+ (sprint ? -0.55f * amt : 0);
			p.elbow[i] = 0.12f + 0.2f * amt;
			p.spread[i] = 0.07f + 0.03f * Mth.sin(time * 0.06f + i * 2);
		}
		if (fly > 0) {
			for (int i = 0; i < 2; i++) {
				p.hip[i] = Mth.lerp(fly, p.hip[i], 0.12f + 0.08f * Mth.sin(time * 0.1f + i));
				p.knee[i] = Mth.lerp(fly, p.knee[i], 0.5f + 0.1f * Mth.sin(time * 0.13f + i));
				p.swing[i] = Mth.lerp(fly, p.swing[i], -0.2f + 0.06f * Mth.sin(time * 0.09f + i));
				p.spread[i] = Mth.lerp(fly, p.spread[i], 0.25f);
			}
			p.hunch = Mth.lerp(fly, p.hunch, 0.35f);
		}
		// the head never quite sits still: slow lolling plus sudden twitches
		float twitch = twitch(time, seed);
		p.headRoll = 0.22f * Mth.sin(time * 0.035f + seed) + 0.5f * twitch;
		p.headYaw = 0.12f * Mth.sin(time * 0.05f + seed * 2) + 0.25f * twitch;
		p.jaw = 0.12f + 0.06f * Mth.sin(time * 0.09f) + 0.35f * Math.max(0, twitch);
		return p;
	}

	/** Mostly 0, now and then snaps to +-1 for a few ticks. */
	static float twitch(float time, int seed) {
		int slot = (int) (time / 23f);
		long h = (slot * 0x9E3779B97F4A7C15L) ^ (seed * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 31;
		if ((h & 7) > 1) return 0;
		float local = time - slot * 23f;
		if (local > 7) return 0;
		float dir = (h & 8) != 0 ? 1 : -1;
		return dir * Mth.sin(Math.min(1f, local / 1.5f) * (float) Math.PI * 0.5f) * Mth.clamp((7 - local) / 3f, 0, 1);
	}
}
