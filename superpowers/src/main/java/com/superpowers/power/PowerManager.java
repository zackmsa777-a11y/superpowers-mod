package com.superpowers.power;

import com.mojang.serialization.Codec;
import com.superpowers.Superpowers;
import com.superpowers.net.Net;
import com.superpowers.power.powers.AngelPower;
import com.superpowers.power.powers.DemonPower;
import com.superpowers.power.powers.FirePower;
import com.superpowers.power.powers.GodPower;
import com.superpowers.power.powers.Power;
import com.superpowers.power.powers.SpacePower;
import com.superpowers.registry.ModItems;
import com.superpowers.registry.ModSounds;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side owner of every player's power, mana, cooldowns, cutscene and awakening state. */
public final class PowerManager {
	private PowerManager() {}

	public static final AttachmentType<String> POWER = AttachmentRegistry.<String>builder()
			.persistent(Codec.STRING)
			.copyOnDeath()
			.buildAndRegister(Superpowers.id("power"));

	private static final Map<PowerType, Power> POWERS = new EnumMap<>(PowerType.class);
	private static final Map<UUID, State> STATES = new HashMap<>();

	static {
		POWERS.put(PowerType.FIRE, new FirePower());
		POWERS.put(PowerType.SPACE, new SpacePower());
		POWERS.put(PowerType.ANGEL, new AngelPower());
		POWERS.put(PowerType.DEMON, new DemonPower());
		POWERS.put(PowerType.GOD, new GodPower());
		POWERS.put(PowerType.ENTITY, new com.superpowers.power.powers.EntityPower());
	}

	public static final class State {
		public float mana = PowerType.MAX_MANA;
		public final int[] cooldowns = new int[5];
		public int combo;
		public int lastM1Tick = -100;
		public int cutsceneUntil;
		/** Awakened between awakenStart and awakenEnd (server ticks); awakenEnd = MAX_VALUE means until F. */
		public int awakenStart, awakenEnd;
		boolean wasAwakened;
		public GameType lastMode;
		/** We turned on mayfly for this survival player (so we also turn it off). */
		boolean grantedFly;
		boolean lastFlying;
		int syncTimer;
		String lastSync = "";
	}

	public static State state(ServerPlayer p) {
		return STATES.computeIfAbsent(p.getUUID(), u -> new State());
	}

	public static PowerType get(ServerPlayer p) {
		String id = p.getAttached(POWER);
		return id == null ? PowerType.NONE : PowerType.byId(id);
	}

	public static boolean infinite(ServerPlayer p) {
		return p.isCreative();
	}

	public static boolean inCutscene(ServerPlayer p) {
		State s = STATES.get(p.getUUID());
		return s != null && s.cutsceneUntil > p.server.getTickCount();
	}

	public static boolean isAwakened(ServerPlayer p) {
		State s = STATES.get(p.getUUID());
		if (s == null) return false;
		int now = p.server.getTickCount();
		return s.awakenEnd > now && s.awakenStart <= now;
	}

	static final net.minecraft.resources.ResourceLocation HEALTH_ID = Superpowers.id("power_health");

