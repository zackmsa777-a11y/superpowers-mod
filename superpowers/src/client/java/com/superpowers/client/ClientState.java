package com.superpowers.client;

import com.superpowers.power.PowerType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/** Everything the client knows about powers: own mana/cooldowns, other players' powers, temporary forms and anims. */
public final class ClientState {
	private ClientState() {}

	public static PowerType power = PowerType.NONE;
	public static float mana = PowerType.MAX_MANA;
	public static boolean infinite;
	public static final int[] cooldowns = new int[5];
	/** Awakening meter 0..100 and ticks of awakening left (0 = not awakened, -1 = until F). */
	public static float meter;
	public static int awakenLeft;

	/** Client ticks spent in a world (not counting pause). */
	public static long ticks;

	private static final Map<Integer, PowerType> POWERS = new HashMap<>();
	/** entity id -> client tick the awakened form appeared. */
	private static final Map<Integer, Long> AWAKENED = new HashMap<>();

	public static void setAwakened(int entityId, boolean awakened) {
		if (awakened) AWAKENED.putIfAbsent(entityId, ticks);
		else AWAKENED.remove(entityId);
	}

	public static boolean isAwakened(Entity e) {
		return AWAKENED.containsKey(e.getId());
	}

	/** 0..1, ramps up over the first 20 ticks of the awakened form. */
	public static float awakenStrength(Entity e, float pt) {
		Long start = AWAKENED.get(e.getId());
		if (start == null) return 0;
		return Mth.clamp((ticks - start + pt) / 20f, 0, 1);
	}

	// ------------------------------------------------------------------ gore

	/** entity id -> client tick until which its head is gone / it isn't drawn at all. */
	private static final Map<Integer, Long> HEADLESS = new HashMap<>(), HIDDEN = new HashMap<>();

	public static void setHeadless(int id, int duration) {
		HEADLESS.put(id, ticks + duration);
	}

	public static boolean isHeadless(int id) {
		Long t = HEADLESS.get(id);
		return t != null && t > ticks;
	}

	/** Client-only lookalikes (corpses, torn heads): no name tags; head-only ones show nothing but the head. */
	private static final java.util.Set<Integer> PROPS = new java.util.HashSet<>();
	private static final Map<Integer, Long> HEAD_ONLY = new HashMap<>();

	public static void addProp(int id) {
		PROPS.add(id);
	}

	public static boolean isProp(int id) {
		return PROPS.contains(id);
	}

	public static void setHeadOnly(int id, int duration) {
		HEAD_ONLY.put(id, ticks + duration);
	}

	public static boolean isHeadOnly(int id) {
		Long t = HEAD_ONLY.get(id);
		return t != null && t > ticks;
	}

	public static void hide(int id, int duration) {
		HIDDEN.put(id, ticks + duration);
	}

	public static boolean isHidden(int id) {
		Long t = HIDDEN.get(id);
		return t != null && t > ticks;
	}

	// ------------------------------------------------------------------ flight

	private static final java.util.Set<Integer> FLYING = new java.util.HashSet<>();

	/** Smoothed per-player flight blend (0..1) and body tilt (0..1 of the power's max lean). */
	private static final class Fly {
		float amt, amtO, tilt, tiltO, walk, walkO, run, runO;
	}

	/** Wing motion blend for this frame: {fly, walk, run}, each 0..1 (all 0 = idle). */
	public static float[] wingState(Entity e, float pt) {
		Fly f = FLY.get(e.getId());
		if (f == null) return new float[3];
		float fly = Mth.lerp(pt, f.amtO, f.amt);
		return new float[]{fly, Mth.lerp(pt, f.walkO, f.walk) * (1 - fly), Mth.lerp(pt, f.runO, f.run) * (1 - fly)};
	}

	private static final Map<Integer, Fly> FLY = new HashMap<>();

	public static void setFlying(int entityId, boolean flying) {
		if (flying) FLYING.add(entityId);
		else FLYING.remove(entityId);
	}

	/** Flying with power flight right now (own player reads its abilities, others come from the server). */
	public static boolean isPowerFlying(Player p) {
		if (!powerOf(p).canFly(isAwakened(p))) return false;
		if (p == Minecraft.getInstance().player) return p.getAbilities().flying;
		return FLYING.contains(p.getId());
	}

	public static float flight(Entity e, float pt) {
		Fly f = FLY.get(e.getId());
		return f == null ? 0 : Mth.lerp(pt, f.amtO, f.amt);
	}

