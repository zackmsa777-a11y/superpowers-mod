package com.superpowers.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.superpowers.Superpowers;
import com.superpowers.client.model.MeshDraw;
import com.superpowers.client.model.Skin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity 666: the "Hollow Stalker" - one skinned, textured mesh (stalker.sps + stalker.png) on a 20-bone
 * skeleton, posed every frame on the CPU. Pose angles are offsets from its crouched rest pose; model space is
 * +X right, +Y up, +Z forward. Drawing is queued during the effect passes and flushed afterwards.
 */
public final class Creature {
	private Creature() {}

	public static final ResourceLocation TEXTURE = Superpowers.id("textures/entity/stalker.png");
	static final Vec3 UP = new Vec3(0, 1, 0);
	static final Vector3f LIGHT = new Vector3f(0.35f, 0.9f, 0.25f).normalize();

	public static final class Pose {
		/** Torso pitch forward (0.5 = its natural crouch). */
		public float hunch = 0.5f;
		/** Head angles relative to the neck, offsets from rest: pitch (+ = down), yaw (+ = right), roll. */
		public float headPitch, headYaw, headRoll;
		/** Jaw opening (0.1 = rest). */
		public float jaw = 0.1f;
		/** Arms, offsets from rest: forward swing, outward spread (0.08 = rest), elbow bend (0.15 = rest); 0 = +X side, 1 = -X side. */
		public final float[] swing = new float[2], spread = {0.08f, 0.08f}, elbow = {0.15f, 0.15f};
		/** Horizontal sweep of each arm around the body's up axis (+ = out to its own side). */
		public final float[] sweep = new float[2];
		/** World-space hand targets (null = use the angles). */
		public final Vec3[] reach = new Vec3[2];
		/** Legs: forward swing at the hip (0.2 = rest) and knee bend (0.35 = rest). */
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

	// ------------------------------------------------------------------ the rig

	/** Bone indices and derived rest data, resolved once from the model. */
	static final class Rig {
		final Skin sk;
		final int pelvis, spine, chest, neck, head, jaw;
		final int[] clav = new int[2], uparm = new int[2], forearm = new int[2], hand = new int[2], thigh = new int[2], shin = new int[2], foot = new int[2];
		final Vector3f[] palm = new Vector3f[2];
		final int[] headTris;
		final int[][] armTris = new int[2][];

		Rig(Skin sk) {
			this.sk = sk;
			pelvis = sk.bone("pelvis");
			spine = sk.bone("spine");
			chest = sk.bone("chest");
			neck = sk.bone("neck");
			head = sk.bone("head");
			jaw = sk.bone("jaw");
			String[] sides = {"px", "nx"};
			for (int i = 0; i < 2; i++) {
				clav[i] = sk.bone("clav_" + sides[i]);
				uparm[i] = sk.bone("uparm_" + sides[i]);
				forearm[i] = sk.bone("forearm_" + sides[i]);
				hand[i] = sk.bone("hand_" + sides[i]);
				thigh[i] = sk.bone("thigh_" + sides[i]);
				shin[i] = sk.bone("shin_" + sides[i]);
				foot[i] = sk.bone("foot_" + sides[i]);
			}
			// palm = 40% of the way from the wrist to the claw tips
			Vector3f[] tips = {new Vector3f(0.558f, 0.31f, 0.961f), new Vector3f(-0.388f, 0.264f, 0.93f)};
			for (int i = 0; i < 2; i++) palm[i] = new Vector3f(sk.pivots[hand[i]]).lerp(tips[i], 0.4f);
			headTris = tris(sk, new int[]{head, jaw, neck});
			for (int i = 0; i < 2; i++) armTris[i] = tris(sk, new int[]{forearm[i], hand[i]});
		}

		static int[] tris(Skin sk, int[] boneSet) {
			List<Integer> out = new ArrayList<>();
			for (int t = 0; t < sk.indices.length; t += 3) {
				boolean all = true;
				for (int k = 0; k < 3 && all; k++) {
					int d = sk.dominant[sk.indices[t + k]];
					boolean in = false;
					for (int b : boneSet) in |= d == b;
					all = in;
				}
				if (all) out.add(t);
			}
			return out.stream().mapToInt(Integer::intValue).toArray();
		}
	}

	private static Rig rig;
	private static boolean failed;