	/** Every power gives 100 health (vanilla is 20). */
	static void applyHealth(ServerPlayer p, PowerType type) {
		var attr = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
		if (attr == null) return;
		boolean has = attr.hasModifier(HEALTH_ID);
		if (type != PowerType.NONE && !has) {
			attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(HEALTH_ID, 80.0,
					net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		} else if (type == PowerType.NONE && has) {
			attr.removeModifier(HEALTH_ID);
			if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
		}
	}

	/** Creative players can't be hurt anyway; the awakened Entity can't be hurt at all. */
	public static boolean infiniteHealth(ServerPlayer p) {
		return p.isCreative() || Hunt.isEntityForm(p);
	}

	/** Flight while awakened. Double-tap jump to take off. */
	public static boolean canFly(ServerPlayer p) {
		return get(p).canFly(isAwakened(p));
	}

	static void tickFlight(ServerPlayer p, State s) {
		net.minecraft.world.entity.player.Abilities ab = p.getAbilities();
		boolean fly = canFly(p);
		boolean dirty = false;
		if (!p.isCreative() && !p.isSpectator()) {
			if (fly && !ab.mayfly) {
				ab.mayfly = true;
				s.grantedFly = true;
				dirty = true;
			} else if (!fly && s.grantedFly) {
				s.grantedFly = false;
				ab.mayfly = false;
				if (ab.flying) {
					ab.flying = false;
					p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, false, false));
				}
				dirty = true;
			}
		} else {
			s.grantedFly = false;
		}
		float speed = fly ? get(p).flySpeed(isAwakened(p)) : 0.05f;
		if (Math.abs(ab.getFlyingSpeed() - speed) > 1e-4f) {
			ab.setFlyingSpeed(speed);
			dirty = true;
		}
		if (dirty) p.onUpdateAbilities();
		boolean flying = ab.flying && fly;
		if (flying != s.lastFlying) {
			s.lastFlying = flying;
			if (flying) {
				p.resetFallDistance();
				Fx.send(p.serverLevel(), Fx.TAKEOFF, p.position(), p, 1f, get(p).ordinal());
				Abilities.sound(p.serverLevel(), p.position(), get(p) == PowerType.ANGEL ? SoundEvents.ENDER_DRAGON_FLAP
						: get(p) == PowerType.SPACE ? ModSounds.TELEPORT : ModSounds.WHOOSH, 1f, get(p) == PowerType.GOD ? 0.7f : 1.2f);
			}
			broadcastPowerOf(p);
		}
	}

	public static void set(ServerPlayer p, PowerType type) {
		State s = state(p);
		if (isAwakened(p)) {
			s.awakenEnd = 0;
			s.wasAwakened = false;
		}
		if (type == PowerType.NONE) {
			p.removeAttached(POWER);
		} else {
			p.setAttached(POWER, type.id);
		}
		java.util.Arrays.fill(s.cooldowns, 0);
		s.mana = PowerType.MAX_MANA;
		s.combo = 0;
		s.lastSync = "";
		applyHealth(p, type);
		if (type != PowerType.NONE) p.setHealth(p.getMaxHealth());
		broadcastPowerOf(p);
		if (type != PowerType.NONE) {
			Fx.send(p.serverLevel(), Fx.AWAKEN, p.position(), p, 1f, type.ordinal());
			Fx.shake(p.serverLevel(), p.position(), 0.4f, 15, 20);
			Abilities.sound(p.serverLevel(), p.position(), ModSounds.SELECT, 1.2f, 1f);
			Abilities.sound(p.serverLevel(), p.position(), ModSounds.ULT, 0.7f, 1.4f);
			p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
			p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(type.displayName.toUpperCase() + " AWAKENED")
					.withStyle(st -> st.withColor(TextColor.fromRgb(type.color)).withBold(true))));
			p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("M1 combo  •  Z  X  C  •  V ultimate  •  G awakening (flight)")
					.withStyle(ChatFormatting.GRAY)));
		} else {
			Abilities.sound(p.serverLevel(), p.position(), SoundEvents.BEACON_DEACTIVATE, 1f, 1f);
		}
	}

	/** G: transform when the meter is full. */
	public static void awaken(ServerPlayer p) {
		PowerType type = get(p);
		if (type == PowerType.NONE || !p.isAlive()) return;
		State s = state(p);
		int now = p.server.getTickCount();
		if (s.cutsceneUntil > now || isAwakened(p) || s.awakenEnd > now) return;
		if (Awakening.meter(p) < PowerType.MAX_METER) {
			p.displayClientMessage(Component.literal("Awakening meter " + (int) Awakening.meter(p) + "% - " + type.meterHint)
					.withStyle(ChatFormatting.GOLD), true);
			Abilities.sound(p.serverLevel(), p.position(), SoundEvents.NOTE_BLOCK_BASS, 0.6f, 0.5f);
			return;
		}
		Awakening.setMeter(p, 0);
		s.cutsceneUntil = now + PowerType.AWAKEN_CUTSCENE;
		s.awakenStart = now + 62;
		s.awakenEnd = infinite(p) ? Integer.MAX_VALUE : now + PowerType.AWAKEN_CUTSCENE + PowerType.AWAKEN_TICKS;
		s.wasAwakened = true;
		java.util.Arrays.fill(s.cooldowns, 0);
		s.lastSync = "";
		ServerPlayNetworking.send(p, new Net.Cutscene(type.ordinal(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getX(), p.getY(), p.getZ(), 1));
		Awakening.sequence(p, type);
	}

	/** F (or time running out): back to normal. */
	public static void stopAwakening(ServerPlayer p) {
		State s = state(p);
		int now = p.server.getTickCount();
		if (s.awakenEnd <= now || s.cutsceneUntil > now) return;
		s.awakenEnd = 0;
		s.wasAwakened = false;
		s.lastSync = "";
		broadcastPowerOf(p);
		Awakening.end(p);
	}

	/** Called when the client asks to use an ability slot. */
	public static void use(ServerPlayer p, int slot) {
		if (slot < 0 || slot > 4 || !p.isAlive() || p.isSpectator()) return;
		PowerType type = get(p);
		Power power = POWERS.get(type);
		if (power == null) return;
		State s = state(p);
		int now = p.server.getTickCount();
		if (s.cutsceneUntil > now) return;
		if (s.cooldowns[slot] > 0) return;
		boolean inf = infinite(p);
		boolean awake = isAwakened(p);
		int cost = awake ? 0 : PowerType.COST[slot];
		if (!inf && s.mana < cost) {
			p.displayClientMessage(Component.literal("Not enough mana! (" + (int) s.mana + "/" + cost + ")").withStyle(ChatFormatting.RED), true);
			Abilities.sound(p.serverLevel(), p.position(), SoundEvents.NOTE_BLOCK_BASS, 0.6f, 0.5f);
			return;
		}
		if (!inf) s.mana -= cost;
		int cd = type.cooldown(slot);
		s.cooldowns[slot] = awake ? Math.max(slot == 0 ? 4 : 8, (int) (cd * 0.4f)) : cd;
		s.lastSync = "";

		switch (slot) {
			case 0 -> {
				s.combo = now - s.lastM1Tick <= 20 ? (s.combo + 1) % 4 : 0;
				s.lastM1Tick = now;
				power.m1(p, s.combo);
			}
			case 1 -> power.z(p);
			case 2 -> power.x(p);
			case 3 -> power.c(p);
			case 4 -> {
				s.cutsceneUntil = now + type.ultDuration;
				power.v(p);
			}
		}
	}

	/** Every server tick: mana regen, cooldowns, awakening timer, book handout, sync. */
	public static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			State s = state(p);
			PowerType type = get(p);

			for (int i = 0; i < 5; i++) {
				if (s.cooldowns[i] > 0) s.cooldowns[i]--;
			}
			if (infinite(p)) {
				s.mana = PowerType.MAX_MANA;
			} else if (s.mana < PowerType.MAX_MANA) {
				s.mana = Math.min(PowerType.MAX_MANA, s.mana + 0.35f);
			}

			if (s.wasAwakened && s.awakenEnd <= now) {
				s.wasAwakened = false;
				broadcastPowerOf(p);
				Awakening.end(p);
			}
			if (isAwakened(p) && now % 20 == 0) {
				p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 2, false, false, false));
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false, false));
			}

			tickFlight(p, s);
			if (now % 20 == 0) applyHealth(p, type);

			GameType mode = p.gameMode.getGameModeForPlayer();
			if (mode == GameType.CREATIVE && s.lastMode != GameType.CREATIVE && !hasTome(p)) {
				p.getInventory().add(new ItemStack(ModItems.POWER_TOME));
				p.displayClientMessage(Component.literal("✦ The SP Book appeared in your inventory - right-click it to choose a power!")
						.withStyle(ChatFormatting.GOLD), false);
				Abilities.sound(p.serverLevel(), p.position(), SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
			}
			s.lastMode = mode;

			// keep the caster safe and floating during cutscenes
			if (s.cutsceneUntil > now) {
				p.resetFallDistance();
				if (s.cutsceneUntil - now == 1) {
					p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
				}
			}

			if (++s.syncTimer >= 2) {
				s.syncTimer = 0;
				int[] cd = s.cooldowns.clone();
				float meter = Awakening.meter(p);
				int left = !isAwakened(p) ? 0 : s.awakenEnd == Integer.MAX_VALUE ? -1 : s.awakenEnd - now;
				String key = type.ordinal() + ":" + (int) (s.mana * 10) + ":" + infinite(p) + ":" + java.util.Arrays.toString(cd)
						+ ":" + (int) (meter * 10) + ":" + (left > 0 ? left / 10 : left);
				if (!key.equals(s.lastSync)) {
					s.lastSync = key;
					ServerPlayNetworking.send(p, new Net.Sync(type.ordinal(), s.mana, infinite(p), cd, meter, left));
				}
			}
		}
	}

	private static boolean hasTome(ServerPlayer p) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(ModItems.POWER_TOME)) return true;
		}
		return false;
	}

	public static void broadcastPowerOf(ServerPlayer p) {
		Net.PowerOf payload = new Net.PowerOf(p.getId(), get(p).ordinal(), isAwakened(p), state(p).lastFlying);
		for (ServerPlayer other : p.server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(other, payload);
		}
	}

	public static void onJoin(ServerPlayer p) {
		State s = state(p);
		s.lastSync = "";
		s.cutsceneUntil = 0;
		broadcastPowerOf(p);
		for (ServerPlayer other : p.server.getPlayerList().getPlayers()) {
			if (other != p) ServerPlayNetworking.send(p, new Net.PowerOf(other.getId(), get(other).ordinal(), isAwakened(other), state(other).lastFlying));
		}
	}

	public static void onLeave(ServerPlayer p) {
		STATES.remove(p.getUUID());
	}

	public static void clear() {
		STATES.clear();
	}
}
