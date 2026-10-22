package com.superpowers.power.powers;

import com.superpowers.power.Abilities;
import com.superpowers.power.Fx;
import com.superpowers.power.Hunt;
import com.superpowers.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/** Entity 666: a horror that hunts. Claws, blinks behind you, plants fake mobs, traps you in the dark and eats you. */
public class EntityPower extends Power {
	@Override
	public void m1(ServerPlayer p, int combo) {
		ServerLevel l = level(p);
		boolean fin = combo == 3;
		boolean aw = awake(p);
		// wide horizontal swipes: right hand sweeps right-to-left, left hand left-to-right, finisher = both arms
		double range = (fin ? 5.0 : 4.4) * (aw ? 1.6 : 1);
		List<LivingEntity> hits = Abilities.inCone(p, range, -0.2);
		Vec3 sweepDir = right(p).scale(combo % 2 == 0 ? -1 : 1);
		for (LivingEntity e : hits) {
			Abilities.hurt(p, e, dmg(p, fin ? 10f : 5.5f));
			if (fin) {
				Abilities.knock(e, p.position(), -1.1 * (aw ? 1.8 : 1), 0.5); // hooks them in
			} else {
				e.setDeltaMovement(e.getDeltaMovement().scale(0.3).add(sweepDir.scale(aw ? 1.0 : 0.55)).add(0, 0.15, 0));
				e.hurtMarked = true;
			}
			Fx.send(l, Fx.ENTITY_BITE, e.getBoundingBox().getCenter(), p, 0.6f, 0);
		}
		Vec3 look = p.getLookAngle();
		Fx.send(l, Fx.ENTITY_CLAW, p.getEyePosition().add(look.scale(1.5)).add(0, -0.25, 0), look, p, hits.isEmpty() ? 0f : 1f, combo);
		Abilities.anim(p, fin ? Fx.Anim.SWEEP_BOTH : combo % 2 == 0 ? Fx.Anim.SWEEP_R : Fx.Anim.SWEEP_L, fin ? 11 : 8);
		Abilities.sound(l, p.position(), ModSounds.SLASH, 0.9f, 0.55f + combo * 0.05f);
		if (!hits.isEmpty()) Abilities.sound(l, p.position(), ModSounds.CRUNCH, 0.5f, 1.3f);
		// awakened: the last hit grabs and eats whatever it caught
		if (combo == 3 && awake(p)) {
			hits.stream().filter(Hunt::canExecute).min(Comparator.comparingDouble(e -> e.distanceToSqr(p)))
					.ifPresent(e -> Hunt.feast(p, e, false));
		}
	}

	@Override
	public void z(ServerPlayer p) {
		ServerLevel l = level(p);
		LivingEntity target = Abilities.aimEntity(p, awake(p) ? 48 : 30);
		Vec3 from = p.position();
		Vec3 dest;
		if (target != null) {
			Vec3 behind = target.position().add(Abilities.horizontalLook(target).scale(-1.7));
			dest = Hunt.free(l, p, behind) ? behind : target.position().add(Abilities.horizontalLook(target).scale(1.7));
		} else {
			Vec3 hit = Abilities.aimBlock(p, 18);
			dest = hit.subtract(p.getLookAngle().scale(1.0));
			dest = Abilities.ground(l, dest.add(0, 1, 0), 6);
		}
		Fx.send(l, Fx.ENTITY_FLICKER, from, dest.subtract(from), p, 1f, 0);
		Abilities.sound(l, from, ModSounds.STATIC, 1.2f, 0.8f);
		if (target != null) {
			Vec3 to = target.position().subtract(dest);
			float yaw = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
			p.teleportTo(l, dest.x, dest.y, dest.z, yaw, 15f);
			Abilities.hurt(p, target, dmg(p, 4f));
			target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0));
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
			if (target instanceof ServerPlayer tp) Hunt.soundTo(tp, ModSounds.WHISPER, tp.position(), 1f, 0.9f);
		} else {
			p.teleportTo(l, dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
		}
		p.resetFallDistance();
		Abilities.sound(l, dest, ModSounds.STATIC, 1.2f, 0.6f);
		Abilities.anim(p, Fx.Anim.CLAW, 8);
	}

	@Override
	public void x(ServerPlayer p) {
		Hunt.spawnFakes(p, cnt(p, 3));
		Abilities.anim(p, Fx.Anim.CAST_FORWARD, 14);
		Abilities.sound(level(p), p.position(), ModSounds.WHISPER, 1.2f, 0.7f);
	}

	@Override
	public void c(ServerPlayer p) {
		Hunt.domain(p, rad(p, 14), awake(p) ? 400 : 240);
		Abilities.anim(p, Fx.Anim.SPREAD, 20);
	}

	@Override
	public void v(ServerPlayer p) {
		ServerLevel l = level(p);
		LivingEntity target = Abilities.aimEntity(p, 40);
		if (target == null) {
			Vec3 eye = p.getEyePosition();
			Vec3 look = p.getLookAngle();
			target = Abilities.inRadius(l, p, p.position(), 24).stream()
					.filter(e -> e.getBoundingBox().getCenter().subtract(eye).normalize().dot(look) > 0.5)
					.min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
		}
		if (target != null) {
			Hunt.feast(p, target, true);
		} else {
			// nothing to eat: it rises, screams at nothing, and sinks back
			Hunt.whiff(p, ultTarget(p, 30, 10, 4));
		}
	}
}