	static Rig rig() {
		if (rig == null && !failed) {
			Skin sk = Skin.get("stalker");
			if (sk == null) failed = true;
			else rig = new Rig(sk);
		}
		return rig;
	}

	static Matrix4f about(Vector3f p, Quaternionf q) {
		return new Matrix4f().translate(p).rotate(q).translate(-p.x, -p.y, -p.z);
	}

	/** Global (model space) bone transforms for a pose; arm IK uses {@code root} to bring world targets in. */
	static Matrix4f[] solve(Rig r, Pose ps, Matrix4f root) {
		Skin sk = r.sk;
		int n = sk.boneNames.length;
		Matrix4f[] local = new Matrix4f[n];
		for (int i = 0; i < n; i++) local[i] = new Matrix4f();
		float dh = ps.hunch - 0.5f;
		local[r.spine] = about(sk.pivots[r.spine], new Quaternionf().rotateX(dh * 0.55f));
		local[r.chest] = about(sk.pivots[r.chest], new Quaternionf().rotateX(dh * 0.45f));
		local[r.head] = about(sk.pivots[r.head], new Quaternionf().rotateY(ps.headYaw).rotateX(ps.headPitch).rotateZ(ps.headRoll));
		local[r.jaw] = about(sk.pivots[r.jaw], new Quaternionf().rotateX((ps.jaw - 0.1f) * 0.65f));
		for (int i = 0; i < 2; i++) {
			float s = i == 0 ? 1 : -1;
			float dHip = ps.hip[i] - 0.2f, dKnee = ps.knee[i] - 0.35f;
			local[r.thigh[i]] = about(sk.pivots[r.thigh[i]], new Quaternionf().rotateX(-dHip));
			local[r.shin[i]] = about(sk.pivots[r.shin[i]], new Quaternionf().rotateX(dKnee));
			local[r.foot[i]] = about(sk.pivots[r.foot[i]], new Quaternionf().rotateX(dHip - dKnee));
			local[r.uparm[i]] = about(sk.pivots[r.uparm[i]], new Quaternionf().rotateY(s * ps.sweep[i]).rotateX(-ps.swing[i]).rotateZ(s * (ps.spread[i] - 0.08f)));
			local[r.forearm[i]] = about(sk.pivots[r.forearm[i]], new Quaternionf().rotateX(-(ps.elbow[i] - 0.15f)));
		}
		Matrix4f[] g = globals(sk, local);
		boolean ik = false;
		for (int i = 0; i < 2; i++) {
			if (ps.reach[i] == null) continue;
			ik(r, i, ps.reach[i], root, g, local);
			ik = true;
		}
		return ik ? globals(sk, local) : g;
	}

	static Matrix4f[] globals(Skin sk, Matrix4f[] local) {
		Matrix4f[] g = new Matrix4f[local.length];
		for (int i = 0; i < local.length; i++) {
			int p = sk.parents[i];
			g[i] = p < 0 ? new Matrix4f(local[i]) : new Matrix4f(g[p]).mul(local[i]);
		}
		return g;
	}

	/** Two-bone IK: shoulder -> elbow -> palm reaching a world point, elbow bending the way it naturally does. */
	static void ik(Rig r, int i, Vec3 target, Matrix4f root, Matrix4f[] g, Matrix4f[] local) {
		Skin sk = r.sk;
		Vector3f t = new Matrix4f(root).invert().transformPosition(
				(float) (target.x - Draw.cx), (float) (target.y - Draw.cy), (float) (target.z - Draw.cz), new Vector3f());
		new Matrix4f(g[r.clav[i]]).invert().transformPosition(t);
		Vector3f ps = sk.pivots[r.uparm[i]], pe = sk.pivots[r.forearm[i]], ph = r.palm[i];
		Vector3f u0 = new Vector3f(pe).sub(ps), l0 = new Vector3f(ph).sub(pe);
		float l1 = u0.length(), l2 = l0.length();
		Vector3f d = new Vector3f(t).sub(ps);
		float dist = Mth.clamp(d.length(), 0.15f, l1 + l2 - 0.01f);
		Vector3f dn = d.lengthSquared() > 1e-8f ? new Vector3f(d).normalize() : new Vector3f(0, -1, 0);
		Vector3f hint = new Vector3f(u0).sub(new Vector3f(dn).mul(u0.dot(dn)));
		if (hint.lengthSquared() < 1e-6f) hint.set(i == 0 ? 0.3f : -0.3f, 0.3f, -1f);
		hint.normalize();
		float a = (float) Math.acos(Mth.clamp((l1 * l1 + dist * dist - l2 * l2) / (2 * l1 * dist), -1, 1));
		Vector3f un = new Vector3f(dn).mul(Mth.cos(a)).add(new Vector3f(hint).mul(Mth.sin(a))).normalize();
		Quaternionf qu = new Quaternionf().rotationTo(new Vector3f(u0).normalize(), un);
		Vector3f elbow = new Vector3f(ps).add(new Vector3f(un).mul(l1));
		Vector3f lt = new Vector3f(ps).add(new Vector3f(dn).mul(dist)).sub(elbow).normalize();
		Vector3f ltLocal = new Quaternionf(qu).conjugate().transform(lt, new Vector3f());
		Quaternionf qf = new Quaternionf().rotationTo(new Vector3f(l0).normalize(), ltLocal);
		local[r.uparm[i]] = about(ps, qu);
		local[r.forearm[i]] = about(pe, qf);
	}

