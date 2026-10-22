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

import java.util.List;

public class SpacePower extends Power {
	@Override
	public void m1(ServerPlayer p, int combo) {
		ServerLevel l = level(p);
		List<LivingEntity> hits = melee(p, combo, 5f, 10f, 0.7, 1.9, Fx.SPACE_PUNCH);
		if (combo == 3) {
			// implode: drag nearby enemies into the strike point first
			Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(3));
			for (LivingEntity e : Abilities.inRadius(l, p, at, rad(p, 6))) {
				if (hits.contains(e)) continue;
				Vec3 pull = at.subtract(e.position()).normalize().scale(0.9);
				e.setDeltaMovement(pull.add(0, 0.25, 0));
				e.hurtMarked = true;
			}
			Abilities.sound(l, p.position(), ModSounds.TELEPORT, 1f, 0.7f);
		}
		Abilities.sound(l, p.position(), SoundEvents.SHULKER_SHOOT, 0.5f, 1.6f + combo * 0.1f);
		if (!hits.isEmpty()) Abilities.sound(l, p.position(), ModSounds.IMPACT, 0.9f, 0.8f);
	}

	@Override
	public void z(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double range = awake(p) ? 80 : 40;
		Vec3 hit = Abilities.aimBlock(p, range);
		double dist = Math.max(0, hit.distanceTo(eye) - 1.0);
		Vec3 from = p.position();
		Vec3 dest = null;
		// walk back along the ray until the player fits
		for (double d = dist; d > 0 && dest == null; d -= 0.5) {
			Vec3 feet = eye.add(look.scale(d)).subtract(0, p.getEyeHeight(), 0);
			for (int up = 0; up <= 2; up++) {
				Vec3 f = feet.add(0, up, 0);
				if (l.noCollision(p, p.getBoundingBox().move(f.subtract(from)))) {
					dest = f;
					break;
				}
			}
		}
		if (dest == null) return;
		Fx.send(l, Fx.SPACE_WARP, from.add(0, 1, 0), dest.subtract(from), p, 1f, 0);
		p.teleportTo(dest.x, dest.y, dest.z);
		p.resetFallDistance();
		Abilities.launch(p, Vec3.ZERO);
		Abilities.anim(p, Fx.Anim.CAST_FORWARD, 6);
		Abilities.sound(l, from, ModSounds.TELEPORT, 1.2f, 1f);
		Abilities.sound(l, dest, ModSounds.TELEPORT, 1.2f, 1.2f);
		Abilities.sound(l, dest, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.6f);
		Abilities.blast(l, p, dest.add(0, 1, 0), rad(p, 3.5), dmg(p, 4f), 0, 1.0);
		if (awake(p)) {
			Fx.send(l, Fx.SPACE_SLAM, from, p, 5f, 0);
			Destruction.crater(l, p, from, 3.5, 0.6, 0f, 40);
		}
	}

	@Override
	public void x(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 aim = Abilities.aimPoint(p, 26);
		if (aim.distanceTo(p.position()) > 25) aim = p.position().add(Abilities.horizontalLook(p).scale(10));
		Vec3 center = Abilities.ground(l, aim.add(0, 1, 0), 20);
		final double radius = rad(p, 10);
		Fx.send(l, Fx.SPACE_WELL, center, p, (float) radius, 44);
		Abilities.anim(p, Fx.Anim.CAST_UP, 30);
		Abilities.sound(l, center, ModSounds.BLACKHOLE, 2f, 1.5f);
		Tasks.repeat(p, 0, 34, i -> {
			Vec3 lift = center.add(0, 4.5, 0);
			double t = (p.level().getGameTime() + i) * 0.15;
			int k = 0;
			for (LivingEntity e : Abilities.inRadius(l, p, center.add(0, 2, 0), radius)) {
				double a = t + k++ * 1.3;
				Vec3 spot = lift.add(Math.cos(a) * 2.2, Math.sin(a * 0.7) * 0.5, Math.sin(a) * 2.2);
				e.setDeltaMovement(spot.subtract(e.position()).scale(0.2));
				e.hurtMarked = true;
				e.resetFallDistance();
			}
		});
		Tasks.later(p, 34, () -> {
			for (LivingEntity e : Abilities.inRadius(l, p, center.add(0, 4, 0), radius + 2)) {
				e.setDeltaMovement(0, -3.2, 0);
				e.hurtMarked = true;
			}
			Abilities.anim(p, Fx.Anim.SLAM, 10);
		});
		Tasks.later(p, 37, () -> {
			Abilities.blast(l, p, center, rad(p, 7), dmg(p, 12f), 0, 0.6);
			Destruction.crater(l, p, center, rad(p, 5), 0.35, 0f, 50);
			Fx.send(l, Fx.SPACE_SLAM, center, p, (float) rad(p, 9), 0);
			Fx.shake(l, center, 0.8f, 15, 30);
			Abilities.sound(l, center, ModSounds.BOOM, 2f, 1.3f);
			Abilities.sound(l, center, SoundEvents.ANVIL_LAND, 1f, 0.5f);
		});
	}

	@Override
	public void c(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 target = ultTarget(p, 60, 20, 6);
		final double spread = rad(p, 6.5);
		final int every = awake(p) ? 1 : 3;
		Fx.send(l, Fx.SPACE_METEOR_MARK, target, p, (float) rad(p, 7), 50);
		Abilities.anim(p, Fx.Anim.CAST_UP, 14);
		Abilities.sound(l, p.position(), SoundEvents.BEACON_ACTIVATE, 1f, 0.6f);
		Abilities.sound(l, p.position(), ModSounds.CHARGE, 1f, 1.2f);
		Vec3 back = Abilities.horizontalLook(p).scale(-10);
		Tasks.repeat(p, 6, 36, i -> {
			if (i % every != 0) return;
			double a = rand(p) * Math.PI * 2, r = Math.sqrt(rand(p)) * spread;
			Vec3 land = Abilities.ground(l, target.add(Math.cos(a) * r, 6, Math.sin(a) * r), 20);
			Vec3 start = land.add(back).add((rand(p) - 0.5) * 8, 34, (rand(p) - 0.5) * 8);
			PowerProjectile m = PowerProjectile.create(l, p, PowerProjectile.METEOR, start, 1.9f, 60).shoot(land.subtract(start));
			l.addFreshEntity(m);
			Abilities.sound(l, land, ModSounds.WHOOSH, 2f, 0.5f);
		});
	}

	@Override
	public void v(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 target = ultTarget(p, 32, 18, 12).add(0, 5, 0);
		final double r = rad(p, 22);
		cutscene(p, PowerType.SPACE, target);
		Fx.tint(l, target, 0x14002A, 0.8f, 140, 120);
		Abilities.sound(l, p.position(), ModSounds.CHARGE, 2f, 0.5f);
		Tasks.later(p, 10, () -> {
			Fx.send(l, Fx.SPACE_BLACKHOLE, target, p, awake(p) ? 7f : 4.5f, 85);
			Abilities.sound(l, target, ModSounds.BLACKHOLE, 5f, 0.7f);
		});
		Tasks.later(p, 55, () -> Abilities.sound(l, target, ModSounds.BLACKHOLE, 5f, 0.55f));
		// the black hole devours the ground beneath it
		Tasks.repeat(p, 40, 50, i -> {
			if (i % 10 == 0) Destruction.crater(l, p, target.add(0, -5, 0), 2.5 + i * 0.12 * (awake(p) ? 2 : 1), 0.7, 0f, 25);
		});
		Tasks.repeat(p, 15, 80, i -> {
			for (LivingEntity e : Abilities.inRadius(l, p, target, r * 1.35)) {
				Vec3 to = target.subtract(e.getBoundingBox().getCenter());
				double d = to.length();
				Vec3 swirl = to.normalize().cross(UP).scale(0.35);
				Vec3 pull = to.normalize().scale(Math.min(1.2, 0.25 + i * 0.012)).add(swirl);
				if (d < 2) pull = to.scale(0.3);
				e.setDeltaMovement(pull);
				e.hurtMarked = true;
				e.resetFallDistance();
				if (i % 10 == 0) Abilities.hurt(p, e, dmg(p, 2f));
			}
		});
		Tasks.later(p, 95, () -> {
			Fx.send(l, Fx.SPACE_COLLAPSE, target, p, 1f, 0);
			Abilities.sound(l, target, ModSounds.TELEPORT, 4f, 0.4f);
			Abilities.sound(l, target, ModSounds.CHARGE, 4f, 1.4f);
		});
		Tasks.later(p, 105, () -> {
			Abilities.blast(l, p, target, r, dmg(p, 60f), 0, 3.5);
			Destruction.crater(l, p, target.add(0, -4, 0), r * 0.65, 0.6, 0f, 120);
			Fx.send(l, Fx.SPACE_BIGBANG, target, p, (float) r, 0);
			Fx.flash(l, target, 0xFFFFFF, 1f, 30, 160);
			Fx.shake(l, target, 2f, 45, 160);
			Abilities.sound(l, target, ModSounds.BOOM, 10f, 0.5f);
			Abilities.sound(l, target, ModSounds.THUNDER, 6f, 0.6f);
			Abilities.sound(l, target, SoundEvents.GENERIC_EXPLODE, 6f, 0.4f);
		});
	}
}
