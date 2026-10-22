package com.superpowers.client.fx;

import com.superpowers.client.ClientState;
import com.superpowers.client.ClientState.Form;
import com.superpowers.client.model.Mesh;
import com.superpowers.client.model.MeshDraw;
import com.superpowers.entity.PowerProjectile;
import com.superpowers.power.PowerType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Per-player power auras, temporary forms, awakened forms and projectile models. */
public final class Auras {
	private Auras() {}

	static final Vec3 UP = new Vec3(0, 1, 0);
	public static final int AZURE = 0x3EA8FF;

	/** Body + head frame of a player for this frame. */
	/** fwd/up follow the flight lean; at(y) is a point on the body y blocks above the feet. */
	public record Frame(Vec3 pos, Vec3 fwd, Vec3 right, Vec3 head, Vec3 hFwd, Vec3 hUp, Vec3 hRight, boolean crouch, float bright, Vec3 up, Vec3 pivot) {
		public Vec3 at(double y) {
			return pivot.add(up.scale(y - 0.9));
		}
	}

	/** Wing motion of the player being drawn: flying / walking / running blend (idle is the rest). */
	static float wFly, wWalk, wRun;

	public static Frame frame(Player p, float pt) {
		Vec3 pos = p.getPosition(pt);
		float by = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 flat = new Vec3(-Math.sin(by), 0, Math.cos(by));
		Vec3 right = flat.cross(UP).normalize();
		double tilt = ClientState.flightTilt(p, pt) * Mth.DEG_TO_RAD;
		Vec3 up = UP.scale(Math.cos(tilt)).add(flat.scale(Math.sin(tilt)));
		Vec3 fwd = flat.scale(Math.cos(tilt)).subtract(UP.scale(Math.sin(tilt)));
		Vec3 pivot = pos.add(0, 0.9, 0);
		boolean crouch = p.isCrouching();
		Vec3 neck = pivot.add(up.scale((crouch ? 1.2 : 1.5) - 0.9));
		float hy = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot) * Mth.DEG_TO_RAD;
		float hp = p.getViewXRot(pt) * Mth.DEG_TO_RAD;
		Vec3 hFwd = new Vec3(-Math.sin(hy) * Math.cos(hp), -Math.sin(hp), Math.cos(hy) * Math.cos(hp));
		Vec3 hRight = new Vec3(-Math.cos(hy), 0, -Math.sin(hy));
		Vec3 hUp = hRight.cross(hFwd).normalize();
		return new Frame(pos, fwd, right, neck, hFwd, hUp, hRight, crouch, MeshDraw.brightness(pos.add(0, 1.2, 0)), up, pivot);
	}

	static boolean hidden(Player p) {
		Minecraft mc = Minecraft.getInstance();
		if (p.isInvisible() || p.isSpectator()) return true;
		return p == mc.getCameraEntity() && !mc.gameRenderer.getMainCamera().isDetached();
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		long t = ClientState.ticks;
		for (Player p : mc.level.players()) {
			if (p.isInvisible() || p.isSpectator()) continue;
			PowerType type = ClientState.powerOf(p);
			boolean aw = ClientState.isAwakened(p);
			Vec3 c = p.position().add(0, 1, 0);
			int every = aw ? 1 : 1;
			switch (type) {
				case FIRE -> {
					if (aw) {
						Particles.add(ParticleTypes.SOUL_FIRE_FLAME, c.add(Particles.randDir().scale(0.6)), new Vec3(0, 0.06, 0));
						Particles.add(ParticleTypes.SOUL_FIRE_FLAME, c.add(Particles.randDir().scale(0.9)), new Vec3(0, 0.04, 0));
					} else if (t % 3 == 0) Particles.add(ParticleTypes.SMALL_FLAME, c.add(Particles.randDir().scale(0.5)), new Vec3(0, 0.03, 0));
				}
				case DEMON -> {
					if (aw || t % 6 == 0) Particles.add(Particles.dust(0xB00018, 0.8f), c.add(Particles.randDir().scale(0.7)), new Vec3(0, 0.02, 0));
					if (aw && t % 2 == 0) Particles.add(ParticleTypes.LARGE_SMOKE, p.position().add(Particles.randDir().scale(0.6)), new Vec3(0, 0.02, 0));
				}
				case ANGEL -> {
					if (aw || t % 8 == 0) Particles.add(ParticleTypes.END_ROD, c.add(Particles.randDir().scale(aw ? 1.6 : 0.6)), new Vec3(0, -0.01, 0));
					if (aw && t % 3 == 0) Particles.add(ParticleTypes.WHITE_ASH, c.add(Particles.randDir().scale(2)), new Vec3(0, -0.05, 0));
				}
				case GOD -> {
					if (aw || t % 7 == 0) Particles.add(ParticleTypes.ELECTRIC_SPARK, c.add(Particles.randDir().scale(aw ? 1.5 : 0.7)), Vec3.ZERO);
					if (aw && t % 2 == 0) Particles.add(ParticleTypes.END_ROD, c.add(Particles.randDir().scale(2.5)), new Vec3(0, 0.04, 0));
				}
				case ENTITY -> {
					// nothing drifting in front of your own eyes in first person
					if (p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) continue;
					if (t % 4 == 0) Particles.add(ParticleTypes.SMOKE, p.position().add(Particles.randDir().scale(0.4)), new Vec3(0, 0.01, 0));
					if (aw) {
						if (t % 2 == 0) Particles.add(ParticleTypes.SMOKE, p.position().add(0, 0.2, 0).add(Particles.randDir().scale(0.8)), new Vec3(0, 0.02, 0));
						if (t % 9 == 0) Particles.add(Particles.dust(0x5A0006, 1.2f), p.position().add(0, 2.9, 0).add(Particles.randDir().scale(0.3)), new Vec3(0, -0.1, 0));
					}
				}
				case SPACE -> {
					if (aw || t % 5 == 0) Particles.add(ParticleTypes.REVERSE_PORTAL, c.add(Particles.randDir().scale(aw ? 1.6 : 0.7)), Vec3.ZERO);
					if (aw && t % 2 == 0) Particles.add(ParticleTypes.END_ROD, c.add(Particles.randDir().scale(2.2)), Vec3.ZERO);
				}
				default -> {}
			}
			if (ClientState.flight(p, 1) > 0.3f) flightParticles(p, type, t);
			if (ClientState.form(Form.WINGS, p.getId(), 0) > 0.5f && t % 4 == 0) {
				Particles.add(ParticleTypes.END_ROD, c.add(0, 0.4, 0).add(Particles.randDir().scale(1.2)), new Vec3(0, -0.03, 0));
			}
			if (ClientState.form(Form.DEMON, p.getId(), 0) > 0.5f) {
				Particles.add(ParticleTypes.SOUL, c.add(Particles.randDir().scale(1.0)), new Vec3(0, 0.05, 0));
			}
		}
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof PowerProjectile pr)) continue;
			Vec3 c = e.position();
			switch (pr.kind()) {
				case PowerProjectile.FIREBALL -> {
					Particles.burst(ParticleTypes.FLAME, c, 4, 0.4, 0.05);
					Particles.add(ParticleTypes.LARGE_SMOKE, c, Vec3.ZERO);
				}
				case PowerProjectile.METEOR -> {
					Particles.burst(ParticleTypes.FLAME, c, 3, 0.6, 0.05);
					Particles.burst(ParticleTypes.REVERSE_PORTAL, c, 3, 0.6, 0.1);
					Particles.add(ParticleTypes.LARGE_SMOKE, c, Vec3.ZERO);
				}
				case PowerProjectile.LANCE -> Particles.add(ParticleTypes.END_ROD, c, Vec3.ZERO);
				case PowerProjectile.SWORD -> Particles.add(ParticleTypes.ELECTRIC_SPARK, c, Vec3.ZERO);
				default -> {}
			}
		}
	}

	public static void render(boolean add, float pt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		float time = ClientState.ticks + pt;
		for (Player p : mc.level.players()) {
			PowerType type = ClientState.powerOf(p);
			float[] ws = ClientState.wingState(p, pt);
			wFly = ws[0];
			wWalk = ws[1];
			wRun = ws[2];
			if (hidden(p)) {
				// first person: you can still look down at the meteor under your feet
				if (type == PowerType.SPACE && wFly > 0.01f && !p.isSpectator() && !p.isInvisible()) meteorRide(frame(p, pt), time, wFly, add);
				continue;
			}
			float aw = ClientState.awakenStrength(p, pt);
			float wings = ClientState.form(Form.WINGS, p.getId(), pt);
			float demon = ClientState.form(Form.DEMON, p.getId(), pt);
			float rings = ClientState.form(Form.RINGS, p.getId(), pt);
			if (type == PowerType.NONE && wings + demon + rings <= 0) continue;
			Frame f = frame(p, pt);

			switch (type) {
				case ANGEL -> {
					halo(f, time, 1 + aw * 0.5f, add, aw);
					if (aw > 0) seraph(f, time, aw, add);
					else if (wings > 0) angelWingPair(f, time, wings, 0.6f, 0.15f, 1.32, add, 0, 0);
					if (aw > 0) eyes(f, 0xBFE8FF, aw, add);
				}
				case DEMON -> {
					float big = Math.max(demon, aw);
					horns(f, 0.55f + big * 0.45f, add);
					if (big > 0) demonWings(f, time, big, add);
					if (aw > 0) tail(f, time, aw, add);
					if (big > 0) eyes(f, 0xFF1020, big, add);
				}
				case GOD -> {
					float big = Math.max(rings, aw);
					disc(f, time, 0.45f + big * 0.8f, add, big);
					if (big > 0) godRings(f, time, big, add);
					if (aw > 0) {
						halo(f, time, 0.9f * aw, add, aw);
						eyes(f, 0xFFF2B0, aw, add);
					}
				}
				case ENTITY -> {
					if (aw > 0) {
						Creature.Pose ps = HorrorFx.playerPose(p, pt);
						ps.scale = 0.55f + 0.45f * aw;
						float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
						HorrorFx.rememberJoints(p.getId(), Creature.draw(p.getPosition(pt), yaw, ps, Math.max(0.3f, f.bright), add, 1f));
					} else {
						eyes(f, 0xFF1A10, 1f, add);
					}
				}
				case SPACE -> {
					planets(f, time, aw > 0 ? 5 : 3, 0.85 + aw * 0.6, 0.14f + aw * 0.08f, add);
					if (aw > 0) {
						crown(f, "star_crown", time, aw, 0, 0, add);
						galaxy(f, time, aw, add);
						eyes(f, 0x8FE8FF, aw, add);
					}
				}
				case FIRE -> {
					if (add) Draw.glow(f.at(1.0), 1.3 + aw * 1.2, aw > 0 ? AZURE : 0xFF5A10, 0.12f + aw * 0.15f);
					if (aw > 0) {
						crown(f, "flame_crown", time, aw, AZURE, 0.85f, add);
						flameWings(f, time, aw, add);
						eyes(f, 0x9FE0FF, aw, add);
					}
				}
				default -> {
					if (wings > 0) angelWingPair(f, time, wings, 0.6f, 0.15f, 1.32, add, 0, 0);
				}
			}
			if (type != PowerType.ANGEL && type != PowerType.NONE && wings > 0) angelWingPair(f, time, wings, 0.6f, 0.15f, 1.32, add, 0, 0);
			if (wFly > 0.01f && type != PowerType.ENTITY) flight(p, f, type, time, wFly, add);
			if (aw > 0 && add) {
				float pulse = 0.75f + 0.25f * Mth.sin(time * 0.2f);
				Draw.glow(f.at(1.0), 2.2 * aw * pulse, type == PowerType.FIRE ? AZURE : type.color, 0.16f * aw);
			}
		}
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof PowerProjectile pr) projectile(pr, pt, time, add);
		}
	}

	// ------------------------------------------------------------------ helpers

	static Matrix4f bodyAt(Frame f, Vec3 pos, float scale) {
		return MeshDraw.frame(pos, f.right, f.up, f.fwd, scale);
	}

	static Matrix4f headAt(Frame f, Vec3 pos, float scale) {
		return MeshDraw.frame(pos, f.hRight, f.hUp, f.hFwd, scale);
	}

	static void mesh(String name, Matrix4f m, Frame f, boolean add, int tint, float tintAmt, float alpha) {
		Mesh mesh = Mesh.get(name);
		if (add) MeshDraw.drawGlow(mesh, m, tint, tintAmt, alpha);
		else MeshDraw.draw(mesh, m, f.bright, tint, tintAmt, alpha);
	}

	static void eyes(Frame f, int rgb, float a, boolean add) {
		if (!add) return;
		for (int s = -1; s <= 1; s += 2) {
			Vec3 e = f.head.add(f.hUp.scale(0.24)).add(f.hFwd.scale(0.27)).add(f.hRight.scale(0.1 * s));
			Draw.glow(e, 0.16, rgb, a);
			Draw.glow(e, 0.05, 0xFFFFFF, a);
		}
	}

	// ------------------------------------------------------------------ angel

	static void halo(Frame f, float time, float scale, boolean add, float aw) {
		Vec3 c = f.head.add(f.hUp.scale(0.68 + 0.03 * Math.sin(time * 0.12) + aw * 0.12));
		Matrix4f m = headAt(f, c, scale).rotateY(time * 0.02f);
		mesh("halo", m, f, add, 0, 0, 1f);
		if (aw > 0) {
			Matrix4f m2 = headAt(f, c.add(f.hUp.scale(0.05)), scale * 1.45f).rotateY(-time * 0.035f);
			mesh("halo", m2, f, add, 0, 0, aw);
		}
		if (add) Draw.glow(c, 0.7 * scale, 0xFFF0C0, 0.3f);
	}

	/** One pair of feathered wings. lift raises them, rootY is height on the back. */
	static void angelWingPair(Frame f, float time, float a, float scale, float baseLift, double rootY, boolean add, int tint, float tintAmt) {
		float flap = flap(time, 0.28f);
		for (int s = -1; s <= 1; s += 2) {
			Vec3 root = f.at(f.crouch ? rootY - 0.3 : rootY).subtract(f.fwd.scale(0.2)).add(f.right.scale(0.12 * s));
			Matrix4f m = bodyAt(f, root, 1f)
					.rotateY(s * (0.5f + wRun * 0.6f - wFly * 0.25f))
					.rotateZ(s * (baseLift + flap - wRun * 0.3f + wFly * 0.1f))
					.scale(s * scale * a, scale * a, scale * a);
			mesh("angel_wing", m, f, add, tint, tintAmt, 1f);
		}
		if (add && tintAmt == 0) Draw.glow(f.at(rootY).subtract(f.fwd.scale(0.3)), 0.8 * a, 0xFFF4C2, 0.35f * a);
	}

	/**
	 * Wing beat for the current motion: slow breathing when idle, a light beat while walking, folded back
	 * and steady when running, big strokes in flight. Fixed frequencies are blended so nothing jumps.
	 */
	static float flap(float time, float amp) {
		float idle = Mth.clamp(1 - wFly - wWalk - wRun, 0, 1);
		return amp * (0.35f * Mth.sin(time * 0.09f) * idle
				+ 0.6f * Mth.sin(time * 0.2f) * wWalk
				+ 0.2f * Mth.sin(time * 0.15f) * wRun
				+ 1.8f * Mth.sin(time * 0.45f) * wFly);
	}

	static void seraph(Frame f, float time, float a, boolean add) {
		angelWingPair(f, time, a, 0.55f, 0.55f, 1.45, add, 0, 0);
		angelWingPair(f, time + 6, a, 0.63f, 0.05f, 1.3, add, 0, 0);
		angelWingPair(f, time + 12, a, 0.44f, -0.5f, 1.1, add, 0, 0);
	}

	// ------------------------------------------------------------------ demon

	static void horns(Frame f, float scale, boolean add) {
		for (int s = -1; s <= 1; s += 2) {
			Vec3 base = f.head.add(f.hUp.scale(0.43)).add(f.hRight.scale(0.15 * s)).add(f.hFwd.scale(0.02));
			Matrix4f m = headAt(f, base, 1f).scale(s * scale, scale, scale);
			mesh("demon_horn", m, f, add, 0, 0, 1f);
		}
	}

	static void demonWings(Frame f, float time, float a, boolean add) {
		float flap = flap(time, 0.26f);
		for (int s = -1; s <= 1; s += 2) {
			Vec3 root = f.at(f.crouch ? 1.05 : 1.35).subtract(f.fwd.scale(0.2)).add(f.right.scale(0.12 * s));
			float sc = 0.66f * a;
			Matrix4f m = bodyAt(f, root, 1f)
					.rotateY(s * (0.45f + wRun * 0.6f - wFly * 0.2f))
					.rotateZ(s * (0.2f + flap - wRun * 0.25f + wFly * 0.1f))
					.scale(s * sc, sc, sc);
			mesh("demon_wing", m, f, add, 0xD0101E, 0.5f, 1f);
		}
		if (add) Draw.glow(f.at(1.3).subtract(f.fwd.scale(0.4)), 1.0 * a, 0xFF1020, 0.25f * a);
	}

	static void tail(Frame f, float time, float a, boolean add) {
		Vec3 base = f.at(f.crouch ? 0.6 : 0.78).subtract(f.fwd.scale(0.15));
		Matrix4f m = bodyAt(f, base, a).rotateY(0.35f * Mth.sin(time * 0.08f)).rotateX(0.1f * Mth.sin(time * 0.11f));
		mesh("demon_tail", m, f, add, 0, 0, 1f);
	}

	// ------------------------------------------------------------------ god

	static void disc(Frame f, float time, float scale, boolean add, float big) {
		Vec3 c = f.head.add(0, 0.25 + big * 0.2, 0).subtract(f.fwd.scale(0.42 + big * 0.2));
		Matrix4f m = bodyAt(f, c, scale).rotateZ(time * 0.01f);
		mesh("god_disc", m, f, add, 0, 0, 1f);
		if (add) Draw.glow(c, 0.9 * scale, 0xFFD870, 0.25f);
	}

	static void godRings(Frame f, float time, float a, boolean add) {
		Vec3 c = f.at(1.1);
		float[] radii = {1.5f, 1.95f, 2.4f};
		for (int i = 0; i < 3; i++) {
			float s = time * (0.05f + i * 0.025f) + i;
			Matrix4f m = MeshDraw.frame(c, new Vec3(1, 0, 0), UP, new Vec3(0, 0, 1), 1f)
					.rotateY(s * 0.7f + i).rotateX(s).rotateZ(s * 0.4f)
					.scale(radii[i] * (0.6f + 0.4f * a));
			mesh("god_ring", m, f, add, 0, 0, a);
		}
		if (add) Draw.glow(c, 1.4, 0xFFD870, a * 0.35f);
	}

	// ------------------------------------------------------------------ space

	static void planets(Frame f, float time, int count, double orbit, float scale, boolean add) {
		Vec3 c = f.at(1.0);
		int[] cols = {0x9A5CFF, 0x5CD6FF, 0xFFFFFF, 0xFF8AD0, 0x7CFFB0};
		for (int i = 0; i < count; i++) {
			double tilt = 0.5 + i * 0.6;
			Vec3 n = new Vec3(Math.sin(tilt) * Math.cos(i * 2.1), Math.cos(tilt), Math.sin(tilt) * Math.sin(i * 2.1)).normalize();
			Vec3[] uw = Draw.basis(n);
			double ang = time * (0.08 + i * 0.03) + i * 2;
			double r = orbit + i * 0.12;
			Vec3 p = Draw.onCircle(c, uw[0], uw[1], r, ang);
			Matrix4f m = MeshDraw.frame(p, new Vec3(1, 0, 0), UP, new Vec3(0, 0, 1), scale).rotateY(time * 0.05f + i).rotateX(0.3f * i);
			mesh("planet", m, f, add, cols[i], i == 0 ? 0f : 0.5f, 1f);
			if (add) {
				Draw.glow(p, scale * 1.6, cols[i], 0.5f);
				for (int k = 1; k <= 6; k++) {
					Vec3 q = Draw.onCircle(c, uw[0], uw[1], r, ang - k * 0.12);
					Draw.glow(q, scale * 0.8 * (1 - k / 7.0), cols[i], 0.35f * (1 - k / 7f));
				}
			}
		}
	}

	static void crown(Frame f, String name, float time, float a, int tint, float tintAmt, boolean add) {
		Vec3 c = f.head.add(f.hUp.scale(0.52));
		Matrix4f m = headAt(f, c, a).rotateY(time * 0.03f);
		mesh(name, m, f, add, tint, tintAmt, 1f);
		if (add) Draw.glow(c.add(f.hUp.scale(0.15)), 0.6 * a, tint != 0 ? tint : 0xBFE0FF, 0.35f * a);
	}

	static void galaxy(Frame f, float time, float a, boolean add) {
		if (!add) return;
		Vec3 c = f.at(1.2).subtract(f.fwd.scale(0.6));
		Vec3 n = f.fwd;
		Vec3[] uw = Draw.basis(n);
		for (int arm = 0; arm < 3; arm++) {
			for (int k = 0; k < 26; k++) {
				double t = k / 26.0;
				double ang = time * 0.02 + arm * Math.PI * 2 / 3 + t * 4.2;
				Vec3 p = Draw.onCircle(c, uw[0], uw[1], 0.2 + t * 1.8 * a, ang);
				int col = Draw.mix(0xFFFFFF, arm == 0 ? 0x9A5CFF : arm == 1 ? 0x5CD6FF : 0xFF8AD0, (float) t);
				Draw.sparkle(p, 0.09 * (1.2 - t), col, (float) (a * (1 - t * 0.6)));
			}
		}
		Draw.glow(c, 1.1 * a, 0xC8B0FF, 0.4f * a);
	}

	// ------------------------------------------------------------------ fire

	static void flameWings(Frame f, float time, float a, boolean add) {
		float flicker = 0.85f + 0.15f * Mth.sin(time * 1.3f);
		angelWingPair(f, time, a, 0.62f, 0.3f, 1.35, add, AZURE, 0.92f * flicker);
		if (add) {
			for (int s = -1; s <= 1; s += 2) {
				Vec3 tip = f.at(1.8).subtract(f.fwd.scale(0.5)).add(f.right.scale(1.2 * s));
				Draw.glow(tip, 0.9, AZURE, 0.35f * a);
			}
		}
	}

	// ------------------------------------------------------------------ flight

	static Vec3 velocity(Player p) {
		return new Vec3(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo);
	}

	/** Trails and mounts while flying. Wings themselves come from the awakened forms above. */
	static void flight(Player p, Frame f, PowerType type, float time, float fly, boolean add) {
		Vec3 vel = velocity(p);
		double speed = vel.length();
		Vec3 back = speed > 0.03 ? vel.scale(-1 / speed) : new Vec3(0, -1, 0);
		float sp = (float) Mth.clamp(speed / 0.8, 0, 1);
		switch (type) {
			case SPACE -> meteorRide(f, time, fly, add);
			case GOD -> {
				if (!add) break;
				float lean = ClientState.flightLean(p, 0);
				Vec3 fist = f.at(2.3);
				Draw.glow(fist, 0.55 + 0.4 * sp, 0xFFF2B0, 0.8f * fly * lean);
				Draw.beam(f.pivot, f.pivot.add(back.scale(1.5 + 6 * sp)), 0.9, 0.05, 0xFFC93C, 0.45f * fly * sp, 0);
				Draw.beam(f.pivot, f.pivot.add(back.scale(1 + 4 * sp)), 0.35, 0.0, 0xFFFFFF, 0.6f * fly * sp, 0);
				if (sp > 0.6f) Draw.ring(fist.add(back.scale(0.4)), back, 0.9 + 0.15 * Mth.sin(time * 0.8f), 0.12, 0xFFE9A0, 0.35f * (sp - 0.6f) * 2.5f, 32);
			}
			case FIRE -> {
				if (!add) break;
				for (int s = -1; s <= 1; s += 2) {
					Vec3 foot = f.at(0.05).add(f.right.scale(0.12 * s));
					Vec3 dir = back.add(0, -0.6, 0).normalize();
					double len = 1.2 + 2.2 * sp + 0.2 * Mth.sin(time * 1.7f + s);
					Draw.beam(foot, foot.add(dir.scale(len)), 0.42, 0.04, AZURE, 0.75f * fly, 0);
					Draw.beam(foot, foot.add(dir.scale(len * 0.55)), 0.16, 0.0, 0xFFFFFF, 0.8f * fly, 0);
					Draw.glow(foot, 0.45, AZURE, 0.6f * fly);
				}
			}
			case ANGEL, DEMON -> {
				if (!add || sp < 0.05f) break;
				int col = type == PowerType.DEMON ? 0xFF1020 : 0xFFF4C2;
				Draw.beam(f.at(1.2), f.at(1.2).add(back.scale(1 + 4 * sp)), 1.1, 0.05, col, 0.22f * fly * sp, 0);
			}
			default -> {}
		}
	}

	/** Space rides a burning meteor. */
	static void meteorRide(Frame f, float time, float fly, boolean add) {
		Vec3 c = f.pos.add(0, -0.5, 0);
		float sc = 0.8f * fly;
		Matrix4f m = MeshDraw.frame(c, new Vec3(1, 0, 0), UP, new Vec3(0, 0, 1), sc).rotateY(time * 0.03f).rotateX(0.25f);
		if (add) {
			MeshDraw.drawGlow(Mesh.get("meteor"), m, 0, 0, fly);
			Draw.glow(c, 1.5, 0xB050FF, 0.35f * fly);
			Draw.glow(c.add(0, -0.5, 0), 1.0, 0xFF9040, 0.5f * fly);
		} else {
			MeshDraw.draw(Mesh.get("meteor"), m, Math.max(f.bright, 0.6f), 0, 0, 1f);
		}
	}

	static void flightParticles(Player p, PowerType type, long t) {
		Vec3 vel = velocity(p);
		Vec3 feet = p.position();
		Vec3 chest = feet.add(0, 1.1, 0);
		switch (type) {
			case FIRE -> {
				for (int i = 0; i < 3; i++) {
					Particles.add(ParticleTypes.SOUL_FIRE_FLAME, feet.add(Particles.randDir().scale(0.2)), vel.scale(-0.3).add(0, -0.12, 0));
				}
			}
			case SPACE -> {
				Vec3 rock = feet.add(0, -0.5, 0);
				Particles.add(ParticleTypes.FLAME, rock.add(Particles.randDir().scale(0.5)), vel.scale(-0.2).add(0, -0.03, 0));
				Particles.add(ParticleTypes.REVERSE_PORTAL, rock.add(Particles.randDir().scale(0.6)), Vec3.ZERO);
				if (t % 2 == 0) Particles.add(ParticleTypes.LARGE_SMOKE, rock.add(Particles.randDir().scale(0.4)), vel.scale(-0.1));
			}
			case ANGEL -> {
				if (t % 2 == 0) Particles.add(ParticleTypes.END_ROD, chest.add(Particles.randDir().scale(0.9)), vel.scale(-0.2));
				if (t % 3 == 0) Particles.add(ParticleTypes.WHITE_ASH, chest.add(Particles.randDir().scale(1.2)), new Vec3(0, -0.05, 0));
			}
			case DEMON -> {
				Particles.add(Particles.dust(0xC0101E, 1.2f), chest.add(Particles.randDir().scale(0.8)), vel.scale(-0.2));
				if (t % 2 == 0) Particles.add(ParticleTypes.LARGE_SMOKE, chest.add(Particles.randDir().scale(0.6)), vel.scale(-0.15));
			}
			case GOD -> {
				Particles.add(ParticleTypes.ELECTRIC_SPARK, chest.add(Particles.randDir().scale(0.7)), vel.scale(-0.3));
				if (t % 2 == 0) Particles.add(ParticleTypes.END_ROD, chest.add(Particles.randDir().scale(0.5)), vel.scale(-0.2));
			}
			default -> {}
		}
	}

	// ------------------------------------------------------------------ projectiles

	static void projectile(PowerProjectile pr, float pt, float time, boolean add) {
		Vec3 c = pr.getPosition(pt).add(0, pr.getBbHeight() / 2, 0);
		Vec3 dir = pr.getViewVector(pt);
		if (dir.lengthSqr() < 1e-6) dir = new Vec3(0, 0, 1);
		dir = dir.normalize();
		float bright = MeshDraw.brightness(c);
		boolean aw = pr.getOwner() instanceof Player op && ClientState.isAwakened(op);
		switch (pr.kind()) {
			case PowerProjectile.FIREBALL -> {
				if (add) {
					Draw.beam(c, c.subtract(dir.scale(3.5)), 1.4, 0.1, aw ? AZURE : 0xFF6010, 0.6f, 0);
					Effects.Sun.renderSun(c, aw ? 0.65 : 0.45, time, 1f);
					if (aw) Draw.glow(c, 1.6, AZURE, 0.6f);
				}
			}
			case PowerProjectile.METEOR -> {
				Matrix4f m = MeshDraw.frame(c, new Vec3(1, 0, 0), UP, new Vec3(0, 0, 1), 1f).rotateXYZ(time * 0.11f, time * 0.07f, time * 0.05f);
				if (add) {
					Draw.beam(c, c.subtract(dir.scale(7)), 2.4, 0.2, 0xB050FF, 0.7f, 0);
					Draw.beam(c, c.subtract(dir.scale(4)), 1.4, 0.1, 0xFF9040, 0.8f, 0);
					Draw.glow(c, 2.4, 0xB050FF, 0.5f);
					MeshDraw.drawGlow(Mesh.get("meteor"), m, 0, 0, 1f);
				} else {
					MeshDraw.draw(Mesh.get("meteor"), m, Math.max(bright, 0.6f), 0, 0, 1f);
				}
			}
			case PowerProjectile.LANCE -> {
				Matrix4f m = MeshDraw.facing(c, dir, 0.85f);
				if (add) {
					MeshDraw.drawGlow(Mesh.get("holy_lance"), m, 0, 0, 1f);
					Vec3 tip = c.add(dir.scale(1.15));
					Draw.glow(tip, 0.7, 0xFFFFFF, 0.9f);
					Draw.beam(c.subtract(dir.scale(1.0)), c.subtract(dir.scale(2.8)), 0.4, 0.0, 0x8FE3FF, 0.45f, 0);
					Draw.glow(c, 1.0, 0xFFF4C2, 0.3f);
				} else {
					MeshDraw.draw(Mesh.get("holy_lance"), m, 1f, 0, 0, 1f);
				}
			}
			case PowerProjectile.SWORD -> {
				Matrix4f m = MeshDraw.facing(c.subtract(dir.scale(0.55)), dir, 1f);
				if (add) {
					MeshDraw.drawGlow(Mesh.get("god_sword"), m, 0, 0, 1f);
					Draw.beam(c, c.subtract(dir.scale(2.2)), 0.6, 0.0, 0xFFC93C, 0.3f, 0);
					Draw.glow(c, 0.9, 0xFFE9A0, 0.4f);
				} else {
					MeshDraw.draw(Mesh.get("god_sword"), m, Math.max(bright, 0.7f), 0, 0, 1f);
				}
			}
			default -> {}
		}
	}
}
