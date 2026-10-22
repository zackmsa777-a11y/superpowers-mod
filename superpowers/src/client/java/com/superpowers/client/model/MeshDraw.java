package com.superpowers.client.model;

import com.superpowers.client.fx.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Draws {@link Mesh}es into the effect buffers with simple CPU lighting. */
public final class MeshDraw {
	private MeshDraw() {}

	private static final Vector3f LIGHT = new Vector3f(0.35f, 0.9f, 0.25f).normalize();
	private static final Vector3f P = new Vector3f(), N = new Vector3f();
	private static final Matrix3f NM = new Matrix3f();

	/** Model matrix placing a mesh at a world position with the given basis (camera-relative). */
	public static Matrix4f frame(Vec3 pos, Vec3 right, Vec3 up, Vec3 fwd, float scale) {
		Matrix4f m = new Matrix4f(
				(float) right.x, (float) right.y, (float) right.z, 0,
				(float) up.x, (float) up.y, (float) up.z, 0,
				(float) fwd.x, (float) fwd.y, (float) fwd.z, 0,
				(float) (pos.x - Draw.cx), (float) (pos.y - Draw.cy), (float) (pos.z - Draw.cz), 1);
		return scale == 1f ? m : m.scale(scale);
	}

	/** Basis looking along {@code fwd}. */
	public static Matrix4f facing(Vec3 pos, Vec3 fwd, float scale) {
		Vec3 f = fwd.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : fwd.normalize();
		Vec3 side = f.cross(new Vec3(0, 1, 0));
		if (side.lengthSqr() < 1e-4) side = f.cross(new Vec3(1, 0, 0));
		side = side.normalize();
		Vec3 up = side.cross(f).normalize();
		return frame(pos, side, up, f, scale);
	}

	/** 0.2..1 brightness from the world light at a position. */
	public static float brightness(Vec3 pos) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return 1f;
		int packed = LevelRenderer.getLightColor(mc.level, BlockPos.containing(pos));
		float block = LightTexture.block(packed) / 15f;
		float sky = LightTexture.sky(packed) / 15f * mc.level.getSkyDarken(1f);
		return 0.25f + 0.75f * Math.max(block, sky);
	}

	/** Solid pass: lit, emissive parts full bright. tintAmt blends the colors toward {@code tint}. */
	public static void draw(Mesh mesh, Matrix4f m, float bright, int tint, float tintAmt, float alpha) {
		emit(mesh.solid, m, bright, tint, tintAmt, alpha, false);
	}

	/** Additive pass: glow parts plus a bloom of the emissive areas. */
	public static void drawGlow(Mesh mesh, Matrix4f m, int tint, float tintAmt, float alpha) {
		emit(mesh.glow, m, 1f, tint, tintAmt, alpha, false);
		emit(mesh.solid, m, 1f, tint, tintAmt, alpha * 0.45f, true);
	}

	private static void emit(float[] d, Matrix4f m, float bright, int tint, float tintAmt, float alpha, boolean emissiveOnly) {
		if (d.length == 0 || Draw.buf == null) return;
		NM.set(m).invert().transpose();
		float tr = Draw.r(tint), tg = Draw.g(tint), tb = Draw.b(tint);
		for (int t = 0; t < d.length; t += Mesh.STRIDE * 3) {
			if (emissiveOnly && d[t + 9] < 0.3f && d[t + Mesh.STRIDE + 9] < 0.3f && d[t + Mesh.STRIDE * 2 + 9] < 0.3f) continue;
			for (int v = 0; v < 3; v++) {
				int o = t + v * Mesh.STRIDE;
				m.transformPosition(d[o], d[o + 1], d[o + 2], P);
				NM.transform(d[o + 3], d[o + 4], d[o + 5], N);
				float len = N.length();
				float diff = len > 1e-6f ? Math.abs(N.dot(LIGHT)) / len : 0.5f;
				float top = len > 1e-6f ? N.y / len * 0.5f + 0.5f : 0.5f;
				float e = d[o + 9];
				float r = d[o + 6], g = d[o + 7], b = d[o + 8];
				if (tintAmt > 0) {
					float lum = (0.3f * r + 0.59f * g + 0.11f * b) * 1.35f;
					r = Mth.lerp(tintAmt, r, Math.min(1, tr * lum));
					g = Mth.lerp(tintAmt, g, Math.min(1, tg * lum));
					b = Mth.lerp(tintAmt, b, Math.min(1, tb * lum));
				}
				float k;
				if (emissiveOnly) {
					k = e;
				} else {
					float lit = (0.36f + 0.5f * diff + 0.14f * top) * bright;
					k = Mth.lerp(e, lit, 1.15f);
				}
				Draw.buf.addVertex(P.x, P.y, P.z).setColor(Math.min(1, r * k), Math.min(1, g * k), Math.min(1, b * k), alpha);
			}
			// QUADS buffer: repeat the last vertex to make a degenerate quad
			Draw.buf.addVertex(P.x, P.y, P.z).setColor(0f, 0f, 0f, 0f);
		}
	}
}
