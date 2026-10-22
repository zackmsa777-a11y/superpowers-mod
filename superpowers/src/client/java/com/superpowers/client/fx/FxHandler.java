package com.superpowers.client.fx;

import com.superpowers.client.ClientState;
import com.superpowers.client.ClientState.Form;
import com.superpowers.client.ScreenFx;
import com.superpowers.client.fx.Effects.*;
import com.superpowers.net.Net;
import com.superpowers.power.Fx;
import com.superpowers.power.PowerType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntConsumer;

/** Turns effect packets into layered geometry + particles + screen effects. */
public final class FxHandler {
	private FxHandler() {}

	static final Vec3 UP = new Vec3(0, 1, 0);
	static final RandomSource RND = RandomSource.create();

	static int FIRE = 0xFF6A1A, FIRE_CORE = 0xFFD24A, FIRE_HOT = 0xFFF2C0;
	/** Fire turns into blue flame while awakened. */
	static ParticleOptions FLAME = ParticleTypes.FLAME;
	static final int SPACE = 0x9A5CFF, SPACE2 = 0x5CD6FF;
	static final int ANGEL = 0xFFF4C2, ANGEL2 = 0x8FE3FF;
	static final int DEMON = 0xE0102F, DEMON_DARK = 0x2A0006;
	static final int GOD = 0xFFC93C, GOD2 = 0xFFE9A0;

	private static BlackHole lastBlackHole;

	/** Effect that just runs a callback each tick (particle emitters). */
	static class Emitter extends Effect {
		final IntConsumer body;

		Emitter(int life, IntConsumer body) {
			super(life);
			this.body = body;
		}

		@Override
		public void tick() {
			body.accept(age);
			super.tick();
		}
	}

	/** Time-stop dome: a sphere that snaps out and lingers, plus a frozen clock on the ground. */
	static class Dome extends Effect {
		final Vec3 pos;
		final double radius;

		Dome(Vec3 pos, double radius, int life) {
			super(life);
			this.pos = pos;
			this.radius = radius;
		}

		@Override
		public void renderAdd(float pt) {
			float t = age + pt;
			float a = fade(pt, 0f, 0.15f);
			double r = radius * Effect.easeOut(Math.min(1f, t / 12f));
			float flash = Math.max(0, 1 - t / 12f);
			Draw.sphere(pos, r, GOD, 0.04f + flash * 0.3f, (0.25f + flash * 0.5f) * a, 18, 32);
			Draw.ring(pos.add(0, 0.1, 0), UP, r, 0.6, GOD, a * 0.8f, 96);
			Vec3 c = pos.add(0, 0.08, 0);
			double cr = Math.min(radius * 0.3, 7);
			Draw.sigil(c, UP, cr, 0.4, GOD2, a * 0.8f, 12, 5);
			// clock hands frozen mid-tick
			double handA = t < 8 ? t * 0.4 : 3.2;
			Draw.flatLine(c, c.add(Math.cos(handA) * cr * 0.8, 0, Math.sin(handA) * cr * 0.8), UP, 0.3, 0xFFFFFF, a);
			Draw.flatLine(c, c.add(Math.cos(handA * 0.3 + 1) * cr * 0.5, 0, Math.sin(handA * 0.3 + 1) * cr * 0.5), UP, 0.45, 0xFFFFFF, a);
		}
	}

