package com.superpowers.power;

import com.mojang.serialization.Codec;
import com.superpowers.Superpowers;
import com.superpowers.registry.ModSounds;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The awakening meter, how each power fills it, and the transformation sequence. */
public final class Awakening {
	private Awakening() {}

	public static final AttachmentType<Float> METER = AttachmentRegistry.<Float>builder()
			.persistent(Codec.FLOAT)
			.copyOnDeath()
			.buildAndRegister(Superpowers.id("awakening_meter"));

	/** victim -> Fire user who set it ablaze, so burn kills are credited. */
	private static final Map<UUID, UUID> IGNITED = new HashMap<>();

	public static float meter(ServerPlayer p) {
		Float f = p.getAttached(METER);
		return f == null ? 0f : f;
	}

	public static void setMeter(ServerPlayer p, float v) {
		p.setAttached(METER, Math.max(0, Math.min(PowerType.MAX_METER, v)));
	}

	public static void add(ServerPlayer p, float amount) {
		if (amount <= 0 || PowerManager.get(p) == PowerType.NONE || PowerManager.isAwakened(p)) return;
		float before = meter(p);
		if (before >= PowerType.MAX_METER) return;
		float after = Math.min(PowerType.MAX_METER, before + amount);
		setMeter(p, after);
		if (after >= PowerType.MAX_METER) ready(p);
	}

