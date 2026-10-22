package com.superpowers.power.powers;

import com.superpowers.entity.PowerProjectile;
import com.superpowers.power.Abilities;
import com.superpowers.power.Destruction;
import com.superpowers.power.Fx;
import com.superpowers.power.PowerType;
import com.superpowers.power.Tasks;
import com.superpowers.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FirePower extends Power {
	@Override
	public void m1(ServerPlayer p, int combo) {
		List<LivingEntity> hits = melee(p, combo, 5f, 9f, 0.5, 1.5, Fx.FIRE_PUNCH);
		for (LivingEntity e : hits) Abilities.ignite(p, e, combo == 3 ? 6 : 3);
		ServerLevel l = level(p);
		Abilities.sound(l, p.position(), SoundEvents.BLAZE_SHOOT, 0.5f, 1.5f + (float) rand(p) * 0.3f);
		if (!hits.isEmpty()) Abilities.sound(l, p.position(), ModSounds.IMPACT, 0.9f, 1.0f + combo * 0.08f);
		if (combo == 3) {
			Abilities.sound(l, p.position(), ModSounds.FIRE, 1.2f, 1.2f);
			Destruction.scorch(l, p, p.getEyePosition().add(p.getLookAngle().scale(3)), awake(p) ? 4 : 2, 0.5f);
		}
	}

	@Override
	public void z(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 start = hand(p);
		Vec3 look = p.getLookAngle();
		int n = awake(p) ? 3 : 1;
		for (int i = 0; i < n; i++) {
			Vec3 dir = n == 1 ? look : look.add(right(p).scale((i - 1) * 0.18)).normalize();
			PowerProjectile orb = PowerProjectile.create(l, p, PowerProjectile.FIREBALL, start, awake(p) ? 2.0f : 1.5f, 60).shoot(dir);
			l.addFreshEntity(orb);
		}
		Fx.send(l, Fx.FIRE_ORB_CAST, start, look, p, 1f, 0);
		Abilities.anim(p, Fx.Anim.CAST_FORWARD, 10);
		Abilities.sound(l, start, ModSounds.FIRE, 1.2f, 1.3f);
		Abilities.sound(l, start, SoundEvents.BLAZE_SHOOT, 1f, 0.7f);
	}

	@Override
	public void x(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 dir = p.getLookAngle();
		dir = new Vec3(dir.x, Math.max(-0.2, Math.min(0.35, dir.y)), dir.z).normalize();
		final Vec3 d = dir;
		final int ticks = awake(p) ? 11 : 7;
		final double hitR = rad(p, 2.6);
		Set<LivingEntity> hit = new HashSet<>();
		Fx.send(l, Fx.FIRE_DASH, p.position(), d, p, 1f, ticks + 5);
		Abilities.anim(p, Fx.Anim.DASH, ticks + 2);
		Abilities.sound(l, p.position(), ModSounds.WHOOSH, 1.2f, 0.9f);
		Abilities.sound(l, p.position(), ModSounds.FIRE, 1f, 1.4f);
		Tasks.repeat(p, 0, ticks, i -> {
			Abilities.launch(p, i < ticks - 1 ? d.scale(1.75) : d.scale(0.3));
			if (i % 2 == 0) Destruction.scorch(l, p, p.position(), 1.5, 0.6f);
			for (LivingEntity e : Abilities.inRadius(l, p, p.position().add(0, 1, 0), hitR)) {
				if (hit.add(e)) {
					Abilities.hurt(p, e, dmg(p, 7f));
					Abilities.ignite(p, e, 5);
					Abilities.knock(e, p.position(), 0.8, 0.6);
					Fx.send(l, Fx.FIRE_EXPLOSION, e.position().add(0, 1, 0), p, 1.5f, 0);
				}
			}
			if (i == ticks - 1) {
				Fx.send(l, Fx.FIRE_EXPLOSION, p.position().add(0, 1, 0), p, (float) rad(p, 2.5), 0);
				Abilities.sound(l, p.position(), ModSounds.BOOM, 0.8f, 1.6f);
				if (awake(p)) Destruction.crater(l, p, p.position(), 3, 0.6, 0.6f, 30);
			}
		});
	}

	@Override
	public void c(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 center = p.position();
		int waves = awake(p) ? 7 : 4;
		Fx.send(l, Fx.FIRE_RING_CAST, center, p, (float) (2.5 + waves * 2.3), 30);
		Abilities.anim(p, Fx.Anim.SLAM, 12);
		Abilities.sound(l, center, ModSounds.CHARGE, 1f, 1.6f);
		for (int k = 0; k < waves; k++) {
			final int wave = k;
			Tasks.later(p, 6 + k * 5, () -> {
				double r = 2.5 + wave * 2.3;
				int n = 6 + wave * 3;
				double off = rand(p) * Math.PI;
				for (int j = 0; j < n; j++) {
					double a = off + j * Math.PI * 2 / n;
					Vec3 g = Abilities.ground(l, center.add(Math.cos(a) * r, 3, Math.sin(a) * r), 10);
					Fx.send(l, Fx.FIRE_PILLAR, g, p, 4f + wave, 0);
					if (j % 2 == 0) Destruction.crater(l, p, g.add(0, -0.5, 0), awake(p) ? 2.0 : 1.1, 0.8, 0.5f, 3);
					for (LivingEntity e : Abilities.inRadius(l, p, g.add(0, 1, 0), 2.2)) {
						Abilities.hurt(p, e, dmg(p, 7f));
						Abilities.ignite(p, e, 5);
						e.setDeltaMovement(e.getDeltaMovement().add(0, 0.9, 0));
						e.hurtMarked = true;
					}
				}
				Fx.shake(l, center, 0.35f, 6, 20);
				Abilities.sound(l, center, ModSounds.FIRE, 1.6f, 0.8f + wave * 0.1f);
				Abilities.sound(l, center, SoundEvents.BLAZE_SHOOT, 1f, 0.6f);
			});
		}
	}

	@Override
	public void v(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 target = ultTarget(p, 60, 24, 10);
		final double r = rad(p, 14);
		cutscene(p, PowerType.FIRE, target);
		Fx.send(l, Fx.FIRE_SUN_CHARGE, p.position(), p, 1f, 65);
		Fx.tint(l, p.position(), awake(p) ? 0x2A6CFF : 0xFF5A10, 0.45f, 110, 80);
		Abilities.sound(l, p.position(), ModSounds.CHARGE, 2f, 0.6f);
		Tasks.repeat(p, 0, 65, i -> {
			if (i % 16 == 0) Abilities.sound(l, p.position(), ModSounds.FIRE, 2f, 0.6f + i * 0.01f);
			if (i == 30) Abilities.sound(l, p.position(), ModSounds.CHARGE, 2f, 0.9f);
		});
		Tasks.later(p, 65, () -> {
			Vec3 sun = p.position().add(0, 4.3, 0);
			Fx.send(l, Fx.FIRE_SUN_THROW, sun, target.subtract(sun), p, 1f, 15);
			Abilities.sound(l, sun, ModSounds.WHOOSH, 3f, 0.5f);
			Abilities.sound(l, sun, SoundEvents.BLAZE_SHOOT, 2f, 0.5f);
		});
		Tasks.later(p, 80, () -> {
			Abilities.blast(l, p, target, r, dmg(p, 45f), 8, 3.0);
			Destruction.crater(l, p, target, r * 0.75, 0.55, 0.7f, 120);
			Fx.send(l, Fx.FIRE_SUPERNOVA, target, p, (float) r, 0);
			Fx.shake(l, target, 1.6f, 40, 120);
			Fx.flash(l, target, awake(p) ? 0xC8E8FF : 0xFFC070, 0.9f, 25, 150);
			Abilities.sound(l, target, ModSounds.BOOM, 8f, 0.6f);
			Abilities.sound(l, target, ModSounds.FIRE, 6f, 0.6f);
			Abilities.sound(l, target, SoundEvents.GENERIC_EXPLODE, 6f, 0.5f);
		});
		for (int k = 0; k < 3; k++) {
			final int ring = k;
			Tasks.later(p, 84 + k * 4, () -> {
				int n = 8 + ring * 4;
				double rr = r * (0.45 + ring * 0.3);
				for (int j = 0; j < n; j++) {
					double a = j * Math.PI * 2 / n + ring * 0.4;
					Vec3 g = Abilities.ground(l, target.add(Math.cos(a) * rr, 6, Math.sin(a) * rr), 16);
					Fx.send(l, Fx.FIRE_PILLAR, g, p, 7f - ring, 0);
				}
				Destruction.scorch(l, p, target, rr, 0.5f);
			});
		}
	}
}
