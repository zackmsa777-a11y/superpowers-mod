package com.superpowers.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Immediate-mode 3D geometry for effects. Everything is emitted as POSITION_COLOR quads in world space;
 * vertices are made camera-relative here. Cull is off, so winding doesn't matter.
 */
public final class Draw {
	private Draw() {}

	public static VertexConsumer buf;
	public static double cx, cy, cz;
	public static Vec3 camPos = Vec3.ZERO;
	/** Camera basis in world space. */
	public static Vec3 camLeft = new Vec3(1, 0, 0), camUp = new Vec3(0, 1, 0), camLook = new Vec3(0, 0, 1);

	public static void begin(Camera camera) {
		Vec3 p = camera.getPosition();
		camPos = p;
		cx = p.x;
		cy = p.y;
		cz = p.z;
		Vector3f l = camera.getLeftVector(), u = camera.getUpVector(), f = camera.getLookVector();
		camLeft = new Vec3(l.x(), l.y(), l.z());
		camUp = new Vec3(u.x(), u.y(), u.z());
		camLook = new Vec3(f.x(), f.y(), f.z());
	}

	// ------------------------------------------------------------------ colors

	public static float r(int rgb) {
		return ((rgb >> 16) & 255) / 255f;
	}

	public static float g(int rgb) {
		return ((rgb >> 8) & 255) / 255f;
	}

	public static float b(int rgb) {
		return (rgb & 255) / 255f;
	}

	public static int mix(int c1, int c2, float t) {
		t = Mth.clamp(t, 0, 1);
		int r = (int) Mth.lerp(t, (c1 >> 16) & 255, (c2 >> 16) & 255);
		int g = (int) Mth.lerp(t, (c1 >> 8) & 255, (c2 >> 8) & 255);
		int b = (int) Mth.lerp(t, c1 & 255, c2 & 255);
		return (r << 16) | (g << 8) | b;
	}

	public static int scale(int rgb, float k) {
		int r = Math.min(255, (int) (((rgb >> 16) & 255) * k));
		int g = Math.min(255, (int) (((rgb >> 8) & 255) * k));
		int b = Math.min(255, (int) ((rgb & 255) * k));
		return (r << 16) | (g << 8) | b;
	}

	// ------------------------------------------------------------------ vertices

	public static void v(double x, double y, double z, int rgb, float a) {
		buf.addVertex((float) (x - cx), (float) (y - cy), (float) (z - cz)).setColor(r(rgb), g(rgb), b(rgb), Mth.clamp(a, 0f, 1f));
	}

	public static void v(Vec3 p, int rgb, float a) {
		v(p.x, p.y, p.z, rgb, a);
	}

