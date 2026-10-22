package com.superpowers.entity;

import com.superpowers.power.Abilities;
import com.superpowers.power.Destruction;
import com.superpowers.power.Fx;
import com.superpowers.power.PowerManager;
import com.superpowers.registry.ModSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Fireballs, meteors, holy lances and golden swords. Rendered by the client FX pass. */
public class PowerProjectile extends Projectile {
	public static final int FIREBALL = 0;
	public static final int METEOR = 1;
	public static final int LANCE = 2;
	public static final int SWORD = 3;

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(PowerProjectile.class, EntityDataSerializers.INT);

	private int life;
	private int maxLife = 80;
	private int hover;
	private float speed = 1.5f;
	private LivingEntity homingTarget;
	private Vec3 aimPoint;

	public PowerProjectile(EntityType<? extends PowerProjectile> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, FIREBALL);
	}

	public int kind() {
		return entityData.get(KIND);
	}

	public int life() {
		return life;
	}

	public static PowerProjectile create(ServerLevel level, ServerPlayer owner, int kind, Vec3 pos, float speed, int maxLife) {
		PowerProjectile p = new PowerProjectile(com.superpowers.registry.ModEntities.PROJECTILE, level);
		p.entityData.set(KIND, kind);
		p.setOwner(owner);
		p.setPos(pos.x, pos.y, pos.z);
		p.speed = speed;
		p.maxLife = maxLife;
		return p;
	}

	/** Fly straight along {@code dir}. */
	public PowerProjectile shoot(Vec3 dir) {
		setDeltaMovement(dir.normalize().scale(speed));
		face(dir);
		return this;
	}

	/** Hover for {@code ticks}, then fly at the target (homing if it's an entity). */
	public PowerProjectile hoverThenSeek(int ticks, LivingEntity target, Vec3 point) {
		this.hover = ticks;
		this.homingTarget = target;
		this.aimPoint = point;
		setDeltaMovement(Vec3.ZERO);
		face(seekPoint().subtract(position()));
		return this;
	}

	private Vec3 seekPoint() {
		if (homingTarget != null && homingTarget.isAlive()) return homingTarget.getBoundingBox().getCenter();
		return aimPoint != null ? aimPoint : position().add(getViewVector(1f).scale(10));
	}

	private void face(Vec3 dir) {
		if (dir.lengthSqr() < 1e-6) return;
		double h = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
		setYRot((float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG));
		setXRot((float) (Mth.atan2(-dir.y, h) * Mth.RAD_TO_DEG));
		yRotO = getYRot();
		xRotO = getXRot();
	}

	@Override
	public void tick() {
		super.tick();
		life++;
		if (!level().isClientSide) {
			if (life > maxLife) {
				impact(position(), null);
				return;
			}
			if (hover > 0) {
				hover--;
				setDeltaMovement(Vec3.ZERO);
				face(seekPoint().subtract(position()));
				if (hover == 0) {
					Vec3 dir = seekPoint().subtract(position());
					setDeltaMovement(dir.normalize().scale(speed));
					level().playSound(null, getX(), getY(), getZ(), ModSounds.WHOOSH, getSoundSource(), 0.8f, 1.6f);
				}
			} else if (homingTarget != null && homingTarget.isAlive()) {
				Vec3 desired = seekPoint().subtract(position()).normalize().scale(speed);
				Vec3 v = getDeltaMovement().lerp(desired, 0.3);
				setDeltaMovement(v.normalize().scale(speed));
			}
			if (hover <= 0) {
				HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
				if (hit.getType() != HitResult.Type.MISS) {
					impact(hit.getLocation(), hit instanceof EntityHitResult ehr ? ehr.getEntity() : null);
					return;
				}
			}
		}
		Vec3 v = getDeltaMovement();
		setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
		if (v.lengthSqr() > 1e-4) face(v);
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		if (e == getOwner() || e instanceof PowerProjectile || !e.isAlive()) return false;
		return super.canHitEntity(e) && e instanceof LivingEntity && Abilities.isTarget(getOwner(), e);
	}

	private void impact(Vec3 pos, Entity direct) {
		if (!(level() instanceof ServerLevel level)) return;
		ServerPlayer owner = getOwner() instanceof ServerPlayer sp ? sp : null;
		boolean aw = owner != null && PowerManager.isAwakened(owner);
		float dm = aw ? 10f : 1f;
		double rm = aw ? 2.0 : 1.0;
		switch (kind()) {
			case FIREBALL -> {
				Abilities.blast(level, owner, pos, 4.5 * rm, 10f * dm, 5, 1.1);
				Destruction.crater(level, owner, pos, 3.0 * rm, 0.75, 0.5f, aw ? 50 : 25);
				Fx.send(level, Fx.FIRE_EXPLOSION, pos, this, (float) (4.5 * rm), 0);
				Fx.shake(level, pos, 0.6f, 12, 24);
				Abilities.sound(level, pos, ModSounds.BOOM, 2.5f, 1.15f);
				Abilities.sound(level, pos, ModSounds.FIRE, 1.5f, 0.9f);
			}
			case METEOR -> {
				Abilities.blast(level, owner, pos, 4.0 * rm, 12f * dm, 3, 1.2);
				Destruction.crater(level, owner, pos, 2.6 * rm, 0.7, 0.25f, aw ? 30 : 18);
				Fx.send(level, Fx.SPACE_METEOR_IMPACT, pos, this, (float) (4 * rm), 0);
				Fx.shake(level, pos, 0.5f, 10, 30);
				Abilities.sound(level, pos, ModSounds.BOOM, 2.5f, 1.3f);
			}
			case LANCE -> {
				if (direct instanceof LivingEntity le && owner != null) Abilities.hurt(owner, le, 8f * dm);
				Abilities.blast(level, owner, pos, 1.8 * rm, 4f * dm, 0, 0.4);
				Destruction.crater(level, owner, pos, 1.3 * rm, 1.0, 0f, 6);
				Fx.send(level, Fx.ANGEL_LANCE_HIT, pos, this, 1f, 0);
				Abilities.sound(level, pos, ModSounds.HOLY, 1f, 1.6f);
			}
			case SWORD -> {
				if (direct instanceof LivingEntity le && owner != null) Abilities.hurt(owner, le, 9f * dm);
				Abilities.blast(level, owner, pos, 2.2 * rm, 5f * dm, 0, 0.6);
				Destruction.crater(level, owner, pos, 1.5 * rm, 1.0, 0f, 8);
				Fx.send(level, Fx.GOD_SWORD_HIT, pos, this, 1f, 0);
				Abilities.sound(level, pos, ModSounds.IMPACT, 1.4f, 1.2f);
				level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.TRIDENT_HIT, getSoundSource(), 1f, 0.8f);
			}
			default -> {}
		}
		discard();
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256 * 256;
	}
}
