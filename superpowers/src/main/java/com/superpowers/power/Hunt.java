package com.superpowers.power;

import com.superpowers.Superpowers;
import com.superpowers.net.Net;
import com.superpowers.power.powers.Power;
import com.superpowers.registry.ModSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Entity 666 server logic: fake mobs, the dark domain and the feast. */
public final class Hunt {
	private Hunt() {}

	public static final ResourceKey<DamageType> DEVOURED = ResourceKey.create(Registries.DAMAGE_TYPE, Superpowers.id("devoured"));
	public static final String FAKE_TAG = "sp_fake";

	/** Feast timing (ticks) shared with the client animation. */
	/**
	 * rise out of the ground, grab, lift to the face, RIP the head off (spine and all), throw the body down,
	 * chew on the head, toss what's left, end.
	 */
	public record Timeline(int rise, int grab, int liftEnd, int rip, int throwAt, int[] chew, int toss, int end) {}

	public static final Timeline CINEMATIC = new Timeline(30, 42, 60, 80, 88, new int[]{98, 108, 118}, 130, 150);
	public static final Timeline QUICK = new Timeline(0, 0, 10, 18, 23, new int[]{31, 39, 47}, 55, 64);

	record Fake(UUID owner, int expire) {}

	record Domain(UUID owner, ResourceKey<Level> dim, Vec3 c, double r, int end) {}

	private static final Map<UUID, Fake> FAKES = new HashMap<>();
	private static final List<Domain> DOMAINS = new ArrayList<>();
	private static final Set<UUID> FEASTING = new HashSet<>();

	static int now(Entity e) {
		return e.getServer() == null ? 0 : e.getServer().getTickCount();
	}

	public static boolean free(ServerLevel l, Entity e, Vec3 pos) {
		return l.noCollision(e, e.getBoundingBox().move(pos.subtract(e.position())));
	}

