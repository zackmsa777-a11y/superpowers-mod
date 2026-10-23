package com.superpowers.client;

import com.superpowers.net.Net;
import com.superpowers.power.PowerType;
import com.superpowers.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Cinematic camera for ultimates. Keyframes are in a frame aligned with the caster's facing. */
public final class Cutscene {
	private Cutscene() {}

	/** Camera keyframe. camT / lookT choose whether the offset is relative to the target (else the caster). */
	record Key(int t, boolean camT, Vec3 cam, boolean lookT, Vec3 look) {}

	static Key P(int t, double cx, double cy, double cz, double lx, double ly, double lz) {
		return new Key(t, false, new Vec3(cx, cy, cz), false, new Vec3(lx, ly, lz));
	}

	static Key PT(int t, double cx, double cy, double cz, double lx, double ly, double lz) {
		return new Key(t, false, new Vec3(cx, cy, cz), true, new Vec3(lx, ly, lz));
	}

	static Key T(int t, double cx, double cy, double cz, double lx, double ly, double lz) {
		return new Key(t, true, new Vec3(cx, cy, cz), true, new Vec3(lx, ly, lz));
	}

	private static PowerType type = PowerType.NONE;
	private static long start;
	private static int duration;
	private static Vec3 target = Vec3.ZERO;
	private static Vec3 fwd = new Vec3(0, 0, 1), right = new Vec3(-1, 0, 0);
	private static List<Key> keys = List.of();
	/** 0 = ultimate, 1 = awakening transformation. */
	private static int kind;

	static List<Key> awakeningKeys() {
		return List.of(
				P(0, 0, 1.6, 3.2, 0, 1.6, 0),
				P(20, 2.6, 1.2, 2.2, 0, 1.9, 0),
				P(40, 2.0, 2.0, -3.2, 0, 1.8, 0),
				P(55, -1.2, 1.9, 1.9, 0, 1.7, 0),
				P(62, 0, 1.68, 1.15, 0, 1.62, 0),
				P(72, 0, 2.4, 7.5, 0, 1.6, 0),
				P(100, 0, 3.0, 10, 0, 1.9, 0));
	}

	static List<Key> keysFor(PowerType t) {
		return switch (t) {
			case FIRE -> List.of(
					P(0, 0, 1.5, 3.6, 0, 1.5, 0),
					P(20, 2.4, 0.8, 2.8, 0, 2.2, 0),
					P(42, -5.0, 0.8, 9.0, 0, 3.4, 0),
					P(60, -6.0, 2.0, 8.5, 0, 3.8, 0),
					PT(66, -1.4, 2.6, -4.5, 0, 1, 0),
					PT(78, -2.0, 4.0, -7.0, 0, 1, 0),
					T(84, 9, 7, -16, 0, 3, 0),
					T(110, 12, 9, -20, 0, 4, 0));
			case SPACE -> List.of(
					P(0, 0, 1.6, 3.0, 0, 1.6, 0),
					P(15, 1.2, 1.8, 2.2, 0, 1.7, 0),
					PT(30, -2.2, 2.2, -4.5, 0, 0, 0),
					T(60, -20, 5, 6, 0, 0, 0),
					T(85, -14, 9, -20, 0, 0, 0),
					T(95, 0, 4, -16, 0, 0, 0),
					T(105, 4, 12, -30, 0, 0, 0),
					T(140, 6, 14, -34, 0, 0, 0));
			case ANGEL -> List.of(
					P(0, 0, 1.5, 3.4, 0, 1.6, 0),
					P(22, -2.5, 1.4, -3.2, 0, 2.0, 0),
					PT(38, 1.5, 0.4, 1.5, 0, 28, 0),
					T(60, 16, 6, -12, 0, 14, 0),
					T(74, 20, 8, -22, 0, 6, 0),
					T(120, 22, 10, -26, 0, 5, 0));
			case DEMON -> List.of(
					P(0, 0, 1.7, 2.6, 0, 1.6, 0),
					P(25, 0, 1.65, 1.4, 0, 1.7, 0),
					P(42, 0.5, 20, 2, 0, 0, 0),
					P(65, 8, 14, 12, 0, 0, 0),
					P(80, 14, 6, 16, 0, 2, 0),
					P(120, 18, 9, 20, 0, 3, 0));
			case GOD -> List.of(
					P(0, 0, 1.5, 3.2, 0, 1.6, 0),
					P(20, 4.0, 1.5, 0.5, 0, 2.0, 0),
					P(45, 1.0, 3.0, -5.5, 0, 2.5, 0),
					P(70, -5.0, 4.5, 3.5, 0, 2.5, 0),
					P(85, -3.0, 1.0, 6.0, 0, 4.0, 0),
					P(95, 0, 28, 14, 0, 0, 0),
					P(130, 0, 34, 18, 0, 0, 0));
			case ENTITY -> List.of(
					P(0, 0, 1.6, 3.0, 0, 1.6, 0),
					T(12, 3.0, 0.5, -3.0, 0, 1.3, 0),
					T(32, 3.4, 1.0, -3.2, 0, 2.2, 0),
					T(58, 2.6, 2.3, -2.2, 0, 2.4, 0.4),
					T(80, 2.2, 2.5, -1.8, 0, 2.45, 0.5),
					T(92, 2.1, 2.55, -1.7, 0, 2.45, 0.5),
					T(122, -3.0, 2.2, -3.0, 0, 2.2, 0),
					T(150, -5.4, 3.2, -6.2, 0, 1.6, 0));
			default -> List.of();
		};
	}