	static Vec3 world(Matrix4f m, Vector3f p) {
		Vector3f v = m.transformPosition(p, new Vector3f());
		return new Vec3(v.x + Draw.cx, v.y + Draw.cy, v.z + Draw.cz);
	}

	// ------------------------------------------------------------------ drawing in the world

	record Job(Matrix4f[] skin, float bright, float alpha) {}

	private static final List<Job> JOBS = new ArrayList<>();

	/**
	 * Poses the creature standing at {@code feet} facing {@code yaw} (radians, Minecraft body yaw). The solid
	 * pass queues the skinned mesh; the additive pass draws the eyes. Returns joint positions either way.
	 */
	public static Joints draw(Vec3 feet, float yaw, Pose ps, float bright, boolean add, float alpha) {
		Joints j = new Joints();
		Rig r = rig();
		if (r == null) return j;
		Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = fwd.cross(UP).normalize();
		Matrix4f root = MeshDraw.frame(feet.subtract(0, ps.sink, 0), right, UP, fwd, ps.scale);
		Matrix4f[] g = solve(r, ps, root);
		Matrix4f[] skin = new Matrix4f[g.length];
		for (int i = 0; i < g.length; i++) skin[i] = new Matrix4f(root).mul(g[i]);
		Skin sk = r.sk;
		Skin.Socket eyeA = sk.sockets.get("eye_px"), eyeB = sk.sockets.get("eye_nx"), mouth = sk.sockets.get("mouth");
		if (eyeA != null) j.eyeR = world(skin[eyeA.bone()], eyeA.pos());
		if (eyeB != null) j.eyeL = world(skin[eyeB.bone()], eyeB.pos());
		if (mouth != null) j.mouth = world(skin[mouth.bone()], mouth.pos());
		j.head = world(skin[r.head], new Vector3f(sk.pivots[r.head]).add(0, 0.2f, 0.15f));
		for (int i = 0; i < 2; i++) j.hand[i] = world(skin[r.hand[i]], r.palm[i]);
		if (add) {
			for (Vec3 e : new Vec3[]{j.eyeL, j.eyeR}) {
				Draw.glow(e, 0.08 * ps.scale, 0xFF2010, 0.85f * alpha);
				Draw.glow(e, 0.025 * ps.scale, 0xFFFFFF, alpha);
			}
		} else {
			JOBS.add(new Job(skin, bright, alpha));
		}
		return j;
	}

	/** Draws every creature queued this frame (called after the effect passes, camera rotation in the model-view). */
	public static void flush() {
		if (JOBS.isEmpty()) return;
		Rig r = rig();
		if (r == null) {
			JOBS.clear();
			return;
		}
		Skin sk = r.sk;
		int nv = sk.vertCount;
		float[] out = new float[nv * 3];
		float[] light = new float[nv];
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, TEXTURE);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		try {
			for (Job job : JOBS) {
				skinVerts(sk, job.skin, out, light, job.bright, LIGHT, true);
				BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
				for (int idx : sk.indices) {
					float k = light[idx];
					bb.addVertex(out[idx * 3], out[idx * 3 + 1], out[idx * 3 + 2])
							.setUv(sk.uv[idx * 2], sk.uv[idx * 2 + 1])
							.setColor(Math.min(1, k), Math.min(1, k), Math.min(1, k), job.alpha);
				}
				MeshData mesh = bb.build();
				if (mesh != null) BufferUploader.drawWithShader(mesh);
			}
		} finally {
			JOBS.clear();
			RenderSystem.enableCull();
			RenderSystem.disableBlend();
		}
	}