	public static void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float aa, float ab, float ac, float ad) {
		v(a, rgb, aa);
		v(b, rgb, ab);
		v(c, rgb, ac);
		v(d, rgb, ad);
	}

	public static void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float alpha) {
		quad(a, b, c, d, rgb, alpha, alpha, alpha, alpha);
	}

	public static void tri(Vec3 a, Vec3 b, Vec3 c, int rgb, float aa, float ab, float ac) {
		v(a, rgb, aa);
		v(b, rgb, ab);
		v(c, rgb, ac);
		v(c, rgb, ac);
	}

	// ------------------------------------------------------------------ basis helpers

	/** Two unit vectors perpendicular to n (and each other). */
	public static Vec3[] basis(Vec3 n) {
		n = n.normalize();
		Vec3 helper = Math.abs(n.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
		Vec3 u = helper.cross(n).normalize();
		Vec3 w = n.cross(u).normalize();
		return new Vec3[]{u, w};
	}

	public static Vec3 onCircle(Vec3 c, Vec3 u, Vec3 w, double r, double ang) {
		return c.add(u.scale(Math.cos(ang) * r)).add(w.scale(Math.sin(ang) * r));
	}

	// ------------------------------------------------------------------ primitives

	/** Soft camera-facing glow blob. */
	public static void glow(Vec3 c, double radius, int rgb, float alpha) {
		int n = 18;
		double mid = 0.3;
		for (int i = 0; i < n; i++) {
			double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
			Vec3 m0 = onCircle(c, camLeft, camUp, radius * mid, a0), m1 = onCircle(c, camLeft, camUp, radius * mid, a1);
			Vec3 o0 = onCircle(c, camLeft, camUp, radius, a0), o1 = onCircle(c, camLeft, camUp, radius, a1);
			quad(c, c, m0, m1, rgb, alpha, alpha, alpha * 0.55f, alpha * 0.55f);
			quad(m0, m1, o1, o0, rgb, alpha * 0.55f, alpha * 0.55f, 0, 0);
		}
	}

	/** Flat ring band in the plane perpendicular to n, alpha from inner edge a0 to outer edge a1. */
	public static void annulus(Vec3 c, Vec3 n, double r0, double r1, int rgb, float a0, float a1, int segs, double rot) {
		Vec3[] uw = basis(n);
		for (int i = 0; i < segs; i++) {
			double t0 = rot + i * Math.PI * 2 / segs, t1 = rot + (i + 1) * Math.PI * 2 / segs;
			quad(onCircle(c, uw[0], uw[1], r0, t0), onCircle(c, uw[0], uw[1], r0, t1),
					onCircle(c, uw[0], uw[1], r1, t1), onCircle(c, uw[0], uw[1], r1, t0), rgb, a0, a0, a1, a1);
		}
	}

	/** Soft glowing circle line of radius r and width w. */
	public static void ring(Vec3 c, Vec3 n, double r, double w, int rgb, float alpha, int segs) {
		annulus(c, n, Math.max(0, r - w / 2), r, rgb, 0, alpha, segs, 0);
		annulus(c, n, r, r + w / 2, rgb, alpha, 0, segs, 0);
	}

	/** Filled disc fading out to the edge. */
	public static void disc(Vec3 c, Vec3 n, double r, int rgb, float center, float edge, int segs) {
		annulus(c, n, 0, r, rgb, center, edge, segs, 0);
	}

	/** Camera-facing ribbon between a and b with soft edges. */
	public static void beam(Vec3 a, Vec3 b, double wa, double wb, int rgb, float aa, float ab) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1e-8) return;
		Vec3 mid = a.add(b).scale(0.5);
		Vec3 side = dir.cross(camPos.subtract(mid));
		if (side.lengthSqr() < 1e-8) side = dir.cross(camUp);
		side = side.normalize();
		Vec3 sa = side.scale(wa / 2), sb = side.scale(wb / 2);
		quad(a.subtract(sa), a, b, b.subtract(sb), rgb, 0, aa, ab, 0);
		quad(a, a.add(sa), b.add(sb), b, rgb, aa, 0, 0, ab);
	}

	public static void beam(Vec3 a, Vec3 b, double w, int rgb, float alpha) {
		beam(a, b, w, w, rgb, alpha, alpha);
	}

	/** Glowing beam with a hot white core. */
	public static void hotBeam(Vec3 a, Vec3 b, double w, int rgb, float alpha) {
		beam(a, b, w * 2.2, rgb, alpha * 0.35f);
		beam(a, b, w, rgb, alpha * 0.8f);
		beam(a, b, w * 0.35, 0xFFFFFF, alpha);
	}

	/** Line lying flat in the plane perpendicular to n. */
	public static void flatLine(Vec3 p, Vec3 q, Vec3 n, double w, int rgb, float alpha) {
		Vec3 side = q.subtract(p).cross(n);
		if (side.lengthSqr() < 1e-8) return;
		side = side.normalize().scale(w / 2);
		quad(p.subtract(side), p, q, q.subtract(side), rgb, 0, alpha, alpha, 0);
		quad(p, p.add(side), q.add(side), q, rgb, alpha, 0, 0, alpha);
	}

	/** UV sphere; alpha depends on facing (bright center, edge alpha at the rim) for a volumetric look. */
	public static void sphere(Vec3 c, double r, int rgb, float center, float edge, int lat, int lon) {
		Vec3[][] pts = new Vec3[lat + 1][lon + 1];
		float[][] al = new float[lat + 1][lon + 1];
		for (int i = 0; i <= lat; i++) {
			double th = Math.PI * i / lat;
			for (int j = 0; j <= lon; j++) {
				double ph = Math.PI * 2 * j / lon;
				Vec3 nrm = new Vec3(Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph));
				Vec3 p = c.add(nrm.scale(r));
				pts[i][j] = p;
				Vec3 toCam = camPos.subtract(p);
				double d = toCam.lengthSqr() < 1e-6 ? 1 : Math.abs(nrm.dot(toCam.normalize()));
				al[i][j] = (float) Mth.lerp(Math.pow(d, 1.5), edge, center);
			}
		}
		for (int i = 0; i < lat; i++) {
			for (int j = 0; j < lon; j++) {
				quad(pts[i][j], pts[i + 1][j], pts[i + 1][j + 1], pts[i][j + 1], rgb,
						al[i][j], al[i + 1][j], al[i + 1][j + 1], al[i][j + 1]);
			}
		}
	}

	/** Flat-shaded solid sphere (for the alpha pass). */
	public static void solidSphere(Vec3 c, double r, int rgb, float alpha, int lat, int lon) {
		Vec3 light = new Vec3(0.3, 1, 0.2).normalize();
		for (int i = 0; i < lat; i++) {
			double th0 = Math.PI * i / lat, th1 = Math.PI * (i + 1) / lat;
			for (int j = 0; j < lon; j++) {
				double ph0 = Math.PI * 2 * j / lon, ph1 = Math.PI * 2 * (j + 1) / lon;
				Vec3 n00 = sph(th0, ph0), n10 = sph(th1, ph0), n11 = sph(th1, ph1), n01 = sph(th0, ph1);
				Vec3 nm = n00.add(n11).normalize();
				float shade = (float) (0.55 + 0.45 * Math.max(0, nm.dot(light)));
				quad(c.add(n00.scale(r)), c.add(n10.scale(r)), c.add(n11.scale(r)), c.add(n01.scale(r)), scale(rgb, shade), alpha);
			}
		}
	}

	private static Vec3 sph(double th, double ph) {
		return new Vec3(Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph));
	}

	/** Open tube along {@code axis}; radius and alpha interpolate from bottom to top. */
	public static void tube(Vec3 base, Vec3 axis, double h, double r0, double r1, int rgb, float a0, float a1, int segs, double twist) {
		Vec3 n = axis.normalize();
		Vec3[] uw = basis(n);
		Vec3 top = base.add(n.scale(h));
		for (int i = 0; i < segs; i++) {
			double t0 = i * Math.PI * 2 / segs, t1 = (i + 1) * Math.PI * 2 / segs;
			Vec3 b0 = onCircle(base, uw[0], uw[1], r0, t0), b1 = onCircle(base, uw[0], uw[1], r0, t1);
			Vec3 c0 = onCircle(top, uw[0], uw[1], r1, t0 + twist), c1 = onCircle(top, uw[0], uw[1], r1, t1 + twist);
			// fade the side of the tube facing the camera edge-on less than the silhouette
			quad(b0, b1, c1, c0, rgb, a0, a0, a1, a1);
		}
	}

	/** Cone from a base circle to a tip; flat-shaded when {@code shade}. */
	public static void cone(Vec3 base, Vec3 tip, double r, int rgb, float alpha, int segs, boolean shade) {
		Vec3 axis = tip.subtract(base);
		Vec3[] uw = basis(axis);
		Vec3 light = new Vec3(0.3, 1, 0.2).normalize();
		for (int i = 0; i < segs; i++) {
			double t0 = i * Math.PI * 2 / segs, t1 = (i + 1) * Math.PI * 2 / segs;
			Vec3 p0 = onCircle(base, uw[0], uw[1], r, t0), p1 = onCircle(base, uw[0], uw[1], r, t1);
			int col = rgb;
			if (shade) {
				Vec3 nrm = p1.subtract(p0).cross(tip.subtract(p0)).normalize();
				Vec3 outward = p0.add(p1).scale(0.5).subtract(base);
				if (nrm.dot(outward) < 0) nrm = nrm.scale(-1);
				col = scale(rgb, (float) (0.45 + 0.75 * Math.max(0, nrm.dot(light))));
			}
			tri(p0, p1, tip, col, alpha, alpha, alpha);
		}
	}

	/** Crescent slash arc in the (u, w) plane around c. Head at a1 is bright, tail at a0 fades. */
	public static void crescent(Vec3 c, Vec3 u, Vec3 w, double radius, double a0, double a1, double thick, int rgb, float alpha, int segs) {
		for (int i = 0; i < segs; i++) {
			double s0 = (double) i / segs, s1 = (double) (i + 1) / segs;
			double ang0 = Mth.lerp(s0, a0, a1), ang1 = Mth.lerp(s1, a0, a1);
			double tk0 = thick * Math.pow(Math.sin(Math.PI * Math.min(1, s0 * 1.15)), 0.6);
			double tk1 = thick * Math.pow(Math.sin(Math.PI * Math.min(1, s1 * 1.15)), 0.6);
			float f0 = (float) (alpha * s0), f1 = (float) (alpha * s1);
			Vec3 d0 = u.scale(Math.cos(ang0)).add(w.scale(Math.sin(ang0)));
			Vec3 d1 = u.scale(Math.cos(ang1)).add(w.scale(Math.sin(ang1)));
			Vec3 in0 = c.add(d0.scale(radius - tk0 * 0.5)), mid0 = c.add(d0.scale(radius)), out0 = c.add(d0.scale(radius + tk0 * 0.5));
			Vec3 in1 = c.add(d1.scale(radius - tk1 * 0.5)), mid1 = c.add(d1.scale(radius)), out1 = c.add(d1.scale(radius + tk1 * 0.5));
			quad(in0, mid0, mid1, in1, rgb, 0, f0, f1, 0);
			quad(mid0, out0, out1, mid1, rgb, f0, f0 * 0.3f, f1 * 0.3f, f1);
			// white hot edge
			Vec3 e0 = c.add(d0.scale(radius + tk0 * 0.15)), e1 = c.add(d1.scale(radius + tk1 * 0.15));
			quad(mid0, e0, e1, mid1, 0xFFFFFF, f0 * 0.8f, 0, 0, f1 * 0.8f);
		}
	}

	/** Four-point twinkle star. */
	public static void sparkle(Vec3 c, double size, int rgb, float alpha) {
		Vec3 l = camLeft.scale(size), u = camUp.scale(size);
		Vec3 l2 = camLeft.scale(size * 0.12), u2 = camUp.scale(size * 0.12);
		quad(c.add(l), c.add(u2), c.subtract(l), c.subtract(u2), rgb, 0, alpha, 0, alpha);
		quad(c.add(u), c.add(l2), c.subtract(u), c.subtract(l2), rgb, 0, alpha, 0, alpha);
		glow(c, size * 0.6, rgb, alpha * 0.6f);
	}

	/** Star polygon {points/step} lying in a plane (pentagram = 5/2). */
	public static void polygram(Vec3 c, Vec3 n, double r, int points, int step, double w, double rot, int rgb, float alpha) {
		Vec3[] uw = basis(n);
		for (int i = 0; i < points; i++) {
			Vec3 p = onCircle(c, uw[0], uw[1], r, rot + i * Math.PI * 2 / points);
			Vec3 q = onCircle(c, uw[0], uw[1], r, rot + ((i + step) % points) * Math.PI * 2 / points);
			flatLine(p, q, n, w, rgb, alpha);
		}
	}

	/** Jagged lightning points between a and b. */
	public static List<Vec3> boltPoints(Vec3 a, Vec3 b, long seed, double jitter, int segments) {
		RandomSource rnd = RandomSource.create(seed);
		List<Vec3> pts = new ArrayList<>();
		Vec3 d = b.subtract(a);
		Vec3[] uw = basis(d);
		for (int i = 0; i <= segments; i++) {
			double t = (double) i / segments;
			Vec3 p = a.add(d.scale(t));
			if (i > 0 && i < segments) {
				double k = jitter * Math.sin(Math.PI * t);
				p = p.add(uw[0].scale((rnd.nextDouble() - 0.5) * 2 * k)).add(uw[1].scale((rnd.nextDouble() - 0.5) * 2 * k));
			}
			pts.add(p);
		}
		return pts;
	}

	public static void bolt(Vec3 a, Vec3 b, long seed, double width, int rgb, float alpha, int branches) {
		double len = a.distanceTo(b);
		List<Vec3> pts = boltPoints(a, b, seed, Math.min(3.5, len * 0.12), Math.max(6, (int) (len * 1.2)));
		for (int i = 0; i < pts.size() - 1; i++) {
			beam(pts.get(i), pts.get(i + 1), width * 3, rgb, alpha * 0.35f);
			beam(pts.get(i), pts.get(i + 1), width, 0xFFFFFF, alpha);
		}
		RandomSource rnd = RandomSource.create(seed * 31 + 7);
		for (int k = 0; k < branches && pts.size() > 4; k++) {
			int idx = 1 + rnd.nextInt(pts.size() - 3);
			Vec3 s = pts.get(idx);
			Vec3 dir = b.subtract(a).normalize().add((rnd.nextDouble() - 0.5) * 1.6, (rnd.nextDouble() - 0.5) * 0.6, (rnd.nextDouble() - 0.5) * 1.6);
			Vec3 e = s.add(dir.normalize().scale(len * (0.15 + rnd.nextDouble() * 0.2)));
			List<Vec3> bp = boltPoints(s, e, seed + k * 101L, len * 0.04, 5);
			for (int i = 0; i < bp.size() - 1; i++) {
				beam(bp.get(i), bp.get(i + 1), width * 1.6, rgb, alpha * 0.3f);
				beam(bp.get(i), bp.get(i + 1), width * 0.5, 0xFFFFFF, alpha * 0.7f);
			}
		}
	}

	/** Magic circle on a plane: rings, a star polygon and rune ticks. */
	public static void sigil(Vec3 c, Vec3 n, double r, double rot, int rgb, float alpha, int points, int step) {
		ring(c, n, r, r * 0.04, rgb, alpha, 64);
		ring(c, n, r * 0.92, r * 0.02, rgb, alpha * 0.8f, 64);
		ring(c, n, r * 0.55, r * 0.025, rgb, alpha * 0.9f, 48);
		polygram(c, n, r * 0.9, points, step, r * 0.025, rot, rgb, alpha);
		polygram(c, n, r * 0.55, points, 1, r * 0.018, -rot * 1.5, rgb, alpha * 0.7f);
		Vec3[] uw = basis(n);
		int ticks = 36;
		for (int i = 0; i < ticks; i++) {
			double a = -rot * 0.7 + i * Math.PI * 2 / ticks;
			double len = (i % 3 == 0) ? 0.07 : 0.035;
			Vec3 p = onCircle(c, uw[0], uw[1], r * 0.94, a), q = onCircle(c, uw[0], uw[1], r * (0.94 + len), a);
			flatLine(p, q, n, r * 0.012, rgb, alpha * 0.9f);
		}
		disc(c, n, r, rgb, alpha * 0.12f, alpha * 0.02f, 48);
	}
}
