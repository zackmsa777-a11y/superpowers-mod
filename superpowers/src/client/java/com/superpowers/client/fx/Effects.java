package com.superpowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The library of reusable effects. */
public final class Effects {
	private Effects() {}

	static final Vec3 UP = new Vec3(0, 1, 0);
	static final RandomSource RND = RandomSource.create();

	static Entity entity(int id) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null || id < 0 ? null : mc.level.getEntity(id);
	}

	/** 0..1: fades a vertical column as the camera gets inside it. */
	static float inside(Vec3 base, double radius, double height) {
		Vec3 c = Draw.camPos;
		if (c.y < base.y - 1 || c.y > base.y + height + 1) return 1f;
		double dx = c.x - base.x, dz = c.z - base.z;
		double d = Math.sqrt(dx * dx + dz * dz);
		return d >= radius ? 1f : (float) Math.max(0.12, d / radius);
	}

	/** Expanding ring wave. */
	public static class Shockwave extends Effect {
		final Vec3 pos, normal;
		final double maxR, width;
		final int rgb;
		final float alpha;

		public Shockwave(Vec3 pos, Vec3 normal, double maxR, double width, int life, int rgb, float alpha) {
			super(life);
			this.pos = pos;
			this.normal = normal;
			this.maxR = maxR;
			this.width = width;
			this.rgb = rgb;
			this.alpha = alpha;
		}

		@Override
		public void renderAdd(float pt) {
			float t = t(pt);
			double r = maxR * easeOut(t);
			float a = alpha * (float) Math.pow(1 - t, 1.4);
			double w = width * (1 - t * 0.6);
			Draw.ring(pos, normal, r, w, rgb, a, 72);
			Draw.ring(pos, normal, r * 0.96, w * 0.3, 0xFFFFFF, a * 0.8f, 72);
			Draw.annulus(pos, normal, r * 0.5, r, rgb, 0, a * 0.18f, 48, 0);
		}
	}

	/** Expanding glowing sphere flash. */
	public static class Burst extends Effect {
		final Vec3 pos;
		final double maxR;
		final int rgb, core;

		public Burst(Vec3 pos, double maxR, int life, int rgb, int core) {
			super(life);
			this.pos = pos;
			this.maxR = maxR;
			this.rgb = rgb;
			this.core = core;
		}

		@Override
		public void renderAdd(float pt) {
			float t = t(pt);
			double r = maxR * easeOut(t);
			float a = (float) Math.pow(1 - t, 1.8);
			double camD = Draw.camPos.distanceTo(pos);
			if (camD < r * 1.1) a *= (float) Math.max(0.15, camD / (r * 1.1));
			Draw.sphere(pos, r, rgb, a * 0.1f, a * 0.38f, 14, 24);
			Draw.glow(pos, r * 1.6, rgb, a * 0.7f);
			if (t < 0.4f) {
				float k = 1 - t / 0.4f;
				Draw.sphere(pos, r * 0.55, core, k * 0.6f, k * 0.2f, 10, 16);
				Draw.glow(pos, r * 0.9, 0xFFFFFF, k);
			}
		}
	}

	/** A glowing orb that can follow an entity (with offset) or sit still. */
	public static class Orb extends Effect {
		Vec3 pos;
		final int follow;
		final Vec3 offset;
		final double radius;
		final int rgb, core;

		public Orb(Vec3 pos, int follow, Vec3 offset, double radius, int life, int rgb, int core) {
			super(life);
			this.pos = pos;
			this.follow = follow;
			this.offset = offset;
			this.radius = radius;
			this.rgb = rgb;
			this.core = core;
		}

		Vec3 at(float pt) {
			Entity e = entity(follow);
			return e != null ? e.getPosition(pt).add(offset) : pos;
		}

		@Override
		public void renderAdd(float pt) {
			float a = fade(pt, 0.15f, 0.25f);
			double pulse = 1 + 0.08 * Math.sin((age + pt) * 0.8);
			Vec3 p = at(pt);
			Draw.glow(p, radius * 2.6 * pulse, rgb, a * 0.6f);
			Draw.sphere(p, radius * pulse, rgb, a * 0.5f, a * 0.9f, 10, 16);
			Draw.sphere(p, radius * 0.55, core, a, a * 0.6f, 8, 12);
		}
	}

	/** Straight beam between two points that swells then thins out. */
	public static class Beam extends Effect {
		final Vec3 a, b;
		final double width;
		final int rgb;

		public Beam(Vec3 a, Vec3 b, double width, int life, int rgb) {
			super(life);
			this.a = a;
			this.b = b;
			this.width = width;
			this.rgb = rgb;
		}

		@Override
		public void renderAdd(float pt) {
			float t = t(pt);
			double w = width * (t < 0.2f ? t / 0.2f : 1 - (t - 0.2f) / 0.8f * 0.9f);
			Draw.hotBeam(a, b, w, rgb, 1 - t * t);
			Draw.glow(a, w * 3, rgb, 1 - t);
			Draw.glow(b, w * 4, rgb, 1 - t);
		}
	}

	/** Lightning bolt that re-jags every couple of ticks. */
	public static class Bolt extends Effect {
		final Vec3 a, b;
		final double width;
		final int rgb;
		long seed = RND.nextLong();

		public Bolt(Vec3 a, Vec3 b, double width, int life, int rgb) {
			super(life);
			this.a = a;
			this.b = b;
			this.width = width;
			this.rgb = rgb;
		}

		@Override
		public void tick() {
			super.tick();
			if (age % 2 == 0) seed = RND.nextLong();
		}

		@Override
		public void renderAdd(float pt) {
			float t = t(pt);
			float flicker = (age % 3 == 1) ? 0.6f : 1f;
			Draw.bolt(a, b, seed, width * (1 - t * 0.5), rgb, (1 - t) * flicker, 4);
			Draw.glow(b, 3.5 * (1 - t), rgb, (1 - t) * 0.8f);
		}
	}

	/** Sweeping crescent slash. */
	public static class Slash extends Effect {
		final Vec3 pos, u, w;
		final double radius, thick, sweep;
		final int rgb;

		/** u points to the start of the sweep, w to the end (both unit, perpendicular). */
		public Slash(Vec3 pos, Vec3 u, Vec3 w, double radius, double thick, double sweep, int life, int rgb) {
			super(life);
			this.pos = pos;
			this.u = u;
			this.w = w;
			this.radius = radius;
			this.thick = thick;
			this.sweep = sweep;
			this.rgb = rgb;
		}

		@Override
		public void renderAdd(float pt) {
			float t = t(pt);
			float head = Math.min(1f, t / 0.45f);
			float tail = Math.max(0f, (t - 0.25f) / 0.75f);
			double a1 = -sweep / 2 + sweep * easeOut(head);
			double a0 = -sweep / 2 + sweep * easeOut(tail) * 0.95;
			if (a1 - a0 < 0.05) return;
			float alpha = t < 0.6f ? 1f : (1f - t) / 0.4f;
			Draw.crescent(pos, u, w, radius, a0, a1, thick, rgb, alpha, 24);
		}
	}

	/** Column of light/fire rising from the ground. */
	public static class Pillar extends Effect {
		final Vec3 pos;
		final double radius, height;
		final int rgb, core;

		public Pillar(Vec3 pos, double radius, double height, int life, int rgb, int core) {
			super(life);
			this.pos = pos;
			this.radius = radius;
			this.height = height;
			this.rgb = rgb;
			this.core = core;
		}

		@Override
		public void renderAdd(float pt) {
			float t = t(pt);
			double h = height * easeOut(Math.min(1f, t / 0.25f));
			double r = radius * (t < 0.7f ? 1 : 1 - (t - 0.7f) / 0.3f * 0.9f);
			float a = fade(pt, 0.05f, 0.4f) * inside(pos, r * 1.6, h);
			double spin = (age + pt) * 0.3;
			Draw.tube(pos, UP, h, r, r * 0.6, rgb, a * 0.8f, 0, 16, spin);
			Draw.tube(pos, UP, h * 0.9, r * 0.45, r * 0.2, core, a, 0, 12, -spin);
			Draw.tube(pos, UP, h * 0.6, r * 1.3, r * 1.1, rgb, a * 0.25f, 0, 16, spin * 0.5);
			Draw.disc(pos.add(0, 0.05, 0), UP, r * 2.2, rgb, a * 0.6f, 0, 24);
			Draw.glow(pos.add(0, 0.3, 0), r * 3, rgb, a * 0.5f);
		}
	}

	/** Rotating magic circle on the ground. */
	public static class Sigil extends Effect {
		final Vec3 pos, normal;
		final double radius, spin;
		final int rgb, points, step;

		public Sigil(Vec3 pos, Vec3 normal, double radius, int life, int rgb, int points, int step, double spin) {
			super(life);
			this.pos = pos;
			this.normal = normal;
			this.radius = radius;
			this.rgb = rgb;
			this.points = points;
			this.step = step;
			this.spin = spin;
		}

		@Override
		public void renderAdd(float pt) {
			float t = t(pt);
			double grow = easeOut(Math.min(1f, t / 0.15f));
			float a = fade(pt, 0.05f, 0.2f);
			Draw.sigil(pos, normal, radius * grow, (age + pt) * spin, rgb, a, points, step);
		}
	}

	/** Spinning portal ring (warp). */
	public static class Portal extends Effect {
		final Vec3 pos, normal;
		final double radius;
		final int rgb, rgb2;

		public Portal(Vec3 pos, Vec3 normal, double radius, int life, int rgb, int rgb2) {
			super(life);
			this.pos = pos;
			this.normal = normal;
			this.radius = radius;
			this.rgb = rgb;
			this.rgb2 = rgb2;
		}

		@Override
		public void renderAdd(float pt) {
			float t = t(pt);
			double r = radius * (t < 0.2f ? easeOut(t / 0.2f) : t > 0.75f ? 1 - (t - 0.75f) / 0.25f : 1);
			float a = fade(pt, 0.05f, 0.25f);
			double spin = (age + pt) * 0.25;
			Draw.ring(pos, normal, r, r * 0.16, rgb, a, 48);
			Draw.ring(pos, normal, r * 0.8, r * 0.06, rgb2, a, 48);
			Draw.disc(pos, normal, r, rgb, a * 0.35f, a * 0.1f, 32);
			Vec3[] uw = Draw.basis(normal);
			for (int arm = 0; arm < 5; arm++) {
				Vec3 prev = pos;
				for (int s = 1; s <= 10; s++) {
					double k = s / 10.0;
					double ang = spin + arm * Math.PI * 2 / 5 + k * 2.4;
					Vec3 p = Draw.onCircle(pos, uw[0], uw[1], r * k, ang);
					Draw.flatLine(prev, p, normal, r * 0.08 * (1 - k * 0.5), rgb2, a * (float) (1 - k * 0.6));
					prev = p;
				}
			}
			Draw.glow(pos, r * 0.8, 0xFFFFFF, a * 0.5f);
		}
	}

	/** Black hole: dark core, swirling accretion disk and a photon ring. */
	public static class BlackHole extends Effect {
		final Vec3 pos;
		final double radius;
		final Vec3 diskNormal = new Vec3(0.25, 1, 0.15).normalize();
		public boolean collapsing;
		int collapseAge;

		public BlackHole(Vec3 pos, double radius, int life) {
			super(life);
			this.pos = pos;
			this.radius = radius;
		}

		double size(float pt) {
			float grow = easeOut(Math.min(1f, (age + pt) / 30f));
			if (collapsing) grow *= Math.max(0f, 1f - (collapseAge + pt) / 10f);
			return radius * grow;
		}

		@Override
		public void tick() {
			super.tick();
			if (collapsing) collapseAge++;
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) return;
			double r = size(0);
			for (int i = 0; i < 10; i++) {
				// matter spiraling in
				double ang = RND.nextDouble() * Math.PI * 2;
				double dist = r * (3 + RND.nextDouble() * 4);
				Vec3[] uw = Draw.basis(diskNormal);
				Vec3 p = Draw.onCircle(pos, uw[0], uw[1], dist, ang);
				Vec3 v = pos.subtract(p).scale(0.08).add(uw[0].scale(-Math.sin(ang)).add(uw[1].scale(Math.cos(ang))).scale(0.25));
				mc.level.addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL, true, p.x, p.y, p.z, v.x, v.y, v.z);
			}
		}

		@Override
		public void renderAlpha(float pt) {
			double r = size(pt);
			if (r < 0.05) return;
			Draw.solidSphere(pos, r, 0x000000, 1f, 16, 24);
		}

		@Override
		public void renderAdd(float pt) {
			double r = size(pt);
			if (r < 0.05) return;
			float a = fade(pt, 0.1f, 0.05f);
			double spin = (age + pt) * 0.12;
			// accretion disk: layered bands from hot inner to cool outer
			int[] cols = {0xFFF0D0, 0xFFB050, 0xE0508A, 0x9A5CFF, 0x4030A0};
			for (int i = 0; i < cols.length; i++) {
				double r0 = r * (1.2 + i * 0.4), r1 = r * (1.2 + (i + 1) * 0.4);
				Draw.annulus(pos, diskNormal, r0, r1, cols[i], a * (0.9f - i * 0.14f), a * (0.75f - i * 0.14f), 64, spin * (1.6 - i * 0.25));
			}
			// streaks in the disk
			Vec3[] uw = Draw.basis(diskNormal);
			for (int s = 0; s < 14; s++) {
				double ang = spin * 2 + s * Math.PI * 2 / 14;
				double rr = r * (1.35 + (s % 5) * 0.38);
				Vec3 p = Draw.onCircle(pos, uw[0], uw[1], rr, ang), q = Draw.onCircle(pos, uw[0], uw[1], rr, ang + 0.5);
				Draw.flatLine(p, q, diskNormal, r * 0.06, 0xFFFFFF, a * 0.6f);
			}
			// photon ring always faces the camera
			Draw.ring(pos, Draw.camLook, r * 1.08, r * 0.18, 0xFFE8C0, a, 64);
			Draw.ring(pos, Draw.camLook, r * 1.3, r * 0.5, 0x9A5CFF, a * 0.5f, 64);
			Draw.glow(pos, r * 4.5, 0x5A2CC0, a * 0.35f);
		}
	}

	/** Miniature sun forming above a caster's head. */
	public static class Sun extends Effect {
		final int follow;
		final double maxR;
		Vec3 last = Vec3.ZERO;

		public Sun(int follow, double maxR, int life) {
			super(life);
			this.follow = follow;
			this.maxR = maxR;
		}

		Vec3 at(float pt) {
			Entity e = entity(follow);
			if (e != null) last = e.getPosition(pt).add(0, 4.3, 0);
			return last;
		}

		@Override
		public void tick() {
			super.tick();
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) return;
			Vec3 c = at(1);
			double r = maxR * Math.min(1f, age / (float) life);
			for (int i = 0; i < 6; i++) {
				// flames being sucked into the sun
				Vec3 dir = new Vec3(RND.nextGaussian(), RND.nextGaussian(), RND.nextGaussian()).normalize();
				Vec3 p = c.add(dir.scale(r + 3 + RND.nextDouble() * 3));
				Vec3 v = c.subtract(p).scale(0.12);
				mc.level.addParticle(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, true, p.x, p.y, p.z, v.x, v.y, v.z);
			}
		}

		@Override
		public void renderAdd(float pt) {
			float t = t(pt);
			double r = maxR * easeOut(t) + 0.1;
			renderSun(at(pt), r, age + pt, 1f);
		}

		static void renderSun(Vec3 c, double r, float time, float a) {
			double pulse = 1 + 0.05 * Math.sin(time * 0.9);
			Draw.glow(c, r * 3.0, 0xFF6010, a * 0.35f);
			Draw.glow(c, r * 1.9, 0xFFA030, a * 0.5f);
			Draw.sphere(c, r * 1.15 * pulse, 0xFF5010, a * 0.25f, a * 0.8f, 14, 24);
			Draw.sphere(c, r * pulse, 0xFFB040, a * 0.7f, a * 0.5f, 14, 24);
			Draw.sphere(c, r * 0.7, 0xFFF4C0, a, a * 0.8f, 10, 16);
			// corona flares
			RandomSource rnd = RandomSource.create(1234);
			for (int i = 0; i < 18; i++) {
				Vec3 dir = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize();
				double len = r * (0.5 + 0.6 * (0.5 + 0.5 * Math.sin(time * 0.35 + i * 1.7)));
				Draw.beam(c.add(dir.scale(r * 0.9)), c.add(dir.scale(r + len)), r * 0.35, r * 0.02, 0xFF8020, a * 0.8f, 0);
			}
		}
	}

	/** The sun flying to its target. */
	public static class SunThrow extends Effect {
		final Vec3 from, to;
		final double r;

		public SunThrow(Vec3 from, Vec3 to, double r, int travel) {
			super(travel);
			this.from = from;
			this.to = to;
			this.r = r;
		}

		Vec3 at(float pt) {
			float t = easeIn(t(pt));
			return from.add(to.subtract(from).scale(t));
		}

		@Override
		public void tick() {
			super.tick();
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) return;
			Vec3 c = at(1);
			for (int i = 0; i < 12; i++) {
				Vec3 p = c.add(RND.nextGaussian() * r, RND.nextGaussian() * r, RND.nextGaussian() * r);
				mc.level.addParticle(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME, true, p.x, p.y, p.z, 0, 0.05, 0);
			}
		}

		@Override
		public void renderAdd(float pt) {
			Vec3 c = at(pt);
			Vec3 back = from.subtract(to).normalize();
			Draw.beam(c, c.add(back.scale(r * 6)), r * 2.2, r * 0.2, 0xFF7020, 0.7f, 0);
			Sun.renderSun(c, r, age + pt, 1f);
		}
	}

	/** Chain from an entity's hand to a fixed point or another entity. */
	public static class Chain extends Effect {
		final int caster, target;
		final Vec3 end;
		final int rgb;

		public Chain(int caster, int target, Vec3 end, int life, int rgb) {
			super(life);
			this.caster = caster;
			this.target = target;
			this.end = end;
			this.rgb = rgb;
		}

		@Override
		public void renderAdd(float pt) {
			Entity c = entity(caster);
			if (c == null) return;
			Vec3 a = c.getPosition(pt).add(0, c.getBbHeight() * 0.7, 0);
			Entity te = entity(target);
			Vec3 b = te != null ? te.getPosition(pt).add(0, te.getBbHeight() * 0.5, 0) : end;
			float t = t(pt);
			float extend = Math.min(1f, (age + pt) / 3f);
			Vec3 tip = a.add(b.subtract(a).scale(extend));
			double len = a.distanceTo(tip);
			Vec3 dir = tip.subtract(a).normalize();
			Vec3[] uw = Draw.basis(dir);
			float alpha = t > 0.75f ? (1 - t) / 0.25f : 1f;
			int links = (int) (len / 0.45);
			for (int i = 0; i < links; i++) {
				Vec3 p = a.add(dir.scale(i * 0.45 + 0.22));
				Vec3 n = (i % 2 == 0) ? uw[0] : uw[1];
				Draw.ring(p, n, 0.16, 0.07, rgb, alpha, 10);
			}
			Draw.beam(a, tip, 0.25, rgb, alpha * 0.4f);
			Draw.glow(tip, 0.9, rgb, alpha);
		}
	}

	/** Ribbon trail behind a moving entity. */
	public static class Trail extends Effect {
		final int follow;
		final int rgb, core;
		final double width;
		final List<Vec3> pts = new ArrayList<>();
		final boolean flames;

		public Trail(int follow, int life, double width, int rgb, int core, boolean flames) {
			super(life);
			this.follow = follow;
			this.width = width;
			this.rgb = rgb;
			this.core = core;
			this.flames = flames;
		}

		@Override
		public void tick() {
			super.tick();
			Entity e = entity(follow);
			if (e != null && age < life - 6) {
				Vec3 p = e.position().add(0, 0.9, 0);
				pts.add(p);
				Minecraft mc = Minecraft.getInstance();
				for (int i = 0; i < 6 && mc.level != null; i++) {
					mc.level.addParticle(flames ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME, true,
							p.x + RND.nextGaussian() * 0.3, p.y + RND.nextGaussian() * 0.5, p.z + RND.nextGaussian() * 0.3, 0, 0.02, 0);
				}
			}
		}

		@Override
		public void renderAdd(float pt) {
			float a = fade(pt, 0f, 0.5f);
			for (int i = 0; i < pts.size() - 1; i++) {
				float k = (i + 1f) / pts.size();
				Draw.beam(pts.get(i), pts.get(i + 1), width * k, width * (i + 2f) / pts.size(), rgb, a * k * 0.8f, a * k * 0.8f);
				Draw.beam(pts.get(i), pts.get(i + 1), width * 0.3 * k, core, a * k);
			}
		}
	}

	/** Spike erupting from the ground and retracting. */
	public static class Spike extends Effect {
		final Vec3 pos, tipDir;
		final double height, radius;
		final int rgb, glow;

		public Spike(Vec3 pos, Vec3 tipDir, double height, double radius, int life, int rgb, int glow) {
			super(life);
			this.pos = pos;
			this.tipDir = tipDir.normalize();
			this.height = height;
			this.radius = radius;
			this.rgb = rgb;
			this.glow = glow;
		}

		double h(float pt) {
			float t = t(pt);
			if (t < 0.12f) return height * easeOut(t / 0.12f);
			if (t > 0.75f) return height * (1 - easeIn((t - 0.75f) / 0.25f));
			return height;
		}

		final String model = RND.nextBoolean() ? "demon_spike_a" : "demon_spike_b";
		final float spin = RND.nextFloat() * 6.28f;

		@Override
		public void renderAlpha(float pt) {
			double h = h(pt);
			if (h < 0.05) return;
			// the spike rises out of the ground: keep full size, slide it up
			Vec3 base = pos.subtract(tipDir.scale(height - h + 0.25));
			Vec3 side = tipDir.cross(UP);
			if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
			side = side.normalize();
			Vec3 fwd = side.cross(tipDir).normalize();
			float rs = (float) (radius / 0.32);
			org.joml.Matrix4f m = com.superpowers.client.model.MeshDraw.frame(base, side, tipDir, fwd, 1f)
					.rotateY(spin).scale(rs, (float) height, rs);
			com.superpowers.client.model.MeshDraw.draw(com.superpowers.client.model.Mesh.get(model), m,
					com.superpowers.client.model.MeshDraw.brightness(pos.add(0, 1, 0)), 0, 0, 1f);
		}

		@Override
		public void renderAdd(float pt) {
			double h = h(pt);
			if (h < 0.05) return;
			Vec3 tip = pos.add(tipDir.scale(h));
			Draw.beam(pos, tip, radius * 1.6, radius * 0.2, glow, 0.5f, 0.9f);
			Draw.glow(tip, radius * 1.5, glow, 0.8f);
			Draw.disc(pos.add(0, 0.05, 0), UP, radius * 2.5, glow, 0.5f * fade(pt, 0, 0.3f), 0, 16);
		}
	}

	/** Giant halo in the sky, slowly turning and descending a little. */
	public static class SkyHalo extends Effect {
		final Vec3 pos;
		final double radius;

		public SkyHalo(Vec3 pos, double radius, int life) {
			super(life);
			this.pos = pos;
			this.radius = radius;
		}

		@Override
		public void renderAdd(float pt) {
			float t = t(pt);
			float a = fade(pt, 0.2f, 0.15f);
			double r = radius * easeOut(Math.min(1f, t / 0.3f));
			Vec3 c = pos.subtract(0, 6 * t, 0);
			double spin = (age + pt) * 0.03;
			Draw.ring(c, UP, r, r * 0.12, 0xFFE9A0, a, 96);
			Draw.ring(c, UP, r, r * 0.04, 0xFFFFFF, a, 96);
			Draw.ring(c, UP, r * 1.25, r * 0.03, 0xFFF4C2, a * 0.6f, 96);
			Draw.sigil(c, UP, r * 0.8, spin, 0xFFF4C2, a * 0.7f, 6, 2);
			Draw.disc(c, UP, r * 1.6, 0xFFE0A0, a * 0.16f, 0, 48);
			// god rays falling from the halo
			for (int i = 0; i < 16; i++) {
				double ang = spin * 2 + i * Math.PI * 2 / 16;
				Vec3 p = c.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
				Draw.beam(p, p.subtract(0, 18 + 6 * Math.sin(i + (age + pt) * 0.1), 0), 0.9, 0.2, 0xFFF0C0, a * 0.35f, 0);
			}
			Draw.glow(c, r * 2.2, 0xFFE8B0, a * 0.35f);
		}
	}

	/** Pillar of judgment: an enormous beam of light from the sky. */
	public static class Judgment extends Effect {
		final Vec3 pos;
		final double radius;

		public Judgment(Vec3 pos, double radius, int life) {
			super(life);
			this.pos = pos;
			this.radius = radius;
		}

		@Override
		public void tick() {
			super.tick();
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) return;
			for (int i = 0; i < 24; i++) {
				double a = RND.nextDouble() * Math.PI * 2, r = RND.nextDouble() * radius * 1.3;
				mc.level.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.FIREWORK, true,
						pos.x + Math.cos(a) * r, pos.y + 0.2, pos.z + Math.sin(a) * r, 0, 0.3 + RND.nextDouble() * 0.6, 0);
			}
		}

		@Override
		public void renderAdd(float pt) {
			float t = t(pt);
			float slam = Math.min(1f, (age + pt) / 4f);
			float a = fade(pt, 0f, 0.35f);
			double r = radius * (t > 0.6f ? 1 - (t - 0.6f) / 0.4f * 0.85 : 1) * (1 + 0.05 * Math.sin((age + pt) * 1.3));
			double h = 90;
			Vec3 top = pos.add(0, h, 0);
			Vec3 bottom = top.add(0, -h * slam, 0);
			double spin = (age + pt) * 0.2;
			Draw.tube(bottom, UP, h * slam, r, r * 1.1, 0xFFF0C0, a * 0.55f, a * 0.25f, 32, spin);
			Draw.tube(bottom, UP, h * slam, r * 0.55, r * 0.6, 0xFFFFFF, a * 0.9f, a * 0.5f, 24, -spin);
			Draw.tube(bottom, UP, h * slam, r * 1.5, r * 1.8, 0xFFE0A0, a * 0.15f, a * 0.05f, 32, spin * 0.5);
			Draw.disc(pos.add(0, 0.1, 0), UP, r * 2.4, 0xFFF4D0, a * 0.8f, 0, 48);
			Draw.glow(pos.add(0, 1, 0), r * 4, 0xFFF0C0, a * 0.6f);
			for (int k = 0; k < 3; k++) {
				float lt = ((age + pt) / 8f + k / 3f) % 1f;
				Draw.ring(pos.add(0, 0.15, 0), UP, r * (1 + lt * 2.5), r * 0.25, 0xFFF0C0, a * (1 - lt), 64);
			}
		}
	}

	/** Twinkling stars scattered on a sphere around a point. */
	public static class Starfield extends Effect {
		final Vec3 pos;
		final double radius;
		final List<Vec3> stars = new ArrayList<>();
		final float[] phase;

		public Starfield(Vec3 pos, double radius, int count, int life) {
			super(life);
			this.pos = pos;
			this.radius = radius;
			phase = new float[count];
			for (int i = 0; i < count; i++) {
				Vec3 d = new Vec3(RND.nextGaussian(), Math.abs(RND.nextGaussian()) * 0.8 + 0.05, RND.nextGaussian()).normalize();
				stars.add(pos.add(d.scale(radius * (0.6 + RND.nextDouble() * 0.4))));
				phase[i] = RND.nextFloat() * 10;
			}
		}

		@Override
		public void renderAdd(float pt) {
			float a = fade(pt, 0.2f, 0.2f);
			for (int i = 0; i < stars.size(); i++) {
				float tw = 0.55f + 0.45f * Mth.sin((age + pt) * 0.3f + phase[i]);
				int col = i % 7 == 0 ? 0xB0C8FF : i % 5 == 0 ? 0xFFD8B0 : 0xFFFFFF;
				Draw.sparkle(stars.get(i), 0.35 + (i % 4) * 0.15, col, a * tw);
			}
		}
	}

	/** Gravity well vortex. */
	public static class Vortex extends Effect {
		final Vec3 pos;
		final double radius;
		final int rgb, rgb2;

		public Vortex(Vec3 pos, double radius, int life, int rgb, int rgb2) {
			super(life);
			this.pos = pos;
			this.radius = radius;
			this.rgb = rgb;
			this.rgb2 = rgb2;
		}

		@Override
		public void tick() {
			super.tick();
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) return;
			for (int i = 0; i < 8; i++) {
				double a = RND.nextDouble() * Math.PI * 2, r = radius * (0.3 + RND.nextDouble() * 0.7);
				Vec3 p = pos.add(Math.cos(a) * r, 0.2, Math.sin(a) * r);
				mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, true, p.x, p.y, p.z, -Math.sin(a) * 0.3, 0.25, Math.cos(a) * 0.3);
			}
		}

		@Override
		public void renderAdd(float pt) {
			float a = fade(pt, 0.1f, 0.2f);
			double spin = (age + pt) * 0.15;
			Vec3 c = pos.add(0, 0.1, 0);
			for (int arm = 0; arm < 6; arm++) {
				Vec3 prev = null;
				for (int s = 0; s <= 16; s++) {
					double k = s / 16.0;
					double ang = spin + arm * Math.PI / 3 + (1 - k) * 3.0;
					Vec3 p = c.add(Math.cos(ang) * radius * k, 0, Math.sin(ang) * radius * k);
					if (prev != null) Draw.flatLine(prev, p, UP, 0.35 * k + 0.08, arm % 2 == 0 ? rgb : rgb2, a * (float) (0.9 - k * 0.5));
					prev = p;
				}
			}
			for (int k = 0; k < 4; k++) {
				float lt = ((age + pt) / 12f + k / 4f) % 1f;
				Draw.ring(c.add(0, lt * 6, 0), UP, radius * (1 - lt * 0.7), 0.4, rgb, a * (1 - lt) * 0.7f, 48);
			}
			Draw.ring(c, UP, radius, 0.5, rgb2, a, 64);
			Draw.disc(c, UP, radius, rgb, a * 0.25f, a * 0.05f, 48);
			Draw.glow(c.add(0, 4.5, 0), 3, rgb, a * 0.6f);
		}
	}
}
