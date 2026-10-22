package com.superpowers.power.powers;

import com.superpowers.net.Net;
import com.superpowers.power.Abilities;
import com.superpowers.power.Destruction;
import com.superpowers.power.Fx;
import com.superpowers.power.PowerManager;
import com.superpowers.power.PowerType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** One power's five abilities (server side). */
public abstract class Power {
	public abstract void m1(ServerPlayer p, int combo);

	public abstract void z(ServerPlayer p);

	public abstract void x(ServerPlayer p);

	public abstract void c(ServerPlayer p);

	public abstract void v(ServerPlayer p);

	protected static final Vec3 UP = new Vec3(0, 1, 0);

	// ------------------------------------------------------------------ awakening scaling ("10x crazier")

	protected static boolean awake(ServerPlayer p) {
		return PowerManager.isAwakened(p);
	}

	/** Damage: x10 while awakened. */
	protected static float dmg(ServerPlayer p, float base) {
		return awake(p) ? base * 10f : base;
	}

	/** Area / crater size: x2 while awakened. */
	protected static double rad(ServerPlayer p, double base) {
		return awake(p) ? base * 2.0 : base;
	}

	/** Projectile / pillar counts: x3 while awakened. */
	protected static int cnt(ServerPlayer p, int base) {
		return awake(p) ? base * 3 : base;
	}

	// ------------------------------------------------------------------ shared helpers

	/** Shared melee combo: cone hit + knockback + effect + animation. The 4th hit (combo 3) is the finisher. */
	protected static List<LivingEntity> melee(ServerPlayer p, int combo, float dmg, float finDmg, double kb, double finKb, int fx) {
		boolean fin = combo == 3;
		boolean aw = awake(p);
		double range = (fin ? 4.6 : 3.8) * (aw ? 1.6 : 1);
		List<LivingEntity> hits = Abilities.inCone(p, range, fin ? 0.3 : 0.5);
		for (LivingEntity e : hits) {
			Abilities.hurt(p, e, dmg(p, fin ? finDmg : dmg));
			Abilities.knock(e, p.position(), (fin ? finKb : kb) * (aw ? 1.8 : 1), fin ? 0.5 : 0.12);
		}
		Vec3 look = p.getLookAngle();
		Vec3 at = p.getEyePosition().add(look.scale(1.9));
		Fx.send(p.serverLevel(), fx, at, look, p, hits.isEmpty() ? 0f : 1f, combo);
		int anim = fin ? Fx.Anim.UPPERCUT : (combo % 2 == 0 ? Fx.Anim.PUNCH_R : Fx.Anim.PUNCH_L);
		Abilities.anim(p, anim, fin ? 9 : 6);
		p.swing(InteractionHand.MAIN_HAND);
		if (fin) {
			Fx.shake(p.serverLevel(), at, aw ? 0.6f : 0.25f, 6, 12);
			// finishers crack whatever block you're punching
			Vec3 eye = p.getEyePosition();
			BlockHitResult bh = p.level().clip(new ClipContext(eye, eye.add(look.scale(aw ? 7 : 4.5)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (bh.getType() == HitResult.Type.BLOCK) {
				Destruction.crater(p.serverLevel(), p, bh.getLocation(), aw ? 3.2 : 1.6, 0.8, 0f, aw ? 30 : 10);
			}
		} else if (aw) {
			Vec3 eye = p.getEyePosition();
			BlockHitResult bh = p.level().clip(new ClipContext(eye, eye.add(look.scale(5)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (bh.getType() == HitResult.Type.BLOCK) Destruction.crater(p.serverLevel(), p, bh.getLocation(), 1.4, 0.9, 0f, 6);
		}
		return hits;
	}

	/** Starts the caster's cinematic camera and the ultimate pose. */
	protected static void cutscene(ServerPlayer p, PowerType type, Vec3 target) {
		ServerPlayNetworking.send(p, new Net.Cutscene(type.ordinal(), p.getX(), p.getY(), p.getZ(), p.getYRot(), target.x, target.y, target.z, 0));
		Abilities.anim(p, Fx.Anim.SPREAD, type.ultDuration - 15);
	}

	public static void cutsceneFor(ServerPlayer p, PowerType type, Vec3 target) {
		cutscene(p, type, target);
	}

	public static float dmgFor(ServerPlayer p, float base) {
		return dmg(p, base);
	}

	protected static Vec3 right(ServerPlayer p) {
		Vec3 r = Abilities.horizontalLook(p).cross(UP);
		return r.normalize();
	}

	/** Roughly where the right hand is. */
	protected static Vec3 hand(ServerPlayer p) {
		return p.getEyePosition().add(p.getLookAngle().scale(0.8)).add(right(p).scale(0.35)).add(0, -0.25, 0);
	}

	protected static ServerLevel level(ServerPlayer p) {
		return p.serverLevel();
	}

	/** Ultimate target: what you look at up to {@code range}, else a point {@code fallback} blocks ahead; snapped to ground. */
	protected static Vec3 ultTarget(ServerPlayer p, double range, double fallback, double minDist) {
		Vec3 eye = p.getEyePosition();
		Vec3 pt = Abilities.aimPoint(p, range);
		double d = pt.distanceTo(eye);
		if (d >= range - 0.5) pt = eye.add(Abilities.horizontalLook(p).scale(fallback));
		else if (d < minDist) pt = eye.add(Abilities.horizontalLook(p).scale(minDist));
		return Abilities.ground(level(p), pt.add(0, 2, 0), 48);
	}

	protected static double rand(ServerPlayer p) {
		return p.getRandom().nextDouble();
	}
}