	static Entity entity(int id) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null || id < 0 ? null : mc.level.getEntity(id);
	}

	static Vec3 chest(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.6, 0);
	}

	static void add(Effect e) {
		FxManager.add(e);
	}

	/** u = forward, w = side rolled around forward by {@code roll} radians. */
	static Vec3[] slashBasis(Vec3 dir, double roll) {
		Vec3 f = dir.normalize();
		Vec3 right = f.cross(UP);
		if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
		right = right.normalize();
		Vec3 up = right.cross(f).normalize();
		Vec3 w = right.scale(Math.cos(roll)).add(up.scale(Math.sin(roll)));
		return new Vec3[]{f, w};
	}

	static ParticleOptions powerParticle(PowerType t) {
		return switch (t) {
			case FIRE -> ParticleTypes.FLAME;
			case SPACE -> ParticleTypes.REVERSE_PORTAL;
			case ANGEL -> ParticleTypes.END_ROD;
			case DEMON -> ParticleTypes.SOUL_FIRE_FLAME;
			case GOD -> ParticleTypes.ELECTRIC_SPARK;
			default -> ParticleTypes.CRIT;
		};
	}

	public static void handle(Net.Effect fx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 dir = new Vec3(fx.dx(), fx.dy(), fx.dz());
		Entity caster = entity(fx.caster());
		float scale = fx.scale();
		int extra = fx.extra();
		boolean self = caster != null && caster == mc.player;
		boolean aw = fx.awakened();
		FIRE = aw ? 0x3EA8FF : 0xFF6A1A;
		FIRE_CORE = aw ? 0xA8E8FF : 0xFFD24A;
		FIRE_HOT = aw ? 0xFFFFFF : 0xFFF2C0;
		FLAME = aw ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;

		switch (fx.fx()) {
			case Fx.SHAKE -> ScreenFx.shake(scale, extra);
			case Fx.FLASH -> ScreenFx.flash(rgb(dir), scale, extra);
			case Fx.TINT -> ScreenFx.tint(rgb(dir), scale, extra);

			case Fx.AWAKEN -> {
				PowerType t = PowerType.byOrdinal(extra);
				Vec3 feet = caster != null ? caster.position() : pos;
				add(new Pillar(feet, 1.2, 16, 34, t.color, 0xFFFFFF));
				add(new Shockwave(feet.add(0, 0.1, 0), UP, 9, 1.0, 22, t.color, 1f));
				add(new Shockwave(feet.add(0, 0.1, 0), UP, 5, 0.5, 16, t.color2, 1f));
				add(new Burst(feet.add(0, 1, 0), 3, 14, t.color, 0xFFFFFF));
				add(new Sigil(feet.add(0, 0.06, 0), UP, 4, 45, t.color, 6, 2, 0.08));
				Particles.burst(powerParticle(t), feet.add(0, 1, 0), 90, 0.5, 0.6);
				Particles.column(ParticleTypes.END_ROD, feet, 1.2, 30, 0.5);
				if (self) ScreenFx.flash(t.color, 0.45f, 14);
			}

			// ------------------------------------------------------------ FIRE
			case Fx.FIRE_PUNCH -> {
				boolean fin = extra == 3;
				add(new Shockwave(pos, dir, fin ? 2.6 : 1.3, fin ? 0.6 : 0.35, fin ? 10 : 7, FIRE, 1f));
				add(new Burst(pos, fin ? 1.7 : 0.75, fin ? 9 : 6, FIRE, FIRE_HOT));
				Vec3[] b = slashBasis(dir, extra * 1.2 + 0.4);
				add(new Slash(pos.subtract(dir.scale(1.2)), b[0], b[1], 1.3, 0.45, 2.2, 7, FIRE));
				Particles.cone(FLAME, pos.subtract(dir.scale(0.5)), dir, fin ? 60 : 22, fin ? 0.6 : 0.35, 0.35);
				Particles.burst(ParticleTypes.SMALL_FLAME, pos, 10, 0.3, 0.15);
				if (fin) {
					Particles.burst(ParticleTypes.LAVA, pos, 8, 0.3, 0.2);
					add(new Shockwave(pos, UP, 3.2, 0.5, 12, FIRE_CORE, 0.8f));
				}
			}
			case Fx.FIRE_ORB_CAST -> {
				add(new Burst(pos, 1.0, 7, FIRE, FIRE_HOT));
				add(new Shockwave(pos, dir, 1.6, 0.3, 9, FIRE, 1f));
				Particles.cone(FLAME, pos, dir, 30, 0.4, 0.3);
			}
			case Fx.FIRE_EXPLOSION -> {
				double r = scale;
				add(new Burst(pos, r * 0.9, 14, FIRE, FIRE_HOT));
				add(new Shockwave(pos.add(0, -0.4, 0), UP, r * 1.9, r * 0.2, 16, FIRE, 1f));
				add(new Shockwave(pos, Particles.randDir(), r * 1.3, r * 0.12, 12, FIRE_CORE, 0.8f));
				Particles.burst(FLAME, pos, (int) (20 * r), 0.5, 0.12 * r);
				Particles.burst(ParticleTypes.LARGE_SMOKE, pos, (int) (5 * r), 0.8, 0.05 * r);
				Particles.burst(ParticleTypes.LAVA, pos, (int) (2 * r), 0.3, 0.2);
				Particles.burst(ParticleTypes.EXPLOSION, pos, Math.max(1, (int) (r / 2)), r * 0.4, 0);
				Particles.debris(pos, (int) (6 * r), 0.35);
			}
			case Fx.FIRE_DASH -> {
				if (caster != null) add(new Trail(caster.getId(), extra + 8, 1.5, FIRE, FIRE_HOT, true));
				add(new Shockwave(pos.add(0, 1, 0), dir, 2.2, 0.4, 9, FIRE, 1f));
				add(new Burst(pos.add(0, 1, 0), 1.2, 7, FIRE, FIRE_HOT));
			}
			case Fx.FIRE_PILLAR -> {
				add(new Pillar(pos, 0.85, scale, 24, 0xFF5A10, FIRE_CORE));
				add(new Shockwave(pos.add(0, 0.1, 0), UP, 2.4, 0.45, 10, FIRE, 1f));
				Particles.column(FLAME, pos, 0.8, 30, 0.45);
				Particles.burst(ParticleTypes.LAVA, pos, 4, 0.3, 0.2);
				Particles.column(ParticleTypes.LARGE_SMOKE, pos, 0.6, 5, 0.15);
				Particles.debris(pos, 8, 0.3);
			}
			case Fx.FIRE_RING_CAST -> {
				add(new Sigil(pos.add(0, 0.06, 0), UP, scale, extra + 6, FIRE, 6, 2, 0.07));
				add(new Shockwave(pos.add(0, 0.1, 0), UP, scale, 0.6, 18, FIRE_CORE, 1f));
				Particles.ring(FLAME, pos.add(0, 0.2, 0), 1, 40, 0.5, 0.05);
			}
			case Fx.FIRE_SUN_CHARGE -> {
				if (caster != null) add(new Sun(caster.getId(), 2.2, extra));
				add(new Sigil(pos.add(0, 0.06, 0), UP, 6, extra + 20, FIRE, 6, 2, 0.05));
				add(new Emitter(extra, i -> {
					Entity c = entity(fx.caster());
					if (c != null && i % 2 == 0) Particles.ring(FLAME, c.position().add(0, 0.2, 0), 1.5, 8, -0.05, 0.2);
				}));
			}
			case Fx.FIRE_SUN_THROW -> add(new SunThrow(pos, pos.add(dir), 2.2, Math.max(1, extra)));
			case Fx.FIRE_SUPERNOVA -> {
				double r = scale;
				add(new Burst(pos, r, 32, FIRE, 0xFFF8E0));
				add(new Burst(pos, r * 0.55, 22, FIRE_CORE, 0xFFFFFF));
				add(new Shockwave(pos.add(0, 0.2, 0), UP, r * 2.3, r * 0.22, 32, FIRE, 1f));
				add(new Shockwave(pos.add(0, 0.2, 0), UP, r * 1.5, r * 0.1, 22, FIRE_HOT, 1f));
				add(new Shockwave(pos.add(0, r * 0.3, 0), new Vec3(0.3, 1, 0.2), r * 1.8, r * 0.08, 26, FIRE_CORE, 0.8f));
				add(new Pillar(pos, r * 0.22, 45, 44, FIRE, FIRE_HOT));
				Particles.burst(ParticleTypes.EXPLOSION_EMITTER, pos, 3, r * 0.3, 0);
				Particles.burst(FLAME, pos, 320, 1.0, 1.3);
				Particles.burst(ParticleTypes.LAVA, pos, 40, 1.0, 0.5);
				Particles.burst(ParticleTypes.LARGE_SMOKE, pos.add(0, 2, 0), 90, 3, 0.4);
				Particles.column(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos, r * 0.25, 25, 0.2);
				Particles.debris(pos, 80, 0.8);
			}

			// ------------------------------------------------------------ SPACE
			case Fx.SPACE_PUNCH -> {
				boolean fin = extra == 3;
				add(new Shockwave(pos, dir, fin ? 3.2 : 1.4, fin ? 0.5 : 0.3, fin ? 12 : 8, SPACE, 1f));
				add(new Shockwave(pos, dir, fin ? 2.2 : 0.9, 0.2, fin ? 10 : 6, SPACE2, 1f));
				add(new Burst(pos, fin ? 1.4 : 0.6, 7, SPACE, 0xFFFFFF));
				Particles.burst(ParticleTypes.REVERSE_PORTAL, pos, fin ? 70 : 25, 0.4, fin ? 0.6 : 0.3);
				Particles.burst(ParticleTypes.END_ROD, pos, fin ? 12 : 4, 0.3, 0.15);
				if (fin) add(new Portal(pos, dir, 1.8, 14, SPACE, SPACE2));
			}
			case Fx.SPACE_WARP -> {
				Vec3 to = pos.add(dir);
				Vec3 n = new Vec3(dir.x, 0, dir.z);
				if (n.lengthSqr() < 1e-4) n = new Vec3(0, 0, 1);
				add(new Portal(pos, n, 1.5, 18, SPACE, SPACE2));
				add(new Portal(to.add(0, 1, 0), n, 1.5, 18, SPACE2, SPACE));
				add(new Beam(pos, to.add(0, 1, 0), 0.35, 8, SPACE));
				add(new Burst(to.add(0, 1, 0), 1.6, 8, SPACE, 0xFFFFFF));
				Particles.burst(ParticleTypes.PORTAL, pos, 50, 0.6, 0.6);
				Particles.burst(ParticleTypes.PORTAL, to.add(0, 1, 0), 50, 0.6, 0.6);
				for (int i = 0; i < 30; i++) {
					Vec3 p = pos.add(dir.scale(RND.nextDouble())).add(0, 1, 0);
					Particles.add(ParticleTypes.REVERSE_PORTAL, p, Vec3.ZERO);
				}
			}
			case Fx.SPACE_WELL -> {
				add(new Vortex(pos, scale, extra, SPACE, SPACE2));
				add(new Sigil(pos.add(0, 0.05, 0), UP, scale * 0.55, extra, SPACE2, 8, 3, -0.08));
			}
			case Fx.SPACE_SLAM -> {
				add(new Shockwave(pos.add(0, 0.1, 0), UP, scale * 1.4, 1.2, 16, SPACE, 1f));
				add(new Shockwave(pos.add(0, 0.1, 0), UP, scale * 0.8, 0.6, 12, SPACE2, 1f));
				add(new Burst(pos.add(0, 0.5, 0), 3.2, 11, SPACE, 0xFFFFFF));
				Particles.burst(ParticleTypes.EXPLOSION, pos, 4, 2, 0);
				Particles.burst(ParticleTypes.REVERSE_PORTAL, pos, 90, 1, 0.6);
				Particles.ring(ParticleTypes.CLOUD, pos.add(0, 0.2, 0), 1, 40, 0.6, 0.02);
				Particles.debris(pos, 50, 0.6);
			}
			case Fx.SPACE_METEOR_MARK -> {
				add(new Sigil(pos.add(0, 0.06, 0), UP, scale, extra + 20, 0xC060FF, 6, 1, 0.05));
				add(new Sigil(pos.add(0, 0.07, 0), UP, scale * 0.5, extra + 20, 0xFF8040, 3, 1, -0.1));
				add(new Starfield(pos.add(0, 20, 0), 14, 60, extra + 30));
			}
			case Fx.SPACE_METEOR_IMPACT -> {
				double r = scale;
				add(new Burst(pos, r * 0.85, 12, 0xB050FF, 0xFFE0F0));
				add(new Shockwave(pos.add(0, 0.1, 0), UP, r * 1.7, r * 0.18, 14, 0xFF8040, 1f));
				add(new Shockwave(pos.add(0, 0.1, 0), UP, r * 1.1, r * 0.1, 10, SPACE, 1f));
				Particles.burst(ParticleTypes.EXPLOSION, pos, 2, 1, 0);
				Particles.burst(ParticleTypes.FLAME, pos, 30, 0.6, 0.35);
				Particles.burst(ParticleTypes.REVERSE_PORTAL, pos, 40, 0.6, 0.5);
				Particles.burst(ParticleTypes.LARGE_SMOKE, pos, 16, 1, 0.12);
				Particles.debris(pos, 40, 0.7);
			}
			case Fx.SPACE_BLACKHOLE -> {
				lastBlackHole = new BlackHole(pos, scale, extra);
				add(lastBlackHole);
				add(new Starfield(pos, 38, 260, extra + 40));
				add(new Shockwave(pos, UP, 12, 1.5, 18, SPACE, 1f));
			}
			case Fx.SPACE_COLLAPSE -> {
				if (lastBlackHole != null) lastBlackHole.collapsing = true;
				add(new Burst(pos, 2, 10, 0xFFFFFF, 0xFFFFFF));
				for (int i = 0; i < 120; i++) {
					Vec3 d = Particles.randDir();
					Particles.add(ParticleTypes.END_ROD, pos.add(d.scale(14)), d.scale(-1.2));
				}
			}
			case Fx.SPACE_BIGBANG -> {
				double r = scale;
				add(new Burst(pos, r, 42, SPACE, 0xFFFFFF));
				add(new Burst(pos, r * 0.5, 26, SPACE2, 0xFFFFFF));
				add(new Burst(pos, r * 1.35, 55, 0xFF7AD0, 0xFFFFFF));
				add(new Shockwave(pos, UP, r * 2.6, r * 0.25, 42, 0xFFFFFF, 1f));
				for (int i = 0; i < 4; i++) {
					add(new Shockwave(pos, Particles.randDir(), r * (1.6 + i * 0.3), r * 0.08, 34 + i * 4, i % 2 == 0 ? SPACE : SPACE2, 1f));
				}
				add(new Starfield(pos, r * 1.6, 320, 70));
				Particles.burst(ParticleTypes.EXPLOSION_EMITTER, pos, 3, r * 0.3, 0);
				Particles.burst(ParticleTypes.END_ROD, pos, 300, 1, 1.6);
				Particles.burst(ParticleTypes.FIREWORK, pos, 220, 1, 1.2);
				Particles.burst(ParticleTypes.REVERSE_PORTAL, pos, 200, 2, 1.0);
			}

			// ------------------------------------------------------------ ANGEL
			case Fx.ANGEL_SLASH -> {
				boolean fin = extra == 3;
				double[] rolls = {0.25, Math.PI - 0.25, 1.1};
				if (fin) {
					for (double roll : new double[]{0.8, Math.PI - 0.8}) {
						Vec3[] b = slashBasis(dir, roll);
						add(new Slash(pos.subtract(dir.scale(1.3)), b[0], b[1], 2.0, 0.6, 2.8, 9, ANGEL));
					}
					add(new Burst(pos, 1.6, 9, ANGEL, 0xFFFFFF));
					add(new Shockwave(pos, dir, 2.8, 0.4, 10, ANGEL2, 1f));
					Particles.burst(ParticleTypes.END_ROD, pos, 40, 0.5, 0.4);
				} else {
					Vec3[] b = slashBasis(dir, rolls[extra % 3]);
					add(new Slash(pos.subtract(dir.scale(1.3)), b[0], b[1], 1.6, 0.5, 2.6, 7, ANGEL));
					Particles.burst(ParticleTypes.END_ROD, pos, 8, 0.4, 0.15);
				}
				if (scale > 0) add(new Burst(pos, 0.7, 6, ANGEL2, 0xFFFFFF));
			}
			case Fx.ANGEL_WINGS -> {
				if (caster != null) {
					ClientState.addForm(Form.WINGS, caster.getId(), extra);
					Vec3 c = chest(caster);
					add(new Burst(c, 2.2, 10, ANGEL, 0xFFFFFF));
					add(new Shockwave(caster.position().add(0, 0.1, 0), UP, 5.5, 0.6, 14, ANGEL, 1f));
					Particles.burst(ParticleTypes.END_ROD, c, 40, 0.8, 0.4);
					Particles.burst(ParticleTypes.WHITE_ASH, c, 60, 1.2, 0.3);
					Particles.burst(ParticleTypes.CLOUD, caster.position(), 20, 0.5, 0.2);
				}
			}
			case Fx.ANGEL_LANCE_SUMMON -> {
				add(new Burst(pos, 0.7, 8, ANGEL, 0xFFFFFF));
				add(new Shockwave(pos, Draw.camLook, 1.0, 0.2, 8, ANGEL2, 1f));
				Particles.burst(ParticleTypes.END_ROD, pos, 6, 0.2, 0.08);
			}
			case Fx.ANGEL_LANCE_HIT -> {
				add(new Burst(pos, 1.7, 10, ANGEL, 0xFFFFFF));
				add(new Shockwave(pos, UP, 2.6, 0.35, 10, ANGEL2, 1f));
				add(new Pillar(Particles.level() != null ? pos.subtract(0, 0.5, 0) : pos, 0.4, 5, 12, ANGEL, 0xFFFFFF));
				Particles.burst(ParticleTypes.END_ROD, pos, 22, 0.3, 0.3);
				Particles.burst(ParticleTypes.FIREWORK, pos, 10, 0.3, 0.2);
			}
			case Fx.ANGEL_SANCTUARY -> {
				double r = scale;
				add(new Sigil(pos.add(0, 0.06, 0), UP, r, extra, 0xFFE9A0, 6, 2, 0.02));
				add(new Sigil(pos.add(0, 0.07, 0), UP, r * 0.5, extra, ANGEL2, 4, 1, -0.04));
				add(new Pillar(pos, 0.7, 14, extra, ANGEL, 0xFFFFFF));
				add(new Shockwave(pos.add(0, 0.1, 0), UP, r, 0.5, 16, ANGEL, 1f));
				add(new Emitter(extra, i -> {
					for (int k = 0; k < 3; k++) {
						double a = RND.nextDouble() * Math.PI * 2, rr = Math.sqrt(RND.nextDouble()) * r;
						Particles.add(k == 0 ? ParticleTypes.GLOW : ParticleTypes.END_ROD, pos.add(Math.cos(a) * rr, 0.2, Math.sin(a) * rr), new Vec3(0, 0.08, 0));
					}
				}));
			}
			case Fx.ANGEL_LIGHT_PILLAR -> {
				add(new Pillar(pos, 0.7, scale, 20, ANGEL, 0xFFFFFF));
				add(new Shockwave(pos.add(0, 0.1, 0), UP, 2.2, 0.35, 10, ANGEL2, 1f));
				Particles.column(ParticleTypes.END_ROD, pos, 0.6, 15, 0.5);
			}
			case Fx.ANGEL_HALO_SKY -> {
				add(new SkyHalo(pos, scale, extra));
				add(new Emitter(extra, i -> {
					for (int k = 0; k < 3; k++) {
						double a = RND.nextDouble() * Math.PI * 2, rr = RND.nextDouble() * scale * 1.3;
						Particles.add(k == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.END_ROD,
								pos.add(Math.cos(a) * rr, -2 - RND.nextDouble() * 4, Math.sin(a) * rr), new Vec3(0, -0.15, 0));
					}
				}));
			}
			case Fx.ANGEL_JUDGMENT -> {
				double r = scale;
				add(new Judgment(pos, r, extra));
				add(new Shockwave(pos.add(0, 0.2, 0), UP, r * 3.2, r * 0.3, 26, 0xFFFFFF, 1f));
				add(new Shockwave(pos.add(0, 0.2, 0), UP, r * 2.2, r * 0.18, 20, GOD2, 1f));
				add(new Burst(pos, r, 20, ANGEL, 0xFFFFFF));
				Particles.burst(ParticleTypes.FIREWORK, pos, 150, 1, 1.0);
				Particles.burst(ParticleTypes.END_ROD, pos, 150, 1, 1.2);
				Particles.debris(pos, 80, 0.9);
			}

			// ------------------------------------------------------------ DEMON
			case Fx.DEMON_CLAW -> {
				boolean fin = extra == 3;
				double[] rolls = fin ? new double[]{0.8, Math.PI - 0.8} : new double[]{new double[]{0.9, Math.PI - 0.9, Math.PI / 2}[extra % 3]};
				for (double roll : rolls) {
					Vec3[] b = slashBasis(dir, roll);
					Vec3 sideOff = b[0].cross(b[1]).normalize();
					for (int k = -1; k <= 1; k++) {
						add(new Slash(pos.subtract(dir.scale(1.1)).add(sideOff.scale(k * 0.32)), b[0], b[1], fin ? 1.9 : 1.45, 0.22, 2.0, fin ? 9 : 7, DEMON));
					}
				}
				Particles.burst(Particles.dust(0xC00018, 1.4f), pos, fin ? 40 : 14, 0.5, 0.25);
				Particles.burst(ParticleTypes.SMOKE, pos, 6, 0.3, 0.05);
				if (fin) {
					add(new Burst(pos, 1.6, 9, DEMON, 0xFF8070));
					add(new Shockwave(pos, dir, 2.6, 0.4, 10, DEMON, 1f));
					Particles.burst(ParticleTypes.SOUL, pos, 12, 0.4, 0.15);
				}
			}
			case Fx.DEMON_CHAIN -> {
				int target = scale >= 0 ? (int) scale : -1;
				add(new Chain(fx.caster(), target, pos.add(dir), extra, 0xFF2040));
				add(new Burst(pos.add(dir), 0.9, 7, DEMON, 0xFF8070));
				Particles.burst(ParticleTypes.SOUL_FIRE_FLAME, pos.add(dir), 12, 0.3, 0.15);
			}
			case Fx.DEMON_DASH -> {
				if (caster != null) add(new Trail(caster.getId(), extra + 8, 1.4, 0xC00020, 0xFF6070, false));
				add(new Shockwave(pos.add(0, 1, 0), dir, 2.2, 0.4, 9, DEMON, 1f));
				Particles.burst(ParticleTypes.LARGE_SMOKE, pos.add(0, 1, 0), 10, 0.5, 0.05);
			}
			case Fx.DEMON_SPIKE -> {
				double h = scale;
				Vec3 tilt = new Vec3((RND.nextDouble() - 0.5) * 0.4, 1, (RND.nextDouble() - 0.5) * 0.4);
				add(new Spike(pos, tilt, h, 0.38 + h * 0.06, 26, DEMON_DARK, 0xFF1030));
				for (int k = 0; k < 2; k++) {
					double a = RND.nextDouble() * Math.PI * 2;
					Vec3 p = pos.add(Math.cos(a) * 0.6, 0, Math.sin(a) * 0.6);
					add(new Spike(p, new Vec3(Math.cos(a) * 0.6, 1, Math.sin(a) * 0.6), h * 0.5, 0.2, 22, DEMON_DARK, 0xFF1030));
				}
				Particles.debris(pos, 14, 0.4);
				Particles.burst(Particles.dust(0xB00018, 1.2f), pos.add(0, 0.5, 0), 10, 0.5, 0.2);
				Particles.column(ParticleTypes.SOUL_FIRE_FLAME, pos, 0.5, 6, 0.25);
			}
			case Fx.DEMON_PENTAGRAM -> {
				double r = scale;
				add(new Sigil(pos.add(0, 0.06, 0), UP, r, extra, 0xFF1030, 5, 2, 0.015));
				add(new Sigil(pos.add(0, 0.07, 0), UP, r * 0.45, extra, 0xFF5020, 5, 2, -0.03));
				add(new Shockwave(pos.add(0, 0.1, 0), UP, r, 0.8, 20, DEMON, 1f));
				add(new Emitter(extra, i -> {
					int n = 2 + i / 10;
					for (int k = 0; k < n; k++) {
						double a = RND.nextDouble() * Math.PI * 2, rr = Math.sqrt(RND.nextDouble()) * r;
						Vec3 p = pos.add(Math.cos(a) * rr, 0.15, Math.sin(a) * rr);
						Particles.add(k % 3 == 0 ? ParticleTypes.SOUL : Particles.dust(0xD00018, 1.5f), p, new Vec3(0, 0.12, 0));
					}
				}));
			}
			case Fx.DEMON_ERUPTION -> {
				double r = scale;
				add(new Burst(pos.add(0, 1, 0), r * 0.6, 22, DEMON, 0xFFB0A0));
				add(new Shockwave(pos.add(0, 0.2, 0), UP, r * 1.5, r * 0.15, 28, DEMON, 1f));
				add(new Shockwave(pos.add(0, 0.2, 0), UP, r * 1.0, r * 0.08, 20, 0xFF6040, 1f));
				for (int k = 0; k < 12; k++) {
					double a = k * Math.PI * 2 / 12 + RND.nextDouble() * 0.3, rr = r * (0.3 + RND.nextDouble() * 0.6);
					add(new Pillar(pos.add(Math.cos(a) * rr, 0, Math.sin(a) * rr), 0.9, 10 + RND.nextDouble() * 10, 32, 0xC00020, 0xFF6040));
				}
				for (int k = 0; k < 18; k++) {
					double a = k * Math.PI * 2 / 18;
					Vec3 p = pos.add(Math.cos(a) * r * 0.9, 0, Math.sin(a) * r * 0.9);
					add(new Spike(p, new Vec3(Math.cos(a) * 0.5, 1, Math.sin(a) * 0.5), 5 + RND.nextDouble() * 3, 0.8, 40, DEMON_DARK, 0xFF1030));
				}
				Particles.burst(ParticleTypes.EXPLOSION_EMITTER, pos, 2, 2, 0);
				Particles.column(ParticleTypes.SOUL, pos, r, 100, 0.4);
				Particles.burst(Particles.dust(0xD00018, 2f), pos.add(0, 1, 0), 120, r * 0.5, 0.6);
				Particles.burst(ParticleTypes.LARGE_SMOKE, pos, 60, r * 0.4, 0.2);
				Particles.burst(ParticleTypes.LAVA, pos, 30, 2, 0.6);
				Particles.debris(pos, 80, 0.9);
			}
			case Fx.DEMON_FORM -> {
				if (caster != null) {
					ClientState.addForm(Form.DEMON, caster.getId(), extra);
					add(new Shockwave(caster.position().add(0, 0.1, 0), UP, 4.5, 0.6, 16, DEMON, 1f));
					add(new Burst(chest(caster), 1.8, 10, DEMON, 0xFF8070));
					Particles.burst(ParticleTypes.SOUL, chest(caster), 40, 0.8, 0.25);
					Particles.burst(ParticleTypes.LARGE_SMOKE, caster.position(), 30, 1, 0.08);
				}
			}
			case Fx.DEMON_DRAIN -> {
				if (caster != null) {
					Vec3 to = chest(caster);
					add(new Beam(pos, to, 0.12, 7, 0xFF2040));
					for (int i = 0; i < 12; i++) {
						Vec3 p = pos.add(Particles.randDir().scale(0.4));
						Particles.add(Particles.dust(0xE00020, 1.0f), p, to.subtract(p).scale(0.1));
					}
				}
			}

			// ------------------------------------------------------------ GOD
			case Fx.GOD_PALM -> {
				boolean fin = extra == 3;
				add(new Shockwave(pos, dir, fin ? 3.6 : 1.5, fin ? 0.6 : 0.35, fin ? 12 : 8, GOD, 1f));
				add(new Shockwave(pos, dir, fin ? 2.4 : 1.0, 0.18, fin ? 10 : 6, 0xFFFFFF, 1f));
				add(new Burst(pos, fin ? 2.0 : 0.7, fin ? 9 : 6, GOD, 0xFFFFFF));
				add(new Sigil(pos, dir, fin ? 1.8 : 0.9, fin ? 12 : 8, GOD2, 6, 2, 0.25));
				Particles.burst(ParticleTypes.ELECTRIC_SPARK, pos, fin ? 40 : 18, 0.3, 0.4);
				Particles.burst(ParticleTypes.END_ROD, pos, fin ? 20 : 6, 0.3, 0.2);
				if (fin) Particles.burst(ParticleTypes.FIREWORK, pos, 20, 0.3, 0.3);
			}
			case Fx.GOD_SMITE -> {
				double r = scale;
				Vec3 sky = pos.add((RND.nextDouble() - 0.5) * 6, 40, (RND.nextDouble() - 0.5) * 6);
				add(new Bolt(sky, pos, 0.28, 12, 0xFFD870));
				add(new Bolt(sky.add(2, 0, -1), pos, 0.12, 8, 0xFFFFFF));
				add(new Burst(pos.add(0, 0.5, 0), r, 10, GOD, 0xFFFFFF));
				add(new Shockwave(pos.add(0, 0.1, 0), UP, r * 2, 0.6, 12, GOD, 1f));
				add(new Sigil(pos.add(0, 0.06, 0), UP, r * 1.2, 22, GOD2, 6, 2, 0.1));
				Particles.burst(ParticleTypes.ELECTRIC_SPARK, pos, 50, 0.5, 0.6);
				Particles.burst(ParticleTypes.FIREWORK, pos, 25, 0.3, 0.4);
				Particles.debris(pos, 20, 0.5);
				if (mc.player != null && mc.player.position().distanceTo(pos) < 30) ScreenFx.flash(0xFFF2C0, 0.25f, 6);
			}
			case Fx.GOD_TIMESTOP -> {
				add(new Dome(pos, scale, extra));
				add(new Shockwave(pos.add(0, 1, 0), UP, scale * 1.1, 1.5, 14, 0xFFFFFF, 1f));
				Particles.burst(ParticleTypes.END_ROD, pos.add(0, 1, 0), 60, 1, 0.8);
				if (mc.player != null && mc.player.position().distanceTo(pos) < scale + 4) {
					ScreenFx.tint(0xC8B888, 0.6f, extra);
					ScreenFx.flash(0xFFFFFF, 0.6f, 10);
				}
			}
			case Fx.GOD_SWORD_SUMMON -> {
				add(new Burst(pos, 0.8, 8, GOD, 0xFFFFFF));
				add(new Shockwave(pos, Draw.camLook, 1.1, 0.2, 8, GOD2, 1f));
				Particles.burst(ParticleTypes.ELECTRIC_SPARK, pos, 8, 0.2, 0.15);
			}
			case Fx.GOD_SWORD_HIT -> {
				add(new Burst(pos, 1.9, 10, GOD, 0xFFFFFF));
				add(new Shockwave(pos, UP, 3, 0.4, 10, GOD2, 1f));
				Particles.burst(ParticleTypes.ELECTRIC_SPARK, pos, 20, 0.3, 0.4);
				Particles.burst(ParticleTypes.CRIT, pos, 12, 0.3, 0.4);
				Particles.debris(pos, 10, 0.4);
			}
			case Fx.GOD_RINGS -> {
				if (caster != null) {
					ClientState.addForm(Form.RINGS, caster.getId(), extra);
					add(new Shockwave(caster.position().add(0, 0.1, 0), UP, 6, 0.6, 16, GOD, 1f));
					add(new Sigil(caster.position().add(0, 0.06, 0), UP, 6, extra, GOD, 8, 3, 0.03));
					add(new Emitter(extra, i -> {
						Entity c = entity(fx.caster());
						if (c == null) return;
						for (int k = 0; k < 2; k++) {
							Particles.add(ParticleTypes.END_ROD, c.position().add(Particles.randDir().scale(2.5)), new Vec3(0, 0.05, 0));
						}
					}));
				}
			}
			case Fx.GOD_WRATH -> {
				double r = scale;
				Vec3 c = pos.add(0, 2, 0);
				add(new Burst(c, r * 0.5, 36, GOD, 0xFFFFFF));
				add(new Burst(c, r * 0.25, 22, 0xFFFFFF, 0xFFFFFF));
				add(new Shockwave(pos.add(0, 0.2, 0), UP, r, r * 0.08, 36, GOD, 1f));
				add(new Shockwave(pos.add(0, 0.2, 0), UP, r * 0.7, r * 0.04, 28, 0xFFFFFF, 1f));
				add(new Shockwave(c, new Vec3(0.4, 1, -0.3), r * 0.8, r * 0.03, 30, GOD2, 1f));
				add(new Shockwave(c, new Vec3(-0.5, 1, 0.2), r * 0.8, r * 0.03, 30, GOD2, 1f));
				add(new Sigil(pos.add(0, 0.06, 0), UP, r * 0.7, 70, GOD, 12, 5, 0.01));
				add(new Pillar(pos, 2.6, 70, 45, GOD, 0xFFFFFF));
				Particles.burst(ParticleTypes.FIREWORK, c, 220, 1, 1.2);
				Particles.burst(ParticleTypes.END_ROD, c, 220, 1, 1.4);
				Particles.burst(ParticleTypes.ELECTRIC_SPARK, c, 120, 2, 0.8);
				Particles.debris(pos, 80, 0.9);
			}
			case Fx.GOD_LIGHTNING -> {
				Vec3 sky = pos.add((RND.nextDouble() - 0.5) * 8, 45, (RND.nextDouble() - 0.5) * 8);
				add(new Bolt(sky, pos, 0.32, 13, 0xFFD870));
				add(new Burst(pos.add(0, 0.5, 0), 2.2, 10, GOD, 0xFFFFFF));
				add(new Shockwave(pos.add(0, 0.1, 0), UP, 3.8, 0.5, 12, GOD, 1f));
				Particles.burst(ParticleTypes.ELECTRIC_SPARK, pos, 30, 0.5, 0.6);
				Particles.debris(pos, 12, 0.5);
			}

			// ------------------------------------------------------------ AWAKENING
			case Fx.AWAKEN_CHARGE -> {
				if (caster == null) break;
				PowerType t = ClientState.powerOf(caster);
				int col = t == PowerType.FIRE ? 0x3EA8FF : t.color;
				Vec3 feet = caster.position();
				add(new Sigil(feet.add(0, 0.06, 0), UP, 6, extra + 30, col, 8, 3, 0.12));
				add(new Sigil(feet.add(0, 0.07, 0), UP, 3.2, extra + 30, t.color2, 6, 2, -0.2));
				add(new Pillar(feet, 0.35, 30, extra + 10, col, 0xFFFFFF));
				add(new Emitter(extra, i -> {
					Entity c = entity(fx.caster());
					if (c == null) return;
					Vec3 ch = chest(c);
					for (int k = 0; k < 6 + i / 6; k++) {
						Vec3 d = Particles.randDir();
						Vec3 from = ch.add(d.scale(4 + RND.nextDouble() * 3));
						Particles.add(k % 2 == 0 ? powerParticle(t) : ParticleTypes.END_ROD, from, ch.subtract(from).scale(0.14));
					}
					if (i % 6 == 0) {
						Vec3 from = ch.add(Particles.randDir().scale(5));
						add(new Bolt(from, ch, 0.08, 5, col));
					}
					if (i % 12 == 0) add(new Shockwave(c.position().add(0, 0.1, 0), UP, 6, 0.4, 14, col, 0.8f));
				}));
			}
			case Fx.AWAKEN_BURST -> {
				PowerType t = PowerType.byOrdinal(extra);
				int col = t == PowerType.FIRE ? 0x3EA8FF : t.color;
				Vec3 feet = caster != null ? caster.position() : pos;
				Vec3 ch = feet.add(0, 1.2, 0);
				add(new Pillar(feet, 0.8, 90, 45, col, 0xFFFFFF));
				add(new Burst(ch, 3.0, 14, col, 0xFFFFFF));
				add(new Burst(ch, 1.6, 10, 0xFFFFFF, 0xFFFFFF));
				add(new Shockwave(feet.add(0, 0.2, 0), UP, 32, 3.0, 34, col, 1f));
				add(new Shockwave(feet.add(0, 0.2, 0), UP, 22, 1.5, 26, 0xFFFFFF, 1f));
				add(new Shockwave(ch, new Vec3(0.3, 1, 0.1), 18, 0.8, 30, t.color2, 1f));
				add(new Shockwave(ch, new Vec3(-0.4, 1, -0.2), 18, 0.8, 30, t.color2, 1f));
				add(new Sigil(feet.add(0, 0.06, 0), UP, 12, 70, col, 10, 3, 0.04));
				Particles.burst(powerParticle(t), ch, 300, 1, 1.4);
				Particles.burst(ParticleTypes.END_ROD, ch, 200, 1, 1.1);
				Particles.burst(ParticleTypes.FIREWORK, ch, 120, 1, 0.9);
				Particles.burst(ParticleTypes.EXPLOSION_EMITTER, ch, 2, 1, 0);
			}
			case Fx.AWAKEN_END -> {
				PowerType t = PowerType.byOrdinal(extra);
				Vec3 ch = caster != null ? chest(caster) : pos;
				add(new Burst(ch, 2.5, 12, t.color, 0xFFFFFF));
				for (int i = 0; i < 80; i++) {
					Vec3 d = Particles.randDir();
					Particles.add(powerParticle(t), ch.add(d.scale(3)), d.scale(-0.25));
				}
			}
			case Fx.TAKEOFF -> {
				if (caster == null) break;
				PowerType t = PowerType.byOrdinal(extra);
				int col = t == PowerType.FIRE ? 0x3EA8FF : t.color;
				Vec3 feet = caster.position();
				add(new Shockwave(feet.add(0, 0.1, 0), UP, 4.5, 0.6, 14, col, 0.9f));
				add(new Burst(feet.add(0, 0.4, 0), 1.4, 8, col, 0xFFFFFF));
				Particles.burst(ParticleTypes.CLOUD, feet, 20, 0.6, 0.25);
				switch (t) {
					case SPACE -> {
						add(new Burst(feet.add(0, -0.5, 0), 1.8, 12, 0xB050FF, 0xFF9040));
						Particles.burst(ParticleTypes.REVERSE_PORTAL, feet.add(0, -0.5, 0), 40, 0.8, 0.4);
					}
					case ANGEL -> Particles.burst(ParticleTypes.END_ROD, chest(caster), 25, 0.8, 0.3);
					case DEMON -> Particles.burst(ParticleTypes.LARGE_SMOKE, feet, 25, 0.8, 0.2);
					case FIRE -> Particles.burst(ParticleTypes.SOUL_FIRE_FLAME, feet, 30, 0.6, 0.3);
					case GOD -> Particles.burst(ParticleTypes.ELECTRIC_SPARK, chest(caster), 30, 0.8, 0.5);
					default -> {}
				}
			}
			// ------------------------------------------------------------ ENTITY 666
			case Fx.ENTITY_CLAW -> {
				boolean fin = extra == 3;
				// horizontal rakes: even hits sweep right-to-left, odd hits left-to-right, the finisher crosses both ways
				double[] rolls = fin ? new double[]{Math.PI + 0.28, -0.28} : new double[]{extra % 2 == 0 ? Math.PI + 0.1 : -0.1};
				for (double roll : rolls) {
					Vec3[] b = slashBasis(dir, roll);
					Vec3 sideOff = b[0].cross(b[1]).normalize();
					for (int k = -2; k <= 1; k++) {
						add(new Slash(pos.subtract(dir.scale(1.1)).add(sideOff.scale(k * 0.16 + 0.08)), b[0], b[1], fin ? 2.5 : 2.1, 0.1, 2.9, fin ? 9 : 7, 0x9A0012));
					}
				}
				Particles.burst(ParticleTypes.SMOKE, pos, fin ? 20 : 8, 0.4, 0.08);
				if (fin) add(new Shockwave(pos, dir, 2.2, 0.3, 9, 0x6A0010, 0.8f));
			}
			case Fx.ENTITY_FLICKER -> {
				Vec3 to = pos.add(dir);
				for (Vec3 at : new Vec3[]{pos, to}) {
					Particles.burst(ParticleTypes.SMOKE, at.add(0, 1, 0), 40, 0.5, 0.2);
					Particles.burst(ParticleTypes.LARGE_SMOKE, at.add(0, 1, 0), 15, 0.4, 0.05);
					add(new Burst(at.add(0, 1, 0), 1.2, 6, 0x6A0010, 0x000000));
				}
				for (int k = 0; k <= 30; k++) Particles.add(ParticleTypes.SMOKE, pos.add(0, 1, 0).lerp(to.add(0, 1, 0), k / 30.0), Vec3.ZERO);
				Minecraft m = Minecraft.getInstance();
				if (m.player != null && m.player.position().distanceTo(to) < 4 && !self) ScreenFx.flash(0x000000, 0.8f, 6);
				if (self) ScreenFx.flash(0x000000, 0.6f, 5);
			}
			case Fx.ENTITY_MIMIC -> {
				Particles.burst(ParticleTypes.SMOKE, pos.add(0, 0.8, 0), 30, 0.5, 0.15);
				Particles.burst(ParticleTypes.LARGE_SMOKE, pos.add(0, 0.6, 0), 12, 0.5, 0.05);
			}
			case Fx.ENTITY_FAKE_DEATH -> {
				Particles.burst(ParticleTypes.SMOKE, pos, 50, 0.5, 0.25);
				Particles.burst(ParticleTypes.SMOKE, pos, 25, 0.5, 0.1);
				add(new Burst(pos, 1.0, 6, 0x400008, 0x000000));
			}
			case Fx.ENTITY_JUMPSCARE -> {
				Minecraft m = Minecraft.getInstance();
				if (m.player != null && m.player.getId() == extra) HorrorFx.jumpscare();
				if (caster != null) Particles.burst(ParticleTypes.SMOKE, caster.position().add(0, 1.5, 0), 60, 0.6, 0.3);
			}
			case Fx.ENTITY_DOMAIN -> {
				HorrorFx.domain(pos, scale, extra, caster != null ? caster.getId() : -1);
				add(new Shockwave(pos.add(0, 0.1, 0), UP, scale, 0.8, 18, 0x8A0010, 1f));
				if (self) ScreenFx.tint(0x300004, 0.35f, extra);
			}
			case Fx.ENTITY_FEAST -> HorrorFx.feast(caster != null ? caster.getId() : -1, extra, pos, dir, scale > 0.5f);
			case Fx.ENTITY_THROW -> HorrorFx.throwBody(extra, dir);
			case Fx.ENTITY_TOSS -> HorrorFx.toss(extra, dir);
			case Fx.ENTITY_GORE -> {
				HorrorFx.gore(extra, pos);
				Particles.burst(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
						net.minecraft.world.level.block.Blocks.BONE_BLOCK.defaultBlockState()), pos, 12, 0.2, 0.3);
				Minecraft m = Minecraft.getInstance();
				if (m.player != null && m.player.getId() == extra) ScreenFx.flash(0x200000, 1f, 60);
				else if (m.player != null && m.player.position().distanceTo(pos) < 12) ScreenFx.flash(0x8A0000, 0.35f, 8);
			}
			case Fx.ENTITY_BITE -> HorrorFx.blood(pos, scale < 1 ? 10 : 30, 0.25, 0.25);
			case Fx.ENTITY_DEVOUR -> {
				ClientState.hide(extra, 80);
				HorrorFx.blood(pos, 90, 0.5, 0.35);
				Particles.burst(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
						net.minecraft.world.level.block.Blocks.BONE_BLOCK.defaultBlockState()), pos, 20, 0.4, 0.3);
			}
			case Fx.METER_FULL -> {
				PowerType t = PowerType.byOrdinal(extra);
				if (caster == null) break;
				add(new Shockwave(caster.position().add(0, 0.1, 0), UP, 5, 0.5, 16, t.color, 1f));
				add(new Burst(chest(caster), 1.5, 10, t.color, 0xFFFFFF));
				Particles.burst(ParticleTypes.TOTEM_OF_UNDYING, chest(caster), 50, 0.5, 0.6);
				if (self) ScreenFx.flash(t.color, 0.3f, 10);
			}
			default -> {}
		}
		// awakened casts get an extra layer of power everywhere
		if (aw && fx.fx() >= 10 && fx.fx() < 60) {
			PowerType t = caster instanceof net.minecraft.world.entity.player.Player pl ? ClientState.powerOf(pl) : PowerType.NONE;
			int col = t == PowerType.FIRE ? 0x3EA8FF : t == PowerType.NONE ? 0xFFFFFF : t.color;
			add(new Shockwave(pos, dir.lengthSqr() > 1e-4 ? dir : UP, 3.5, 0.5, 10, col, 0.8f));
			add(new Burst(pos, 1.6, 8, col, 0xFFFFFF));
			Particles.burst(ParticleTypes.END_ROD, pos, 14, 0.5, 0.5);
		}
	}

	static int rgb(Vec3 c) {
		return ((int) (Mth.clamp(c.x, 0, 1) * 255) << 16) | ((int) (Mth.clamp(c.y, 0, 1) * 255) << 8) | (int) (Mth.clamp(c.z, 0, 1) * 255);
	}
}