	public static void soundTo(ServerPlayer p, SoundEvent s, Vec3 at, float vol, float pitch) {
		p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(s), SoundSource.HOSTILE,
				at.x, at.y, at.z, vol, pitch, p.getRandom().nextLong()));
	}

	public static boolean isEntityForm(ServerPlayer p) {
		return PowerManager.get(p) == PowerType.ENTITY && PowerManager.isAwakened(p);
	}

	/** Bosses are too big to eat. */
	public static boolean canExecute(LivingEntity e) {
		return e.isAlive() && e.getMaxHealth() <= 150 && !FEASTING.contains(e.getUUID());
	}

	// ------------------------------------------------------------------ mimic

	private static final EntityType<?>[] FAKE_TYPES = {
			EntityType.ZOMBIE, EntityType.VILLAGER, EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.SKELETON, EntityType.CHICKEN};

	public static void spawnFakes(ServerPlayer p, int n) {
		ServerLevel l = p.serverLevel();
		Vec3 fwd = Abilities.horizontalLook(p);
		Vec3 right = fwd.cross(new Vec3(0, 1, 0)).normalize();
		int expire = now(p) + 1200;
		for (int i = 0; i < n; i++) {
			double along = 5 + p.getRandom().nextDouble() * 5;
			double side = (p.getRandom().nextDouble() - 0.5) * 9;
			Vec3 at = Abilities.ground(l, p.position().add(fwd.scale(along)).add(right.scale(side)).add(0, 3, 0), 12);
			EntityType<?> type = FAKE_TYPES[p.getRandom().nextInt(FAKE_TYPES.length)];
			Entity e = type.create(l);
			if (!(e instanceof Mob mob)) continue;
			mob.moveTo(at.x, at.y, at.z, p.getRandom().nextFloat() * 360f, 0);
			mob.finalizeSpawn(l, l.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
			mob.setPersistenceRequired();
			mob.addTag(FAKE_TAG);
			l.addFreshEntity(mob);
			FAKES.put(mob.getUUID(), new Fake(p.getUUID(), expire));
			Fx.send(l, Fx.ENTITY_MIMIC, at, p, 1f, 0);
		}
		Abilities.sound(l, p.position(), ModSounds.STATIC, 0.8f, 0.5f);
	}

	/** ALLOW_DEATH: fake mobs never really die. Whoever killed one meets the Entity face to face. */
	public static boolean allowDeath(LivingEntity e, DamageSource src, float amount) {
		Fake f = FAKES.remove(e.getUUID());
		if (f == null) return true;
		ServerLevel l = (ServerLevel) e.level();
		Fx.send(l, Fx.ENTITY_FAKE_DEATH, e.getBoundingBox().getCenter(), null, 1f, 0);
		Abilities.sound(l, e.position(), ModSounds.STATIC, 1f, 0.7f);
		e.discard();
		ServerPlayer owner = l.getServer().getPlayerList().getPlayer(f.owner);
		if (src.getEntity() instanceof ServerPlayer k && owner != null && k != owner && k.level() == owner.level()
				&& PowerManager.get(owner) == PowerType.ENTITY) {
			jumpscare(owner, k);
		}
		return false;
	}

	static void jumpscare(ServerPlayer owner, ServerPlayer k) {
		ServerLevel l = k.serverLevel();
		Vec3 look = Abilities.horizontalLook(k);
		Vec3 dest = k.position().add(look.scale(1.5));
		if (!free(l, owner, dest)) dest = k.position().add(look.scale(0.9));
		Vec3 to = k.position().subtract(dest);
		float yaw = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
		owner.teleportTo(l, dest.x, dest.y, dest.z, yaw, 10f);
		owner.resetFallDistance();
		Fx.send(l, Fx.ENTITY_JUMPSCARE, k.getEyePosition(), owner, 1f, k.getId());
		Abilities.sound(l, k.position(), ModSounds.SCREAM, 2.5f, 0.9f + k.getRandom().nextFloat() * 0.2f);
		k.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
		k.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
		PowerManager.State s = PowerManager.state(owner);
		s.cooldowns[0] = s.cooldowns[1] = 0;
		Awakening.add(owner, 5f);
		if (PowerManager.isAwakened(owner) && canExecute(k)) feast(owner, k, false);
	}

	// ------------------------------------------------------------------ the hollow

	public static void domain(ServerPlayer p, double r, int duration) {
		ServerLevel l = p.serverLevel();
		Vec3 c = p.position();
		DOMAINS.add(new Domain(p.getUUID(), l.dimension(), c, r, now(p) + duration));
		Fx.send(l, Fx.ENTITY_DOMAIN, c, p, (float) r, duration);
		Fx.shake(l, c, 0.5f, 20, r * 2);
		Abilities.sound(l, c, ModSounds.DRONE, 3f, 0.8f);
		Abilities.sound(l, c, ModSounds.WHISPER, 2f, 0.6f);
	}

	public static boolean inDomainOf(ServerPlayer owner, Entity e) {
		for (Domain d : DOMAINS) {
			if (d.owner.equals(owner.getUUID()) && d.dim == e.level().dimension() && e.position().distanceTo(d.c) <= d.r + 1) return true;
		}
		return false;
	}

	static void tickDomains(MinecraftServer server) {
		int now = server.getTickCount();
		for (Iterator<Domain> it = DOMAINS.iterator(); it.hasNext(); ) {
			Domain d = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(d.owner);
			if (owner == null || now >= d.end || owner.level().dimension() != d.dim) {
				it.remove();
				continue;
			}
			ServerLevel l = owner.serverLevel();
			if (owner.position().distanceTo(d.c) < d.r) {
				owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 1, false, false));
				owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, false, false));
			}
			for (LivingEntity e : Abilities.inRadius(l, owner, d.c, d.r + 4)) {
				Vec3 off = e.position().subtract(d.c);
				double dist = Math.sqrt(off.x * off.x + off.z * off.z);
				if (dist > d.r + 3.5) continue;
				// nobody leaves
				if (dist > d.r - 0.6) {
					Vec3 in = new Vec3(-off.x, 0, -off.z).normalize();
					if (dist > d.r + 1.5) {
						Vec3 edge = d.c.add(in.scale(-(d.r - 1.5)));
						e.teleportTo(edge.x, e.getY(), edge.z);
					}
					e.setDeltaMovement(in.scale(0.45).add(0, 0.1, 0));
					e.hurtMarked = true;
				}
				if (e instanceof ServerPlayer sp) {
					sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
					sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false));
					sp.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
					if ((now + sp.getId()) % 22 == 0) soundTo(sp, ModSounds.HEARTBEAT, sp.position(), 1f, 1f);
					if (sp.getRandom().nextInt(70) == 0) {
						Vec3 behind = sp.position().add(Abilities.horizontalLook(sp).scale(-3));
						soundTo(sp, ModSounds.WHISPER, behind, 0.9f, 0.7f + sp.getRandom().nextFloat() * 0.4f);
					}
				} else {
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false));
					e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
				}
			}
		}
	}

	// ------------------------------------------------------------------ the feast

	public static DamageSource devoured(ServerPlayer p) {
		return new DamageSource(p.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DEVOURED), p);
	}

	/**
	 * Grab {@code victim}, rip its head off and eat it. Cinematic = the V ultimate (it rises from the ground
	 * behind the victim, with cameras); quick = the awakened Entity grabbing someone in reach.
	 */
	public static void feast(ServerPlayer p, LivingEntity victim, boolean cinematic) {
		if (!canExecute(victim)) return;
		ServerLevel l = p.serverLevel();
		Timeline tl = cinematic ? CINEMATIC : QUICK;
		boolean form = isEntityForm(p);
		FEASTING.add(victim.getUUID());

		Vec3 base;
		if (cinematic) {
			Vec3 behind = victim.position().add(Abilities.horizontalLook(victim).scale(-2.3));
			base = Abilities.ground(l, behind.add(0, 2, 0), 8);
		} else {
			base = p.position();
		}
		Vec3 toVictim = victim.position().subtract(base);
		Vec3 facing = new Vec3(toVictim.x, 0, toVictim.z);
		facing = facing.lengthSqr() < 1e-4 ? Abilities.horizontalLook(p) : facing.normalize();
		final Vec3 face = facing;
		float yaw = (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG);
		if (form) p.teleportTo(l, base.x, base.y, base.z, yaw, 20f);

		Vec3 mouth = base.add(face.scale(0.62)).add(0, 2.45, 0);
		Vec3 hold = mouth.add(face.scale(0.3)).subtract(0, victim.getEyeHeight(), 0);
		Vec3 start = victim.position();

		Fx.send(l, Fx.ENTITY_FEAST, base, face, p, cinematic ? 1f : 0f, victim.getId());
		if (cinematic) {
			Power.cutsceneFor(p, PowerType.ENTITY, base);
			if (victim instanceof ServerPlayer vp) {
				ServerPlayNetworking.send(vp, new Net.Cutscene(PowerType.ENTITY.ordinal(), vp.getX(), vp.getY(), vp.getZ(),
						vp.getYRot(), base.x, base.y, base.z, 2));
			}
			Fx.tint(l, base, 0x200002, 0.55f, tl.end(), 48);
			Abilities.sound(l, base, ModSounds.DRONE, 3f, 0.6f);
			Abilities.sound(l, victim.position(), ModSounds.WHISPER, 2f, 0.8f);
		}
		if (victim instanceof Mob mob) mob.setNoAi(true);

		final boolean playerVictim = victim instanceof Player;
		final Vec3 throwVel = face.cross(new Vec3(0, 1, 0)).normalize().scale(victim.getRandom().nextBoolean() ? 0.34 : -0.34)
				.add(face.scale(0.1)).add(0, 0.2, 0);
		Tasks.repeat(p, 0, tl.end(), t -> {
			if (t < tl.rip() && victim.isAlive() && !victim.isRemoved()) {
				Vec3 pos;
				if (t < tl.grab()) {
					pos = start;
				} else if (t < tl.liftEnd()) {
					float k = (t - tl.grab()) / (float) Math.max(1, tl.liftEnd() - tl.grab());
					k = k * k * (3 - 2 * k);
					pos = start.lerp(hold, k);
				} else {
					pos = hold.add((victim.getRandom().nextDouble() - 0.5) * 0.06, 0, (victim.getRandom().nextDouble() - 0.5) * 0.06);
				}
				hold(victim, pos, face);
			}
			if (form) p.setDeltaMovement(Vec3.ZERO);

			if (cinematic && t == 2) Abilities.sound(l, base, ModSounds.CRUNCH, 2f, 0.4f);
			if (cinematic && t == 10) {
				Destruction.crater(l, p, base, 1.8, 0.7, 0f, 24);
				Fx.shake(l, base, 0.8f, 20, 30);
			}
			if (t == tl.grab()) {
				Abilities.sound(l, start, ModSounds.SLASH, 1.5f, 0.5f);
				Abilities.sound(l, start, playerVictim ? ModSounds.SCREAM : SoundEvents.PLAYER_HURT, 1.6f, 1.25f);
			}
			if (t == tl.rip() && victim.isAlive()) {
				// the head comes off, and the spine comes with it
				Vec3 head = victim.getEyePosition();
				Fx.send(l, Fx.ENTITY_GORE, head, p, 1f, victim.getId());
				Fx.shake(l, head, 1.1f, 16, 24);
				Abilities.sound(l, head, ModSounds.CRUNCH, 2.4f, 0.6f);
				Abilities.sound(l, head, SoundEvents.SLIME_SQUISH, 1.5f, 0.5f);
				Abilities.sound(l, head, SoundEvents.SKELETON_HURT, 1.2f, 0.5f);
				victim.invulnerableTime = 0;
				victim.hurt(devoured(p), 100000f);
				p.heal(20f);
				FEASTING.remove(victim.getUUID());
			}
			if (t == tl.throwAt()) {
				// and the body gets thrown to the ground like trash
				Fx.send(l, Fx.ENTITY_THROW, hold, throwVel, p, 1f, victim.getId());
				Abilities.sound(l, hold, ModSounds.WHOOSH, 1.2f, 0.6f);
			}
			for (int c : tl.chew()) {
				if (t == c) {
					Fx.send(l, Fx.ENTITY_BITE, mouth, p, 1f, 0);
					Abilities.sound(l, mouth, ModSounds.CRUNCH, 1.6f, 0.65f + p.getRandom().nextFloat() * 0.3f);
				}
			}
			if (t == tl.toss()) {
				Fx.send(l, Fx.ENTITY_TOSS, mouth, face, p, 1f, victim.getId());
				if (playerVictim) dropHead(l, victim, mouth, face);
			}
		});
		// failsafe: release if something interrupted the feast
		Tasks.later(p, tl.end() + 2, () -> {
			FEASTING.remove(victim.getUUID());
			if (victim.isAlive() && victim instanceof Mob mob) mob.setNoAi(false);
		});
	}

	static void hold(LivingEntity victim, Vec3 pos, Vec3 face) {
		float yaw = (float) (Mth.atan2(face.x, -face.z) * Mth.RAD_TO_DEG);
		if (victim instanceof ServerPlayer sp) {
			sp.teleportTo(sp.serverLevel(), pos.x, pos.y, pos.z, yaw, -10f);
		} else {
			victim.setPos(pos.x, pos.y, pos.z);
			victim.setYRot(yaw);
			victim.setYHeadRot(yaw);
			victim.yBodyRot = yaw;
		}
		victim.setDeltaMovement(Vec3.ZERO);
		victim.hurtMarked = true;
		victim.resetFallDistance();
	}

	static void dropHead(ServerLevel l, LivingEntity victim, Vec3 at, Vec3 face) {
		ItemStack head = ItemStack.EMPTY;
		if (victim instanceof ServerPlayer sp) {
			head = new ItemStack(Items.PLAYER_HEAD);
			head.set(DataComponents.PROFILE, new ResolvableProfile(sp.getGameProfile()));
		} else if (victim.getType() == EntityType.ZOMBIE || victim.getType() == EntityType.HUSK) {
			head = new ItemStack(Items.ZOMBIE_HEAD);
		} else if (victim.getType() == EntityType.SKELETON || victim.getType() == EntityType.STRAY) {
			head = new ItemStack(Items.SKELETON_SKULL);
		} else if (victim.getType() == EntityType.CREEPER) {
			head = new ItemStack(Items.CREEPER_HEAD);
		} else if (victim.getType() == EntityType.PIGLIN) {
			head = new ItemStack(Items.PIGLIN_HEAD);
		} else if (victim.getType() == EntityType.WITHER_SKELETON) {
			head = new ItemStack(Items.WITHER_SKELETON_SKULL);
		}
		if (head.isEmpty()) return;
		ItemEntity it = new ItemEntity(l, at.x, at.y, at.z, head);
		it.setDeltaMovement(face.scale(-0.15).add((l.random.nextDouble() - 0.5) * 0.2, 0.25, (l.random.nextDouble() - 0.5) * 0.2));
		it.setPickUpDelay(30);
		l.addFreshEntity(it);
	}

	/** V with nothing in reach: it rises where you look, screams, and sinks back. */
	public static void whiff(ServerPlayer p, Vec3 at) {
		ServerLevel l = p.serverLevel();
		Vec3 to = p.position().subtract(at);
		Vec3 face = new Vec3(to.x, 0, to.z).lengthSqr() < 1e-4 ? Abilities.horizontalLook(p) : new Vec3(to.x, 0, to.z).normalize();
		Fx.send(l, Fx.ENTITY_FEAST, at, face, p, 1f, -1);
		Power.cutsceneFor(p, PowerType.ENTITY, at);
		Fx.tint(l, at, 0x200002, 0.5f, CINEMATIC.end(), 48);
		Abilities.sound(l, at, ModSounds.DRONE, 3f, 0.6f);
		Tasks.later(p, 10, () -> Destruction.crater(l, p, at, 1.8, 0.7, 0f, 24));
		Tasks.later(p, 45, () -> {
			Abilities.sound(l, at, ModSounds.SCREAM, 4f, 0.6f);
			Fx.shake(l, at, 1.2f, 30, 40);
			for (LivingEntity e : Abilities.inRadius(l, p, at.add(0, 2, 0), 8)) {
				e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
				Abilities.hurt(p, e, Power.dmgFor(p, 8f));
			}
		});
	}

	// ------------------------------------------------------------------ bookkeeping

	public static void tick(MinecraftServer server) {
		tickDomains(server);
		int now = server.getTickCount();
		if (now % 20 != 0 || FAKES.isEmpty()) return;
		for (Iterator<Map.Entry<UUID, Fake>> it = FAKES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Fake> en = it.next();
			Entity e = null;
			for (ServerLevel l : server.getAllLevels()) {
				e = l.getEntity(en.getKey());
				if (e != null) break;
			}
			if (e == null) {
				it.remove();
				continue;
			}
			if (now >= en.getValue().expire) {
				Fx.send((ServerLevel) e.level(), Fx.ENTITY_FAKE_DEATH, e.getBoundingBox().getCenter(), null, 1f, 0);
				e.discard();
				it.remove();
			}
		}
	}

	/** Fake mobs left over from a previous session are removed when they load. */
	public static void onLoad(Entity e) {
		if (e.getTags().contains(FAKE_TAG) && !FAKES.containsKey(e.getUUID())) e.discard();
	}

	public static void clear() {
		FAKES.clear();
		DOMAINS.clear();
		FEASTING.clear();
	}
}
