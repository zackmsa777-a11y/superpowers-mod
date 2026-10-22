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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GodPower extends Power {
	/** Mobs currently frozen by Time Stop (restored when it ends or the server stops). */
	private static final Set<Mob> FROZEN = new HashSet<>();

	@Override
	public void m1(ServerPlayer p, int combo) {
		ServerLevel l = level(p);
		List<LivingEntity> hits = melee(p, combo, 6f, 11f, 0.6, 2.2, Fx.GOD_PALM);
		if (combo == 3) {
			Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(3));
			for (LivingEntity e : Abilities.inRadius(l, p, at, rad(p, 5))) {
				if (!hits.contains(e)) {
					Abilities.hurt(p, e, dmg(p, 6f));
					Abilities.knock(e, at, 1.4, 0.5);
				}
			}
			Abilities.sound(l, p.position(), ModSounds.HOLY, 1f, 1.1f);
			Abilities.sound(l, p.position(), ModSounds.THUNDER, 0.5f, 1.8f);
		}
		Abilities.sound(l, p.position(), ModSounds.IMPACT, 0.9f, 1.2f + combo * 0.06f);
		Abilities.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.5f);
	}

	private void smiteAt(ServerPlayer p, Vec3 pt) {
		ServerLevel l = level(p);
		Fx.send(l, Fx.GOD_SMITE, pt, p, (float) rad(p, 3.5), 0);
		Abilities.sound(l, pt, ModSounds.THUNDER, 3f, 1.2f);
		Abilities.sound(l, pt, SoundEvents.LIGHTNING_BOLT_IMPACT, 2f, 1.3f);
		Tasks.later(p, 2, () -> {
			Abilities.blast(l, p, pt.add(0, 1, 0), rad(p, 3.8), dmg(p, 13f), 0, 0.8);
			Destruction.crater(l, p, pt, rad(p, 2.4), 0.9, 0.15f, 20);
			Fx.shake(l, pt, 0.5f, 10, 30);
		});
	}

	@Override
	public void z(ServerPlayer p) {
		ServerLevel l = level(p);
		LivingEntity e = Abilities.aimEntity(p, 50);
		Vec3 pt = e != null ? e.position() : Abilities.ground(l, Abilities.aimBlock(p, 50).add(0, 0.5, 0), 30);
		smiteAt(p, pt);
		if (awake(p)) {
			int extra = 0;
			for (LivingEntity o : Abilities.inRadius(l, p, pt, 16)) {
				if (o == e || extra >= 2) continue;
				extra++;
				Vec3 op = o.position();
				Tasks.later(p, 3 + extra * 3, () -> smiteAt(p, op));
			}
			for (; extra < 2; extra++) {
				Vec3 rp = Abilities.ground(l, pt.add((rand(p) - 0.5) * 12, 6, (rand(p) - 0.5) * 12), 16);
				Tasks.later(p, 3 + extra * 3, () -> smiteAt(p, rp));
			}
		}
		Abilities.anim(p, Fx.Anim.CAST_UP, 9);
	}

	@Override
	public void x(ServerPlayer p) {
		ServerLevel l = level(p);
		Vec3 center = p.position();
		final double r = rad(p, 28);
		final int dur = awake(p) ? 200 : 100;
		Fx.send(l, Fx.GOD_TIMESTOP, center, p, (float) r, dur);
		Abilities.anim(p, Fx.Anim.SPREAD, 14);
		Abilities.sound(l, center, ModSounds.TIMESTOP, 3f, 1f);
		List<Mob> frozen = new ArrayList<>();
		for (LivingEntity e : Abilities.inRadius(l, p, center, r)) {
			if (e instanceof Mob m && !m.isNoAi()) {
				m.setNoAi(true);
				FROZEN.add(m);
				frozen.add(m);
			}
		}
		Tasks.repeat(p, 0, dur, i -> {
			for (Mob m : frozen) {
				m.setDeltaMovement(Vec3.ZERO);
				m.hurtMarked = true;
			}
		});
		Tasks.later(p, dur, () -> {
			for (Mob m : frozen) {
				if (FROZEN.remove(m)) m.setNoAi(false);
			}
			Abilities.sound(l, p.position(), ModSounds.TIMESTOP, 2f, 1.8f);
		});
	}

	public static void unfreezeAll() {
		for (Mob m : FROZEN) m.setNoAi(false);
		FROZEN.clear();
	}

	@Override
	public void c(ServerPlayer p) {
		ServerLevel l = level(p);
		LivingEntity target = Abilities.aimEntity(p, 45);
		Vec3 point = Abilities.aimPoint(p, 45);
		Vec3 fwd = Abilities.horizontalLook(p);
		Vec3 right = right(p);
		int n = cnt(p, 8);
		for (int i = 0; i < n; i++) {
			int row = i / 8;
			double a = Math.PI * (i % 8) / 7.0;
			double ring = 2.6 + row * 1.4;
			Vec3 pos = p.getEyePosition().add(right.scale(Math.cos(a) * ring)).add(0, 0.9 + Math.sin(a) * (1.7 + row * 1.1), 0).add(fwd.scale(-1.0 - row * 0.6));
			Vec3 aim = point.add((rand(p) - 0.5) * (2.5 + row * 3), rand(p), (rand(p) - 0.5) * (2.5 + row * 3));
			PowerProjectile sword = PowerProjectile.create(l, p, PowerProjectile.SWORD, pos, 2.4f, 90).hoverThenSeek(10 + i * (awake(p) ? 1 : 3), target, aim);
			l.addFreshEntity(sword);
			Fx.send(l, Fx.GOD_SWORD_SUMMON, pos, p, 1f, 0);
		}
		Abilities.anim(p, Fx.Anim.CAST_UP, 16);
		Abilities.sound(l, p.position(), ModSounds.HOLY, 1.5f, 0.8f);
		Abilities.sound(l, p.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.6f);
	}

	@Override
	public void v(ServerPlayer p) {
		ServerLevel l = level(p);
		cutscene(p, PowerType.GOD, p.position());
		Fx.send(l, Fx.GOD_RINGS, p.position(), p, 1f, 130);
		Fx.tint(l, p.position(), 0xFFD27A, 0.45f, 130, 120);
		Abilities.sound(l, p.position(), ModSounds.ULT, 2f, 1.1f);
		Abilities.sound(l, p.position(), ModSounds.CHARGE, 2f, 0.5f);
		Tasks.later(p, 30, () -> Abilities.sound(l, p.position(), ModSounds.CHOIR, 4f, 0.7f));
		Tasks.later(p, 60, () -> Abilities.sound(l, p.position(), ModSounds.CHARGE, 3f, 0.9f));
		Tasks.later(p, 85, () -> {
			Vec3 center = p.position();
			double r = rad(p, 14);
			Abilities.blast(l, p, center, r, dmg(p, 40f), 0, 2.5);
			Vec3 ground = Abilities.ground(l, center.add(0, 1, 0), 12);
			Destruction.crater(l, p, ground.add(0, -1, 0), r * 0.8, 0.5, 0f, 120);
			Fx.send(l, Fx.GOD_WRATH, center, p, (float) rad(p, 30), 0);
			Fx.flash(l, center, 0xFFF2C0, 1f, 30, 160);
			Fx.shake(l, center, 1.8f, 45, 160);
			Abilities.sound(l, center, ModSounds.BOOM, 8f, 0.7f);
			Abilities.sound(l, center, ModSounds.HOLY, 6f, 0.6f);
			List<LivingEntity> judged = new ArrayList<>();
			for (LivingEntity e : Abilities.inRadius(l, p, center, rad(p, 48))) {
				if (e instanceof Enemy || (e instanceof Mob m && m.getTarget() == p)) judged.add(e);
			}
			for (int k = 0; k < judged.size(); k++) {
				LivingEntity e = judged.get(k);
				Tasks.later(p, k / 2, () -> {
					if (!e.isAlive()) return;
					Fx.send(l, Fx.GOD_LIGHTNING, e.position(), p, 1f, 0);
					Abilities.hurt(p, e, dmg(p, 50f));
					Destruction.crater(l, p, e.position(), 1.8, 1.0, 0.2f, 6);
					Abilities.sound(l, e.position(), ModSounds.THUNDER, 2f, 1f + (float) rand(p) * 0.4f);
				});
			}
			for (int k = 0; k < cnt(p, 14); k++) {
				Tasks.later(p, 2 + k * 2, () -> {
					double a = rand(p) * Math.PI * 2, rr = 6 + rand(p) * rad(p, 22);
					Vec3 g = Abilities.ground(l, center.add(Math.cos(a) * rr, 10, Math.sin(a) * rr), 30);
					Fx.send(l, Fx.GOD_LIGHTNING, g, p, 1f, 0);
					Destruction.crater(l, p, g, 2.2, 1.0, 0.15f, 8);
				});
			}
		});
	}
}
