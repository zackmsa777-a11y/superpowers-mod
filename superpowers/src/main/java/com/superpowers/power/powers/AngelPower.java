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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AngelPower extends Power {
	@Override
	public void m1(ServerPlayer p, int combo) {
		ServerLevel l = level(p);
		List<LivingEntity> hits = melee(p, combo, 5f, 10f, 0.4, 1.3, Fx.ANGEL_SLASH);
		for (LivingEntity e : hits) {
			if (e.isInvertedHealAndHarm()) Abilities.hurt(p, e, dmg(p, 3f)); // holy damage vs undead
		}
		Abilities.sound(l, p.position(), ModSounds.SLASH, 0.9f, 1.3f + combo * 0.1f);
		if (combo == 3) Abilities.sound(l, p.position(), ModSounds.HOLY, 1f, 1.3f);
	}

	@Override
	public void z(ServerPlayer p) {
		ServerLevel l = level(p);
		Fx.send(l, Fx.ANGEL_WINGS, p.position(), p, 1f, 200);
		Abilities.launch(p, p.getLookAngle().scale(0.6).add(0, awake(p) ? 2.0 : 1.3, 0));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1, false, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, awake(p) ? 3 : 1, false, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.JUMP, 200, 2, false, false, true));
		for (LivingEntity e : Abilities.inRadius(l, p, p.position(), rad(p, 4.5))) {
			Abilities.hurt(p, e, dmg(p, 3f));
			Abilities.knock(e, p.position(), 1.4, 0.4);
		}
		Abilities.anim(p, Fx.Anim.SPREAD, 16);
		Abilities.sound(l, p.position(), ModSounds.CHOIR, 1.2f, 1.2f);
		Abilities.sound(l, p.position(), SoundEvents.ENDER_DRAGON_FLAP, 1.5f, 1.4f);
	}

	@Override
	public void x(ServerPlayer p) {
		ServerLevel l = level(p);
		LivingEntity target = Abilities.aimEntity(p, 40);
		Vec3 point = Abilities.aimPoint(p, 40);
		Vec3 fwd = Abilities.horizontalLook(p);
		Vec3 right = right(p);
		int n = cnt(p, 6);
		for (int i = 0; i < n; i++) {
			int row = i / 6;
			double s = (i % 6) - 2.5;
			Vec3 pos = p.getEyePosition().add(right.scale(s * (0.85 + row * 0.35))).add(0, 1.0 + row * 0.9 + (2.5 - Math.abs(s)) * 0.3, 0).add(fwd.scale(-0.6 - row * 0.4));
			Vec3 aim = point.add((rand(p) - 0.5) * (3 + row * 3), rand(p) * 1.5, (rand(p) - 0.5) * (3 + row * 3));
			PowerProjectile lance = PowerProjectile.create(l, p, PowerProjectile.LANCE, pos, 2.2f, 80).hoverThenSeek(8 + i * (awake(p) ? 1 : 3), target, aim);
			l.addFreshEntity(lance);
			Fx.send(l, Fx.ANGEL_LANCE_SUMMON, pos, p, 1f, 0);
		}
		Abilities.anim(p, Fx.Anim.CAST_UP, 12);
		Abilities.sound(l, p.position(), ModSounds.HOLY, 1.2f, 1f);
		Abilities.sound(l, p.position(), SoundEvents.BEACON_POWER_SELECT, 1f, 1.8f);
	}

	@Override
	public void c(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 center = p.position();
		final double r = rad(p, 8);
		Fx.send(l, Fx.ANGEL_SANCTUARY, center, p, (float) r, 120);
		Abilities.anim(p, Fx.Anim.CAST_UP, 16);
		Abilities.sound(l, center, ModSounds.CHOIR, 1.5f, 1f);
		Abilities.sound(l, center, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.4f);
		Tasks.repeat(p, 0, 120, i -> {
			if (i % 10 != 0) return;
			if (p.position().distanceTo(center) < r + 1) p.heal(awake(p) ? 6f : 2f);
			for (LivingEntity e : Abilities.inRadius(l, p, center.add(0, 1, 0), r)) {
				if (e instanceof Enemy || e.isInvertedHealAndHarm()) {
					Abilities.hurt(p, e, dmg(p, e.isInvertedHealAndHarm() ? 6f : 4f));
					Abilities.knock(e, center, 0.35, 0.2);
					Fx.send(l, Fx.ANGEL_LIGHT_PILLAR, e.position(), p, 6f, 0);
				}
			}
			if (i % 20 == 0) Abilities.sound(l, center, ModSounds.HOLY, 0.7f, 1.4f + (float) rand(p) * 0.3f);
		});
	}

	@Override
	public void v(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 target = ultTarget(p, 55, 24, 10);
		final double r = rad(p, 9);
		cutscene(p, PowerType.ANGEL, target);
		Fx.send(l, Fx.ANGEL_WINGS, p.position(), p, 1.4f, 140);
		Fx.tint(l, p.position(), 0xE8B860, 0.3f, 120, 120);
		Abilities.sound(l, p.position(), ModSounds.CHOIR, 3f, 0.9f);
		Tasks.later(p, 5, () -> Fx.send(l, Fx.ANGEL_HALO_SKY, target.add(0, 28, 0), p, (float) r, 110));
		Tasks.later(p, 30, () -> Abilities.sound(l, target, ModSounds.CHOIR, 5f, 0.7f));
		Tasks.later(p, 72, () -> {
			Fx.send(l, Fx.ANGEL_JUDGMENT, target, p, (float) r, 45);
			Abilities.sound(l, target, ModSounds.BEAM, 6f, 0.7f);
			Abilities.sound(l, target, ModSounds.HOLY, 6f, 0.8f);
			Abilities.sound(l, target, ModSounds.THUNDER, 5f, 1.2f);
		});
		Tasks.later(p, 75, () -> {
			Fx.flash(l, target, 0xFFFBE8, 0.95f, 22, 160);
			Fx.shake(l, target, 1.5f, 35, 140);
			Abilities.sound(l, target, ModSounds.BOOM, 8f, 0.8f);
			Destruction.shaft(l, target, r * 0.8, (int) (r * 0.9), 8, 120);
		});
		Tasks.repeat(p, 75, 40, i -> {
			if (i % 5 != 0) return;
			for (LivingEntity e : Abilities.inRadius(l, p, target.add(0, 2, 0), r + 2)) {
				Abilities.hurt(p, e, dmg(p, e.isInvertedHealAndHarm() ? 12f : 9f));
				if (i == 0) Abilities.knock(e, target, 1.6, 0.8);
				e.igniteForSeconds(2);
			}
		});
		for (int k = 0; k < cnt(p, 8); k++) {
			Tasks.later(p, 80 + k * 3, () -> {
				double a = rand(p) * Math.PI * 2, rr = r * 1.3 + rand(p) * 5;
				Vec3 g = Abilities.ground(l, target.add(Math.cos(a) * rr, 8, Math.sin(a) * rr), 20);
				Fx.send(l, Fx.ANGEL_LIGHT_PILLAR, g, p, 18f, 0);
				Destruction.crater(l, p, g, 1.8, 1.2, 0f, 8);
			});
		}
	}
}