	/** Body lean in degrees for the flight pose. */
	public static float flightTilt(Entity e, float pt) {
		Fly f = FLY.get(e.getId());
		if (f == null) return 0;
		float max = switch (powerOf(e)) {
			case GOD -> 82f;
			case FIRE -> 40f;
			case ANGEL, DEMON -> 32f;
			case SPACE -> 10f;
			default -> 0f; // ENTITY floats upright, limbs dangling
		};
		return Mth.lerp(pt, f.tiltO, f.tilt) * max * Mth.lerp(pt, f.amtO, f.amt);
	}

	/** 0..1 how much of the lean is applied, without the per-power max. */
	public static float flightLean(Entity e, float pt) {
		Fly f = FLY.get(e.getId());
		return f == null ? 0 : Mth.lerp(pt, f.tiltO, f.tilt) * Mth.lerp(pt, f.amtO, f.amt);
	}

	static void tickFlight() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (Player p : mc.level.players()) {
			seen.add(p.getId());
			Fly f = FLY.computeIfAbsent(p.getId(), k -> new Fly());
			f.amtO = f.amt;
			f.tiltO = f.tilt;
			f.walkO = f.walk;
			f.runO = f.run;
			float target = isPowerFlying(p) ? 1 : 0;
			f.amt += Mth.clamp(target - f.amt, -0.12f, 0.2f);
			double dx = p.getX() - p.xo, dz = p.getZ() - p.zo, dy = p.getY() - p.yo;
			double speed = Math.sqrt(dx * dx + dz * dz);
			float lean = (float) Mth.clamp((speed - 0.08) / 0.7, 0, 1);
			if (dy > 0.25 && speed < 0.3) lean *= 0.3f; // rising straight up stays upright
			f.tilt += (lean - f.tilt) * 0.15f;
			boolean sprint = p.isSprinting() && speed > 0.1;
			float walk = sprint ? 0 : (float) Mth.clamp(speed / 0.2, 0, 1);
			f.walk += (walk - f.walk) * 0.2f;
			f.run += ((sprint ? 1 : 0) - f.run) * 0.2f;
		}
		FLY.keySet().retainAll(seen);
	}

	public enum Form {WINGS, DEMON, RINGS}

	private record Timed(long start, long end) {}

	private static final Map<Form, Map<Integer, Timed>> FORMS = new HashMap<>();

	public record Anim(int id, long start, int duration) {}

	private static final Map<Integer, Anim> ANIMS = new HashMap<>();

	public static PowerType powerOf(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		if (e == mc.player) return power;
		return POWERS.getOrDefault(e.getId(), PowerType.NONE);
	}

	public static void setPowerOf(int entityId, PowerType type) {
		POWERS.put(entityId, type);
	}

	public static void addForm(Form form, int entityId, int duration) {
		FORMS.computeIfAbsent(form, f -> new HashMap<>()).put(entityId, new Timed(ticks, ticks + duration));
	}

	/** 0..1 strength of a temporary form, with fade in/out. */
	public static float form(Form form, int entityId, float pt) {
		Map<Integer, Timed> m = FORMS.get(form);
		if (m == null) return 0;
		Timed t = m.get(entityId);
		if (t == null) return 0;
		float now = ticks + pt;
		if (now > t.end) return 0;
		return Mth.clamp((now - t.start) / 8f, 0, 1) * Mth.clamp((t.end - now) / 10f, 0, 1);
	}

	public static void playAnim(int entityId, int anim, int duration) {
		ANIMS.put(entityId, new Anim(anim, ticks, Math.max(1, duration)));
	}

	public static Anim anim(int entityId) {
		Anim a = ANIMS.get(entityId);
		if (a != null && ticks > a.start + a.duration + 1) {
			ANIMS.remove(entityId);
			return null;
		}
		return a;
	}

	public static void tick() {
		ticks++;
		tickFlight();
		for (int i = 0; i < 5; i++) {
			if (cooldowns[i] > 0) cooldowns[i]--;
		}
		if (awakenLeft > 0) awakenLeft--;
	}

	public static void reset() {
		power = PowerType.NONE;
		mana = PowerType.MAX_MANA;
		infinite = false;
		java.util.Arrays.fill(cooldowns, 0);
		meter = 0;
		awakenLeft = 0;
		AWAKENED.clear();
		FLYING.clear();
		HEADLESS.clear();
		HIDDEN.clear();
		PROPS.clear();
		HEAD_ONLY.clear();
		FLY.clear();
		POWERS.clear();
		FORMS.clear();
		ANIMS.clear();
	}
}