	/** The victim of THE FEAST: you turn around, and it is there. Target = the creature's feet. */
	static List<Key> victimKeys() {
		return List.of(
				P(0, 0, 1.62, 0.0, 0, 1.62, 4),
				P(14, 0, 1.62, 0.0, 0, 1.62, 4),
				PT(30, 0, 1.62, 0.0, 0, 1.4, 0),
				PT(44, 0, 1.62, 0.0, 0, 2.4, 0),
				PT(60, 0, 1.62, 0.0, 0, 2.45, 0),
				PT(80, 0, 1.62, 0.0, 0, 2.45, 0),
				PT(150, 0, 1.62, 0.0, 0, 2.45, 0));
	}

	public static void start(Net.Cutscene pkt) {
		type = PowerType.byOrdinal(pkt.power());
		if (type == PowerType.NONE) return;
		start = ClientState.ticks;
		kind = pkt.kind();
		duration = kind == 1 ? PowerType.AWAKEN_CUTSCENE : type.ultDuration;
		target = new Vec3(pkt.tx(), pkt.ty(), pkt.tz());
		float yaw = pkt.yaw() * Mth.DEG_TO_RAD;
		fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		right = fwd.cross(new Vec3(0, 1, 0)).normalize();
		keys = kind == 1 ? awakeningKeys() : kind == 2 ? victimKeys() : keysFor(type);
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(kind == 2 ? ModSounds.WHISPER : ModSounds.ULT, 1f, 0.9f));
	}

	public static boolean active() {
		return type != PowerType.NONE && ClientState.ticks - start < duration;
	}

	public static float time(float pt) {
		return ClientState.ticks - start + pt;
	}

	public static void stop() {
		type = PowerType.NONE;
	}

	/** Called at the start of each client tick: hover the caster and keep them still. */
	public static void tick() {
		if (type != PowerType.NONE && !active()) type = PowerType.NONE;
		if (!active()) return;
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			stop();
			return;
		}
		float t = time(0);
		double vy = kind == 1 ? (t < 55 ? 0.03 : 0) : switch (type) {
			case FIRE -> t < 40 ? 0.08 : 0;
			case SPACE -> t < 30 ? 0.03 : 0;
			case ANGEL -> t < 30 ? 0.05 : 0;
			case GOD -> t < 60 ? 0.06 : 0;
			default -> 0;
		};
		p.setDeltaMovement(0, vy, 0);
		p.fallDistance = 0;
	}

	private static Vec3 local(boolean onTarget, Vec3 o, float pt) {
		Vec3 base;
		if (onTarget) {
			base = target;
		} else {
			LocalPlayer p = Minecraft.getInstance().player;
			base = p != null ? p.getPosition(pt) : target;
		}
		return base.add(right.scale(o.x)).add(0, o.y, 0).add(fwd.scale(o.z));
	}

	static double smooth(double s) {
		return s * s * (3 - 2 * s);
	}

	/**
	 * Computes the cutscene camera, blended with the vanilla camera at the start and end.
	 * Returns {x, y, z, yaw, pitch} or null when no cutscene is playing.
	 */
	public static double[] camera(Vec3 vanillaPos, float vanillaYaw, float vanillaPitch, float pt) {
		if (!active() || keys.isEmpty()) return null;
		float t = time(pt);
		Key k0 = keys.get(0), k1 = keys.get(keys.size() - 1);
		for (int i = 0; i < keys.size() - 1; i++) {
			if (t >= keys.get(i).t && t <= keys.get(i + 1).t) {
				k0 = keys.get(i);
				k1 = keys.get(i + 1);
				break;
			}
		}
		double s = k1.t == k0.t ? 1 : smooth(Mth.clamp((t - k0.t) / (double) (k1.t - k0.t), 0, 1));
		Vec3 cam = local(k0.camT, k0.cam, pt).lerp(local(k1.camT, k1.cam, pt), s);
		Vec3 look = local(k0.lookT, k0.look, pt).lerp(local(k1.lookT, k1.look, pt), s);
		Vec3 d = look.subtract(cam);
		double h = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
		float pitch = (float) (Mth.atan2(-d.y, h) * Mth.RAD_TO_DEG);

		// blend in over 8 ticks and out over the last 14
		float blend = (float) Math.min(smooth(Mth.clamp(t / 8f, 0, 1)), smooth(Mth.clamp((duration - t) / 14f, 0, 1)));
		Vec3 pos = vanillaPos.lerp(cam, blend);
		yaw = Mth.rotLerp(blend, vanillaYaw, yaw);
		pitch = Mth.lerp(blend, vanillaPitch, pitch);
		return new double[]{pos.x, pos.y, pos.z, yaw, pitch};
	}

	/** Letterbox bars and the ultimate's title card. */
	public static void renderOverlay(GuiGraphics g, float pt) {
		int w = g.guiWidth(), h = g.guiHeight();
		float t = time(pt);
		float bars = (float) Math.min(smooth(Mth.clamp(t / 10f, 0, 1)), smooth(Mth.clamp((duration - t) / 12f, 0, 1)));
		int bh = (int) (h * 0.12f * bars);
		g.fill(0, 0, w, bh, 0xFF000000);
		g.fill(0, h - bh, w, h, 0xFF000000);

		float titleAt = kind == 1 ? 64 : 6;
		float in = Mth.clamp((t - titleAt) / 10f, 0, 1), out = Mth.clamp((duration - 12 - t) / 12f, 0, 1);
		float a = Math.min(in, out);
		if (a <= 0.02f) return;
		Font font = Minecraft.getInstance().font;
		String title = spaced(kind == 1 ? "AWAKENING" : kind == 2 ? "IT FOUND YOU" : type.abilityNames[4]);
		String sub = kind == 1 ? "—  " + type.awakenedName + "  —" : kind == 2 ? "" : "—  " + type.displayName.toUpperCase() + " ULTIMATE  —";
		int alpha = (int) (a * 255) << 24;
		int tw = font.width(title);
		float scale = Math.min(2.6f + 0.25f * (1 - in), w * 0.9f / tw);
		g.pose().pushPose();
		g.pose().translate(w / 2f, h - bh - 46, 0);
		g.pose().scale(scale, scale, 1);
		g.drawString(font, title, -tw / 2 + 1, 1, alpha | 0x000000, false);
		int titleCol = kind == 1 && type == PowerType.FIRE ? 0x5CC8FF : type.color;
		g.drawString(font, title, -tw / 2, 0, alpha | (titleCol & 0xFFFFFF), false);
		g.pose().popPose();
		int sw = font.width(sub);
		g.drawString(font, sub, (w - sw) / 2, h - bh - 18, alpha | 0xDDDDDD, true);
		// thin accent lines
		int lw = (int) (w * 0.25f * in);
		g.fill(w / 2 - lw, h - bh - 22, w / 2 + lw, h - bh - 21, alpha | (type.color & 0xFFFFFF));
	}

	static String spaced(String s) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			if (i > 0) b.append(' ');
			b.append(s.charAt(i));
		}
		return b.toString();
	}
}
