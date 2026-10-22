package com.superpowers.client;

import com.superpowers.client.fx.Draw;
import com.superpowers.power.PowerType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Replaces the hearts with one bar while you have a power (100 health; infinite in creative or as the Entity). */
public final class HealthBar {
	private HealthBar() {}

	public static final int W = 81, H = 9;
	private static float shown = -1, lag = -1;

	public static boolean active() {
		return ClientState.power != PowerType.NONE;
	}

	public static boolean infinite(Player p) {
		return p.isCreative() || (ClientState.power == PowerType.ENTITY && ClientState.awakenLeft != 0);
	}

	public static void render(GuiGraphics g, int x, int y, Player p) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
		float time = ClientState.ticks + pt;
		PowerType type = ClientState.power;
		boolean inf = infinite(p);
		float max = Math.max(1, p.getMaxHealth());
		float hp = Mth.clamp(p.getHealth(), 0, max);
		if (shown < 0) shown = lag = hp;
		shown += (hp - shown) * 0.35f;
		lag = hp > lag ? hp : lag + (hp - lag) * 0.04f;
		float frac = inf ? 1 : shown / max, lagFrac = inf ? 1 : lag / max;
		boolean low = !inf && hp / max < 0.25f;
		float pulse = 0.5f + 0.5f * Mth.sin(time * (low ? 0.45f : 0.12f));

		int edge = type == PowerType.NONE ? 0xD02020 : type.color;
		if (low) g.fill(x - 2, y - 2, x + W + 2, y + H + 2, ScreenFx.argb(0xFF2020, 0.25f + 0.4f * pulse));
		g.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xD0000000);
		g.fill(x, y, x + W, y + H, 0xFF240608);
		// recent damage trails behind in pale red
		int lw = (int) (W * lagFrac);
		if (lw > 0) g.fill(x, y, x + lw, y + H, 0xFFE0A0A0);
		int fw = (int) (W * frac);
		for (int i = 0; i < fw; i++) {
			int col;
			if (inf) {
				col = Draw.mix(edge, 0xFFFFFF, 0.25f + 0.35f * (float) Math.max(0, Math.sin((i - time * 3) * 0.1)));
			} else {
				col = Draw.mix(0x9A0E12, 0xFF4848, i / (float) W);
				col = Draw.mix(col, 0xFFFFFF, (float) Math.max(0, Math.sin((i + time * 2) * 0.08)) * 0.15f);
			}
			g.fill(x + i, y, x + i + 1, y + H, 0xFF000000 | col);
		}
		g.fill(x, y, x + fw, y + 2, 0x40FFFFFF);
		// absorption as a gold sliver on top
		float abs = p.getAbsorptionAmount();
		if (abs > 0 && !inf) g.fill(x, y + H - 2, x + (int) (W * Math.min(1, abs / max)), y + H, 0xFFFFD040);
		g.fill(x - 1, y - 1, x + W + 1, y, ScreenFx.argb(edge, 0.8f));

		String text = inf ? "∞" : (int) Math.ceil(hp) + " / " + (int) max;
		g.pose().pushPose();
		g.pose().translate(x + W / 2f, y + 1.5f, 0);
		float s = inf ? 0.9f : 0.7f;
		g.pose().scale(s, s, 1);
		g.drawString(font, text, -font.width(text) / 2, 0, 0xFFFFFFFF, true);
		g.pose().popPose();
		g.drawString(font, "♥", x - 9, y + 1, inf ? 0xFF000000 | edge : low ? ScreenFx.argb(0xFF4040, 0.6f + 0.4f * pulse) : 0xFFE03030, true);
	}
}
