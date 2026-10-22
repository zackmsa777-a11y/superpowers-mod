package com.superpowers.power.powers;

import com.superpowers.power.Abilities;
import com.superpowers.power.Destruction;
import com.superpowers.power.Fx;
import com.superpowers.power.PowerType;
import com.superpowers.power.Tasks;
import com.superpowers.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DemonPower extends Power {
	@Override
	public void m1(ServerPlayer p, int combo) {
		ServerLevel l = level(p);
		List<LivingEntity> hits = melee(p, combo, 5f, 10f, 0.4, 1.4, Fx.DEMON_CLAW);
		for (LivingEntity e : hits) {
			p.heal(combo == 3 ? 2.5f : 1f);
			Fx.send(l, Fx.DEMON_DRAIN, e.getBoundingBox().getCenter(), p, 1f, 0);
		}
		Abilities.anim(p, Fx.Anim.CLAW, combo == 3 ? 9 : 6);
		Abilities.sound(l, p.position(), ModSounds.SLASH, 0.9f, 0.75f + combo * 0.05f);
		if (!hits.isEmpty()) Abilities.sound(l, p.position(), SoundEvents.PHANTOM_BITE, 0.6f, 0.7f);
		if (combo == 3) Abilities.sound(l, p.position(), ModSounds.DEMON, 0.7f, 1.6f);
	}

	@Override
	public void z(ServerPlayer p) {
		ServerLevel l = level(p);
		LivingEntity target = Abilities.aimEntity(p, awake(p) ? 48 : 28);
		Vec3 eye = p.getEyePosition();
		Abilities.anim(p, Fx.Anim.CAST_FORWARD, 12);
		Abilities.sound(l, p.position(), SoundEvents.CHAIN_PLACE, 1.5f, 0.6f);
		Abilities.sound(l, p.position(), ModSounds.WHOOSH, 1f, 1.5f);
		if (target != null) {
			Fx.send(l, Fx.DEMON_CHAIN, eye, target.getBoundingBox().getCenter().subtract(eye), p, target.getId(), 14);
			Abilities.hurt(p, target, dmg(p, 5f));
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
			Tasks.repeat(p, 2, 10, i -> {
				if (!target.isAlive()) return;
				Vec3 dest = p.position().add(Abilities.horizontalLook(p).scale(1.6));
				Vec3 v = dest.subtract(target.position()).scale(0.28).add(0, 0.12, 0);
				target.setDeltaMovement(v);
				target.hurtMarked = true;
				if (i == 9) Abilities.sound(l, p.position(), ModSounds.DEMON, 0.6f, 1.8f);
			});
		} else {
			Vec3 hit = Abilities.aimBlock(p, 28);
			if (hit.distanceTo(eye) > 27.5) {
				Fx.send(l, Fx.DEMON_CHAIN, eye, hit.subtract(eye), p, -1, 6);
				return;
			}
			Fx.send(l, Fx.DEMON_CHAIN, eye, hit.subtract(eye), p, -1, 14);
			Tasks.repeat(p, 2, 8, i -> {
				Vec3 to = hit.subtract(p.position().add(0, 1, 0));
				if (to.length() < 2) return;
				Abilities.launch(p, to.normalize().scale(1.6).add(0, 0.12, 0));
			});
		}
	}

	@Override
	public void x(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 look = p.getLookAngle();
		final Vec3 d = new Vec3(look.x, Math.max(-0.2, Math.min(0.3, look.y)), look.z).normalize();
		final int ticks = awake(p) ? 11 : 7;
		final double hitR = rad(p, 2.6);
		Set<LivingEntity> hit = new HashSet<>();
		Fx.send(l, Fx.DEMON_DASH, p.position(), d, p, 1f, ticks + 5);
		Abilities.anim(p, Fx.Anim.DASH, ticks + 2);
		Abilities.sound(l, p.position(), ModSounds.WHOOSH, 1.2f, 0.7f);
		Abilities.sound(l, p.position(), ModSounds.DEMON, 0.5f, 1.8f);
		Tasks.repeat(p, 0, ticks, i -> {
			Abilities.launch(p, i < ticks - 1 ? d.scale(1.8) : d.scale(0.3));
			if (awake(p) && i % 2 == 0) Destruction.crater(l, p, p.position().add(0, -0.5, 0), 1.6, 0.6, 0f, 6);
			for (LivingEntity e : Abilities.inRadius(l, p, p.position().add(0, 1, 0), hitR)) {
				if (hit.add(e)) {
					Abilities.hurt(p, e, dmg(p, 7f));
					e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
					p.heal(2f);
					Fx.send(l, Fx.DEMON_CLAW, e.getBoundingBox().getCenter(), d, p, 1f, 3);
					Fx.send(l, Fx.DEMON_DRAIN, e.getBoundingBox().getCenter(), p, 1f, 0);
					Abilities.sound(l, e.position(), ModSounds.SLASH, 1f, 0.7f);
				}
			}
		});
	}

	@Override
	public void c(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 fwd = Abilities.horizontalLook(p);
		Vec3 start = p.position();
		Abilities.anim(p, Fx.Anim.SLAM, 12);
		Abilities.sound(l, start, ModSounds.DEMON, 1f, 1.3f);
		double[] angles = awake(p) ? new double[]{-0.45, 0, 0.45} : new double[]{0};
		final double spikeR = awake(p) ? 2.0 : 1.2;
		for (double ang : angles) {
			Vec3 dir = new Vec3(fwd.x * Math.cos(ang) - fwd.z * Math.sin(ang), 0, fwd.x * Math.sin(ang) + fwd.z * Math.cos(ang));
			for (int k = 0; k < 12; k++) {
				final int idx = k;
				Tasks.later(p, 3 + k * 2, () -> {
					Vec3 pos = Abilities.ground(l, start.add(dir.scale(2.2 + idx * 1.6)).add(0, 3, 0), 8);
					float h = (2.4f + idx * 0.12f) * (awake(p) ? 1.6f : 1f);
					Fx.send(l, Fx.DEMON_SPIKE, pos, p, h, 0);
					Destruction.crater(l, p, pos.add(0, -0.4, 0), spikeR, 0.9, 0f, 5);
					for (LivingEntity e : Abilities.inRadius(l, p, pos.add(0, 1, 0), spikeR + 0.8)) {
						Abilities.hurt(p, e, dmg(p, 8f));
						e.setDeltaMovement(e.getDeltaMovement().x * 0.3, 1.0, e.getDeltaMovement().z * 0.3);
						e.hurtMarked = true;
					}
					Abilities.sound(l, pos, SoundEvents.EVOKER_FANGS_ATTACK, 1f, 0.7f + idx * 0.03f);
					if (idx % 3 == 0) Fx.shake(l, pos, 0.25f, 5, 16);
				});
			}
		}
	}

	@Override
	public void v(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 center = p.position();
		final double radius = rad(p, 18);
		cutscene(p, PowerType.DEMON, center);
		Fx.send(l, Fx.DEMON_FORM, p.position(), p, 1f, 140);
		Fx.tint(l, center, 0x7A0008, 0.7f, 120, 120);
		Abilities.sound(l, center, ModSounds.DEMON, 3f, 0.75f);
		Tasks.later(p, 28, () -> {
			Fx.send(l, Fx.DEMON_PENTAGRAM, center, p, (float) radius, 95);
			Abilities.sound(l, center, SoundEvents.WITHER_SPAWN, 2f, 0.8f);
		});
		Tasks.later(p, 55, () -> Abilities.sound(l, center, ModSounds.DEMON, 4f, 0.6f));
		Tasks.later(p, 80, () -> {
			List<LivingEntity> hits = Abilities.blast(l, p, center, radius, dmg(p, 40f), 6, 1.5);
			for (LivingEntity e : hits) {
				e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
				e.setDeltaMovement(e.getDeltaMovement().add(0, 1.2, 0));
				e.hurtMarked = true;
			}
			// the ground around the caster is torn open (the caster stands on a pillar of what's left)
			Vec3 ground = Abilities.ground(l, center.add(0, 1, 0), 12);
			for (int k = 0; k < (awake(p) ? 18 : 9); k++) {
				double a = k * Math.PI * 2 / (awake(p) ? 18 : 9) + rand(p) * 0.3, r = radius * (0.35 + rand(p) * 0.5);
				Destruction.crater(l, p, Abilities.ground(l, ground.add(Math.cos(a) * r, 6, Math.sin(a) * r), 14), 2.5 + rand(p) * 2.5, 0.8, 0.3f, 12);
			}
			Fx.send(l, Fx.DEMON_ERUPTION, center, p, (float) radius, 0);
			Fx.flash(l, center, 0xE0101C, 0.85f, 25, 150);
			Fx.shake(l, center, 1.8f, 40, 140);
			Abilities.sound(l, center, ModSounds.BOOM, 8f, 0.55f);
			Abilities.sound(l, center, ModSounds.DEMON, 6f, 0.55f);
			Abilities.sound(l, center, SoundEvents.WITHER_SHOOT, 3f, 0.5f);
		});
		Tasks.repeat(p, 82, 30, i -> {
			if (i % (awake(p) ? 1 : 3) != 0) return;
			double a = rand(p) * Math.PI * 2, r = 3 + rand(p) * (radius - 3);
			Vec3 g = Abilities.ground(l, center.add(Math.cos(a) * r, 5, Math.sin(a) * r), 14);
			Fx.send(l, Fx.DEMON_SPIKE, g, p, awake(p) ? 6f : 3.5f, 0);
		});
	}
}
