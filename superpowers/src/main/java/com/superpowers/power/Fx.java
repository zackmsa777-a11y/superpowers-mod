package com.superpowers.power;

import com.superpowers.net.Net;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Effect ids shared by server and client, plus server-side senders. */
public final class Fx {
	private Fx() {}

	// common
	public static final int AWAKEN = 1;          // caster; extra = power ordinal
	public static final int SHAKE = 2;           // scale = intensity, extra = duration
	public static final int FLASH = 3;           // dir = rgb (0..1), scale = alpha, extra = duration
	public static final int TINT = 4;            // dir = rgb, scale = strength, extra = duration (sky/fog/screen tint)

	// fire
	public static final int FIRE_PUNCH = 10;     // pos, dir = look, extra = combo
	public static final int FIRE_ORB_CAST = 11;  // pos = hand, dir = look
	public static final int FIRE_EXPLOSION = 12; // pos, scale = radius
	public static final int FIRE_DASH = 13;      // pos = start, dir = end - start
	public static final int FIRE_PILLAR = 14;    // pos = ground, scale = height
	public static final int FIRE_RING_CAST = 15; // pos = feet, scale = radius
	public static final int FIRE_SUN_CHARGE = 16;// caster, extra = duration
	public static final int FIRE_SUN_THROW = 17; // pos = sun, dir = target - sun, extra = travel ticks
	public static final int FIRE_SUPERNOVA = 18; // pos, scale = radius

	// space
	public static final int SPACE_PUNCH = 20;
	public static final int SPACE_WARP = 21;     // pos = from, dir = to - from
	public static final int SPACE_WELL = 22;     // pos, scale = radius, extra = duration
	public static final int SPACE_SLAM = 23;     // pos, scale = radius
	public static final int SPACE_METEOR_MARK = 24; // pos, scale = radius, extra = duration
	public static final int SPACE_METEOR_IMPACT = 25; // pos, scale = radius
	public static final int SPACE_BLACKHOLE = 26;// pos, scale = radius, extra = duration
	public static final int SPACE_BIGBANG = 27;  // pos, scale = radius
	public static final int SPACE_COLLAPSE = 28; // pos

	// angel
	public static final int ANGEL_SLASH = 30;
	public static final int ANGEL_WINGS = 31;    // caster, extra = duration
	public static final int ANGEL_LANCE_SUMMON = 32; // pos
	public static final int ANGEL_LANCE_HIT = 33; // pos
	public static final int ANGEL_SANCTUARY = 34; // pos, scale = radius, extra = duration
	public static final int ANGEL_LIGHT_PILLAR = 35; // pos, scale = height
	public static final int ANGEL_HALO_SKY = 36; // pos (sky), scale = radius, extra = duration
	public static final int ANGEL_JUDGMENT = 37; // pos (ground), scale = radius, extra = duration

	// demon
	public static final int DEMON_CLAW = 40;
	public static final int DEMON_CHAIN = 41;    // pos = from, dir = to - from, extra = duration
	public static final int DEMON_DASH = 42;     // pos, dir
	public static final int DEMON_SPIKE = 43;    // pos, scale = height
	public static final int DEMON_PENTAGRAM = 44;// pos, scale = radius, extra = duration
	public static final int DEMON_ERUPTION = 45; // pos, scale = radius
	public static final int DEMON_FORM = 46;     // caster, extra = duration
	public static final int DEMON_DRAIN = 47;    // pos = victim, caster = receiver

	// god
	public static final int GOD_PALM = 50;
	public static final int GOD_SMITE = 51;      // pos, scale = radius
	public static final int GOD_TIMESTOP = 52;   // pos, scale = radius, extra = duration
	public static final int GOD_SWORD_SUMMON = 53;// pos
	public static final int GOD_SWORD_HIT = 54;  // pos
	public static final int GOD_RINGS = 55;      // caster, extra = duration
	public static final int GOD_WRATH = 56;      // pos, scale = radius
	public static final int GOD_LIGHTNING = 57;  // pos (ground)