	static void ready(ServerPlayer p) {
		PowerType t = PowerManager.get(p);
		p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
		p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("AWAKENING READY")
				.withStyle(st -> st.withColor(TextColor.fromRgb(t.color)).withBold(true))));
		p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Press G to become the " + titleCase(t.awakenedName))
				.withStyle(ChatFormatting.GOLD)));
		Fx.send(p.serverLevel(), Fx.METER_FULL, p.position(), p, 1f, t.ordinal());
		Abilities.sound(p.serverLevel(), p.position(), ModSounds.SELECT, 1.2f, 0.7f);
		Abilities.sound(p.serverLevel(), p.position(), SoundEvents.PLAYER_LEVELUP, 0.8f, 0.6f);
	}

	static String titleCase(String s) {
		StringBuilder b = new StringBuilder();
		for (String w : s.toLowerCase().split(" ")) {
			if (!b.isEmpty()) b.append(' ');
			b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return b.toString();
	}

	// ------------------------------------------------------------------ meter sources

	public static void markIgnited(Entity victim, ServerPlayer by) {
		if (PowerManager.get(by) == PowerType.FIRE) IGNITED.put(victim.getUUID(), by.getUUID());
	}

	/** Fire abilities set the world ablaze. */
	public static void onSetFire(ServerPlayer p, int count) {
		if (PowerManager.get(p) == PowerType.FIRE) add(p, Math.min(1f, count * 0.06f));
	}

	/** Called from the FireBlock mixin whenever fire consumes a block. */
	public static void onBlockBurned(ServerLevel level, BlockPos pos) {
		Vec3 c = Vec3.atCenterOf(pos);
		for (ServerPlayer p : level.players()) {
			if (PowerManager.get(p) == PowerType.FIRE && p.position().distanceToSqr(c) < 48 * 48) add(p, 0.05f);
		}
	}

	public static void onDeath(LivingEntity victim, DamageSource src) {
		if (!(victim.level() instanceof ServerLevel level)) return;
		boolean burning = victim.isOnFire() || src.is(DamageTypeTags.IS_FIRE);
		ServerPlayer killer = null;
		if (src.getEntity() instanceof ServerPlayer sp) killer = sp;
		else if (victim.getKillCredit() instanceof ServerPlayer sp) killer = sp;
		UUID igniter = IGNITED.remove(victim.getUUID());
		if (killer == null && burning && igniter != null) killer = level.getServer().getPlayerList().getPlayer(igniter);
		if (killer == null || killer == victim) return;

		float gain = switch (PowerManager.get(killer)) {
			// roughly 30 kills of the right kind to fill the meter
			case FIRE -> burning ? 3.5f : 0.5f;
			case ANGEL -> victim instanceof Enemy ? (victim.getMaxHealth() >= 150 ? 15f : victim.isInvertedHealAndHarm() ? 3.5f : 3f) : 0f;
			case DEMON -> victim instanceof AbstractVillager ? 5f
					: victim instanceof Animal ? 2.5f
					: (victim instanceof AmbientCreature || victim instanceof WaterAnimal) ? 1.5f
					: victim instanceof Player ? 10f : 0f;
			case SPACE -> (!victim.onGround() || src.is(DamageTypeTags.IS_FALL) || src.is(DamageTypes.FELL_OUT_OF_WORLD)) ? 3.5f : 0.5f;
			case GOD -> Math.min(20f, Math.max(1f, victim.getMaxHealth() / 8f));
			// the dark: low light or inside your own domain
			case ENTITY -> (level.getMaxLocalRawBrightness(victim.blockPosition()) <= 7 || Hunt.inDomainOf(killer, victim) ? 3.5f : 1f)
					+ (victim instanceof Player ? 6f : 0f);
			default -> 0f;
		};
		add(killer, gain);
	}

	// ------------------------------------------------------------------ the transformation

	/** Server side of the awakening cutscene: charge for 62 ticks, then the burst. */
	static void sequence(ServerPlayer p, PowerType t) {
		ServerLevel l = p.serverLevel();
		Fx.send(l, Fx.AWAKEN_CHARGE, p.position(), p, 1f, 62);
		Fx.tint(l, p.position(), t == PowerType.ANGEL || t == PowerType.GOD ? 0xD89A30 : t == PowerType.FIRE ? 0x2A6CFF : t.color, 0.4f, 110, 90);
		Abilities.sound(l, p.position(), ModSounds.ULT, 3f, 0.7f);
		Abilities.sound(l, p.position(), ModSounds.CHARGE, 3f, 0.45f);
		Abilities.anim(p, Fx.Anim.SPREAD, PowerType.AWAKEN_CUTSCENE - 10);
		Tasks.later(p, 30, () -> Abilities.sound(l, p.position(), ModSounds.CHARGE, 3f, 0.8f));
		Tasks.later(p, 50, () -> Abilities.sound(l, p.position(), t == PowerType.DEMON ? ModSounds.DEMON : ModSounds.CHOIR, 3f, 0.7f));
		Tasks.later(p, 62, () -> {
			Vec3 c = p.position();
			PowerManager.broadcastPowerOf(p);
			Fx.send(l, Fx.AWAKEN_BURST, c, p, 1f, t.ordinal());
			Fx.flash(l, c, 0xFFFFFF, 0.8f, 14, 120);
			Fx.shake(l, c, 2f, 40, 120);
			Abilities.sound(l, c, ModSounds.BOOM, 8f, 0.5f);
			Abilities.sound(l, c, ModSounds.THUNDER, 5f, 0.6f);
			Abilities.sound(l, c, ModSounds.ULT, 4f, 1.2f);
			Abilities.blast(l, p, c, 14, 30f, t == PowerType.FIRE ? 6 : 0, 3.0);
			Vec3 ground = Abilities.ground(l, c.add(0, 1, 0), 10);
			Destruction.crater(l, p, ground, 7, 0.45, t == PowerType.FIRE ? 0.6f : 0f, 90);
		});
	}

	public static void end(ServerPlayer p) {
		// flight goes away with the form: feather-fall gently instead of dying
		p.resetFallDistance();
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 200, 0, false, false, true));
		Fx.send(p.serverLevel(), Fx.AWAKEN_END, p.position(), p, 1f, PowerManager.get(p).ordinal());
		Abilities.sound(p.serverLevel(), p.position(), SoundEvents.BEACON_DEACTIVATE, 1.5f, 0.6f);
		Abilities.sound(p.serverLevel(), p.position(), ModSounds.TIMESTOP, 1f, 1.6f);
	}

	public static void clear() {
		IGNITED.clear();
	}
}