	/** Linear blend skinning of every vertex; writes positions and a lighting factor. */
	static void skinVerts(Skin sk, Matrix4f[] skin, float[] out, float[] light, float bright, Vector3f lightDir, boolean world) {
		Vector3f p = new Vector3f(), n = new Vector3f(), acc = new Vector3f(), nacc = new Vector3f();
		Matrix3f[] nm = new Matrix3f[skin.length];
		for (int i = 0; i < skin.length; i++) nm[i] = new Matrix3f(skin[i]).invert().transpose();
		for (int v = 0; v < sk.vertCount; v++) {
			acc.zero();
			nacc.zero();
			for (int k = 0; k < 4; k++) {
				float w = sk.weights[v * 4 + k];
				if (w <= 0) continue;
				int b = sk.bones[v * 4 + k];
				skin[b].transformPosition(sk.pos[v * 3], sk.pos[v * 3 + 1], sk.pos[v * 3 + 2], p);
				acc.add(p.mul(w));
				nm[b].transform(sk.nrm[v * 3], sk.nrm[v * 3 + 1], sk.nrm[v * 3 + 2], n);
				nacc.add(n.mul(w));
			}
			out[v * 3] = acc.x;
			out[v * 3 + 1] = acc.y;
			out[v * 3 + 2] = acc.z;
			float len = nacc.length();
			float diff = len > 1e-6f ? Math.abs(nacc.dot(lightDir)) / len : 0.5f;
			float top = len > 1e-6f ? nacc.y / len * 0.5f + 0.5f : 0.5f;
			light[v] = world ? (0.36f + 0.5f * diff + 0.14f * top) * bright : (0.32f + 0.75f * diff);
		}
	}

	// ------------------------------------------------------------------ the face up close (GUI)