	// awakening
	public static final int AWAKEN_CHARGE = 60;  // caster, extra = duration
	public static final int AWAKEN_BURST = 61;   // caster, extra = power ordinal
	public static final int AWAKEN_END = 62;     // caster
	public static final int METER_FULL = 63;     // caster
	public static final int TAKEOFF = 64;        // caster, extra = power ordinal

	// entity 666
	public static final int ENTITY_CLAW = 70;    // pos, dir = look, extra = combo
	public static final int ENTITY_FLICKER = 71; // pos = from, dir = to - from
	public static final int ENTITY_MIMIC = 72;   // pos = fake mob spawn
	public static final int ENTITY_FAKE_DEATH = 73; // pos
	public static final int ENTITY_JUMPSCARE = 74; // caster = the entity, extra = victim id
	public static final int ENTITY_DOMAIN = 75;  // pos, scale = radius, extra = duration
	public static final int ENTITY_FEAST = 76;   // pos = creature feet, dir = facing, scale = 1 cinematic / 0 quick, extra = victim id
	public static final int ENTITY_GORE = 77;    // pos = victim head, extra = victim id (head comes off)
	public static final int ENTITY_BITE = 78;    // pos (blood)
	public static final int ENTITY_DEVOUR = 79;  // pos, extra = victim id (body is gone)
	public static final int ENTITY_THROW = 80;   // pos = body, dir = throw velocity, extra = victim id
	public static final int ENTITY_TOSS = 81;    // pos = hand/mouth, dir = facing, extra = victim id (head + spine dropped)

	public static final double RANGE = 160;

	/** flags bit 0: the caster (or a projectile's owner) is awakened. */
	static int flags(Entity caster) {
		Entity e = caster instanceof net.minecraft.world.entity.projectile.Projectile pr && pr.getOwner() != null ? pr.getOwner() : caster;
		return e instanceof ServerPlayer sp && PowerManager.isAwakened(sp) ? 1 : 0;
	}

	public static void send(ServerLevel level, int fx, Vec3 pos, Vec3 dir, Entity caster, float scale, int extra) {
		Net.Effect payload = new Net.Effect(fx, pos.x, pos.y, pos.z, (float) dir.x, (float) dir.y, (float) dir.z,
				caster == null ? -1 : caster.getId(), scale, extra, flags(caster));
		for (ServerPlayer p : PlayerLookup.around(level, pos, RANGE)) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	public static void send(ServerLevel level, int fx, Vec3 pos, Entity caster, float scale, int extra) {
		send(level, fx, pos, Vec3.ZERO, caster, scale, extra);
	}

	public static void shake(ServerLevel level, Vec3 pos, float intensity, int duration, double radius) {
		Net.Effect payload = new Net.Effect(SHAKE, pos.x, pos.y, pos.z, 0, 0, 0, -1, intensity, duration, 0);
		for (ServerPlayer p : PlayerLookup.around(level, pos, radius)) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	public static void flash(ServerLevel level, Vec3 pos, int rgb, float alpha, int duration, double radius) {
		Net.Effect payload = new Net.Effect(FLASH, pos.x, pos.y, pos.z,
				((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, -1, alpha, duration, 0);
		for (ServerPlayer p : PlayerLookup.around(level, pos, radius)) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	public static void tint(ServerLevel level, Vec3 pos, int rgb, float strength, int duration, double radius) {
		Net.Effect payload = new Net.Effect(TINT, pos.x, pos.y, pos.z,
				((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, -1, strength, duration, 0);
		for (ServerPlayer p : PlayerLookup.around(level, pos, radius)) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	/** Animation ids for {@link Net.Anim}. */
	public static final class Anim {
		public static final int PUNCH_R = 1;
		public static final int PUNCH_L = 2;
		public static final int UPPERCUT = 3;
		public static final int CAST_FORWARD = 4;
		public static final int CAST_UP = 5;
		public static final int SPREAD = 6;
		public static final int DASH = 7;
		public static final int SLAM = 8;
		public static final int CLAW = 9;
		/** Horizontal claw swipes: right hand right-to-left, left hand left-to-right, both arms. */
		public static final int SWEEP_R = 10;
		public static final int SWEEP_L = 11;
		public static final int SWEEP_BOTH = 12;
	}
}
