package com.superpowers.power;

import com.superpowers.net.Net;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Shared server-side helpers for targeting, damage, knockback and sounds. */
public final class Abilities {
	private Abilities() {}

	/** Can {@code caster}'s abilities hurt {@code e}? */
	public static boolean isTarget(Entity caster, Entity e) {
		if (e == caster || !(e instanceof LivingEntity le) || !le.isAlive()) return false;
		if (e instanceof ArmorStand) return false;
		if (e.isSpectator()) return false;
		if (e instanceof Player p && p.isCreative()) return false;
		if (caster != null && e instanceof OwnableEntity own && caster.getUUID().equals(own.getOwnerUUID())) return false;
		return true;
	}

	public static List<LivingEntity> inRadius(ServerLevel level, Entity caster, Vec3 c, double r) {
		AABB box = new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> isTarget(caster, e))) {
			if (e.getBoundingBox().getCenter().distanceToSqr(c) <= r * r) out.add(e);
		}
		return out;
	}

	/** Entities in front of the player within {@code range} and a cone of {@code cos} (dot product threshold). */
	public static List<LivingEntity> inCone(ServerPlayer p, double range, double cos) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : inRadius(p.serverLevel(), p, eye, range + 1)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d > range + e.getBbWidth()) continue;
			if (d < 1.2 || to.normalize().dot(look) >= cos) out.add(e);
		}
		return out;
	}

	public static DamageSource source(ServerPlayer p) {
		return p == null ? null : p.damageSources().playerAttack(p);
	}

	public static void hurt(ServerPlayer p, LivingEntity target, float dmg) {
		target.invulnerableTime = 0;
		if (p != null) {
			target.hurt(p.damageSources().playerAttack(p), dmg);
		} else {
			target.hurt(target.damageSources().magic(), dmg);
		}
	}

	/** Sets an entity on fire and remembers who did it (burn kills fill the Fire awakening meter). */
	public static void ignite(ServerPlayer p, Entity e, int seconds) {
		e.igniteForSeconds(seconds);
		if (p != null) Awakening.markIgnited(e, p);
	}

	public static void knock(Entity target, Vec3 from, double strength, double up) {
		Vec3 d = target.position().subtract(from);
		d = new Vec3(d.x, 0, d.z);
		if (d.lengthSqr() < 1e-4) d = new Vec3(0.01, 0, 0);
		d = d.normalize().scale(strength);
		target.setDeltaMovement(target.getDeltaMovement().scale(0.3).add(d.x, up, d.z));
		target.hurtMarked = true;
		target.hasImpulse = true;
	}

	/** Damages + knocks back everything in a sphere. */
	public static List<LivingEntity> blast(ServerLevel level, ServerPlayer p, Vec3 c, double r, float dmg, int fireSecs, double kb) {
		List<LivingEntity> hit = inRadius(level, p, c, r);
		for (LivingEntity e : hit) {
			double falloff = 1.0 - 0.5 * (e.position().distanceTo(c) / r);
			hurt(p, e, (float) (dmg * Math.max(0.5, falloff)));
			if (fireSecs > 0) ignite(p, e, fireSecs);
			if (kb > 0) knock(e, c, kb * falloff, 0.35 + kb * 0.25);
		}
		return hit;
	}

	/** What the player is looking at: entity, block, or the max-range point. */
	public static Vec3 aimPoint(ServerPlayer p, double range) {
		LivingEntity e = aimEntity(p, range);
		if (e != null) return e.position();
		return aimBlock(p, range);
	}

	public static Vec3 aimBlock(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
	}

	public static LivingEntity aimEntity(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (bh.getType() != HitResult.Type.MISS) end = bh.getLocation();
		AABB box = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.5);
		EntityHitResult eh = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box, e -> isTarget(p, e), 1.0f);
		if (eh != null && eh.getEntity() instanceof LivingEntity le) return le;
		// generous aim assist: closest target near the look ray
		LivingEntity best = null;
		double bestScore = 0.96;
		for (LivingEntity le : inRadius(p.serverLevel(), p, eye, range)) {
			Vec3 to = le.getBoundingBox().getCenter().subtract(eye);
			double dot = to.normalize().dot(look);
			if (dot > bestScore && p.hasLineOfSight(le)) {
				bestScore = dot;
				best = le;
			}
		}
		return best;
	}

	/** Finds the ground below a point (top of the first solid block), or the point itself. */
	public static Vec3 ground(ServerLevel level, Vec3 pos, int maxDown) {
		BlockPos.MutableBlockPos m = BlockPos.containing(pos).mutable();
		for (int i = 0; i < maxDown; i++) {
			if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
				return new Vec3(pos.x, m.getY() + 1.0, pos.z);
			}
			m.move(0, -1, 0);
		}
		return pos;
	}

	public static void sound(ServerLevel level, Vec3 pos, SoundEvent s, float vol, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	public static void sound(ServerLevel level, Vec3 pos, Holder<SoundEvent> s, float vol, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	public static void anim(ServerPlayer p, int anim, int duration) {
		Net.Anim payload = new Net.Anim(p.getId(), anim, duration);
		ServerPlayNetworking.send(p, payload);
		for (ServerPlayer other : PlayerLookup.tracking(p)) {
			if (other != p) ServerPlayNetworking.send(other, payload);
		}
	}

	/** Push the player (client-authoritative movement needs the motion packet). */
	public static void launch(ServerPlayer p, Vec3 velocity) {
		p.setDeltaMovement(velocity);
		p.hurtMarked = true;
		p.hasImpulse = true;
		p.resetFallDistance();
	}

	public static Vec3 horizontalLook(Entity e) {
		Vec3 l = e.getLookAngle();
		Vec3 h = new Vec3(l.x, 0, l.z);
		return h.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : h.normalize();
	}
}