	/** Its head and jaw, drawn in screen space: the jumpscare and the SP Book emblem. */
	public static void renderGuiHead(GuiGraphics g, float cx, float cy, float scale, float jaw, float yaw, float roll, float alpha) {
		Rig r = rig();
		if (r == null) return;
		Skin sk = r.sk;
		Pose ps = new Pose();
		ps.jaw = jaw;
		Matrix4f[] bones = solve(r, ps, new Matrix4f());
		Vector3f c = new Vector3f(sk.pivots[r.head]).add(0, 0.14f, 0.16f);
		Matrix4f view = new Matrix4f().rotateY(yaw).rotateZ(roll).translate(-c.x, -c.y, -c.z);
		Matrix4f[] skin = new Matrix4f[bones.length];
		for (int i = 0; i < bones.length; i++) skin[i] = new Matrix4f(view).mul(bones[i]);
		float[] out = new float[sk.vertCount * 3];
		float[] light = new float[sk.vertCount];
		skinVerts(sk, skin, out, light, 1f, new Vector3f(-0.45f, 0.65f, 0.65f).normalize(), false);
		int[] tris = r.headTris.clone();
		Integer[] order = new Integer[tris.length];
		for (int i = 0; i < tris.length; i++) order[i] = tris[i];
		java.util.Arrays.sort(order, (a, b) -> Float.compare(depth(sk, out, a), depth(sk, out, b)));

		g.flush();
		Matrix4f gm = g.pose().last().pose();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest();
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, TEXTURE);
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
		for (int t : order) {
			for (int k = 0; k < 3; k++) {
				int v = sk.indices[t + k];
				float l = Math.min(1.1f, light[v]);
				bb.addVertex(gm, cx + out[v * 3] * scale, cy - out[v * 3 + 1] * scale, 50)
						.setUv(sk.uv[v * 2], sk.uv[v * 2 + 1])
						.setColor(Math.min(1, l), Math.min(1, l), Math.min(1, l), alpha);
			}
		}
		MeshData mesh = bb.build();
		if (mesh != null) BufferUploader.drawWithShader(mesh);
		RenderSystem.enableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.defaultBlendFunc();
	}

	static float depth(Skin sk, float[] out, int t) {
		return out[sk.indices[t] * 3 + 2] + out[sk.indices[t + 1] * 3 + 2] + out[sk.indices[t + 2] * 3 + 2];
	}

	// ------------------------------------------------------------------ first person

	/**
	 * One forearm + clawed hand in the hand-rendering pose space: elbow at the origin, forearm pointing down -Y.
	 * {@code side} 0 = the creature's +X arm (the player's right).
	 */
	public static void renderFirstPersonArm(PoseStack ps, net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay, int side) {
		Rig r = rig();
		if (r == null) return;
		Skin sk = r.sk;
		Vector3f pe = sk.pivots[r.forearm[side]];
		Vector3f dir = new Vector3f(r.palm[side]).sub(pe).normalize();
		Quaternionf q = new Quaternionf().rotationTo(dir, new Vector3f(0, -1, 0));
		PoseStack.Pose pose = ps.last();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		Vector3f p = new Vector3f(), n = new Vector3f();
		for (int t : r.armTris[side]) {
			for (int k = 0; k < 4; k++) {
				int v = sk.indices[t + Math.min(k, 2)];
				p.set(sk.pos[v * 3], sk.pos[v * 3 + 1], sk.pos[v * 3 + 2]).sub(pe);
				q.transform(p);
				q.transform(n.set(sk.nrm[v * 3], sk.nrm[v * 3 + 1], sk.nrm[v * 3 + 2]));
				vc.addVertex(pose, p.x, p.y, p.z)
						.setColor(255, 255, 255, 255)
						.setUv(sk.uv[v * 2], sk.uv[v * 2 + 1])
						.setOverlay(overlay)
						.setLight(light)
						.setNormal(pose, n.x, n.y, n.z);
			}
		}
	}

	// ------------------------------------------------------------------ procedural motion

	/** Idle / walk / run / fly pose from the motion of whatever carries the creature. */
	public static Pose locomotion(float time, float walkPos, float walkAmt, boolean sprint, float fly, int seed) {
		Pose p = new Pose();
		float ph = walkPos * 0.6662f;
		float amt = Mth.clamp(walkAmt, 0, 1);
		float breathe = 0.035f * Mth.sin(time * 0.07f);
		p.hunch = 0.5f + breathe + (sprint ? 0.25f * amt : 0.08f * amt);
		for (int i = 0; i < 2; i++) {
			float side = i == 0 ? 0 : (float) Math.PI;
			float step = Mth.sin(ph + side);
			p.hip[i] = 0.2f + step * (sprint ? 0.6f : 0.42f) * amt;
			p.knee[i] = 0.35f + Math.max(0, -Mth.cos(ph + side)) * (sprint ? 0.8f : 0.55f) * amt;
			// the long arms drag and swing against the legs
			p.swing[i] = -step * (sprint ? 0.2f : 0.3f) * amt + 0.04f * Mth.sin(time * 0.05f + i)
					+ (sprint ? -0.35f * amt : 0);
			p.elbow[i] = 0.15f + 0.12f * amt + 0.04f * Mth.sin(time * 0.06f + i);
			p.spread[i] = 0.08f + 0.03f * Mth.sin(time * 0.06f + i * 2);
		}
		if (fly > 0) {
			// floating: legs hang, arms spread and drift
			for (int i = 0; i < 2; i++) {
				p.hip[i] = Mth.lerp(fly, p.hip[i], -0.15f + 0.08f * Mth.sin(time * 0.1f + i));
				p.knee[i] = Mth.lerp(fly, p.knee[i], 0.05f + 0.1f * Mth.sin(time * 0.13f + i));
				p.swing[i] = Mth.lerp(fly, p.swing[i], 0.25f + 0.08f * Mth.sin(time * 0.09f + i));
				p.spread[i] = Mth.lerp(fly, p.spread[i], 0.35f);
			}
			p.hunch = Mth.lerp(fly, p.hunch, 0.3f);
		}
		// the head never quite sits still: slow lolling plus sudden twitches
		float twitch = twitch(time, seed);
		p.headRoll = 0.18f * Mth.sin(time * 0.035f + seed) + 0.45f * twitch;
		p.headYaw = 0.1f * Mth.sin(time * 0.05f + seed * 2) + 0.25f * twitch;
		p.jaw = 0.1f + 0.08f * Mth.sin(time * 0.09f) + 0.4f * Math.max(0, twitch);
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

	public static void reset() {
		JOBS.clear();
		rig = null;
		failed = false;
	}
}
