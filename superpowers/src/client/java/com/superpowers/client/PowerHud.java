package com.superpowers.client;

import com.superpowers.client.fx.Draw;
import com.superpowers.power.PowerType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Mana bar + ability slots in the bottom-right corner. */
public final class PowerHud {
	private PowerHud() {}

	static final int SLOT = 24, GAP = 3;

	public static void render(GuiGraphics g, float pt) {
		Minecraft mc = Minecraft.getInstance();
		ScreenFx.renderOverlay(g, pt);
		com.superpowers.client.fx.HorrorFx.renderOverlay(g, pt);
		PowerType type = ClientState.power;
		if (type == PowerType.NONE || mc.player == null || mc.options.hideGui) return;
		// creative has no hearts: show the (infinite) bar where they would be
		if (mc.player.isCreative()) HealthBar.render(g, g.guiWidth() / 2 - 91, g.guiHeight() - 39, mc.player);
		Font font = mc.font;
		int w = g.guiWidth(), h = g.guiHeight();
		int panelW = 5 * SLOT + 4 * GAP;
		int x0 = w - panelW - 8;
		int y0 = h - SLOT - 30;
		float time = ClientState.ticks + pt;

		// title
		boolean awakened = ClientState.awakenLeft != 0;
		String name = awakened ? "✦ " + type.awakenedName : "◆ " + type.displayName.toUpperCase();
		int nameCol = awakened && type == PowerType.FIRE ? 0x5CC8FF : type.color;
		g.pose().pushPose();
		g.pose().translate(x0, y0 - 42, 0);
		float ns = Math.min(1f, panelW / (float) font.width(name));
		g.pose().scale(ns, ns, 1);
		g.drawString(font, name, 0, 0, 0xFF000000 | nameCol, true);
		g.pose().popPose();
		if (!mc.player.getMainHandItem().isEmpty()) {
			String hint = "empty hand = M1";
			g.pose().pushPose();
			g.pose().translate(x0 + panelW, y0 - 49, 0);
			g.pose().scale(0.6f, 0.6f, 1);
			g.drawString(font, hint, -font.width(hint), 0, 0xFF9A9A9A, true);
			g.pose().popPose();
		}

		// awakening meter
		int ay = y0 - 22, aw = panelW;
		float mPulse = 0.5f + 0.5f * Mth.sin(time * 0.3f);
		String alabel;
		float afrac;
		int c1, c2;
		if (awakened) {
			afrac = ClientState.awakenLeft < 0 ? 1f : Mth.clamp(ClientState.awakenLeft / (float) PowerType.AWAKEN_TICKS, 0, 1);
			alabel = (ClientState.awakenLeft < 0 ? "AWAKENED ∞" : "AWAKENED " + (ClientState.awakenLeft / 20) + "s") + "  •  F to end";
			c1 = nameCol;
			c2 = 0xFFFFFF;
		} else {
			afrac = Mth.clamp(ClientState.meter / PowerType.MAX_METER, 0, 1);
			alabel = afrac >= 1f ? "G — AWAKEN" : "AWAKENING " + (int) ClientState.meter + "%";
			c1 = 0xC88A1A;
			c2 = 0xFFE27A;
		}
		boolean meterReady = !awakened && afrac >= 1f;
		if (meterReady || awakened) g.fill(x0 - 2, ay - 2, x0 + aw + 2, ay + 6, ScreenFx.argb(c1, 0.25f + mPulse * 0.35f));
		g.fill(x0 - 1, ay - 1, x0 + aw + 1, ay + 5, 0xC0000000);
		g.fill(x0, ay, x0 + aw, ay + 4, 0xFF201808);
		int af = (int) (aw * afrac);
		for (int i = 0; i < af; i++) {
			int col = Draw.mix(c1, c2, i / (float) aw);
			float shine = (float) Math.max(0, Math.sin((i + time * 4) * 0.12)) * (meterReady || awakened ? 0.7f : 0.35f);
			g.fill(x0 + i, ay, x0 + i + 1, ay + 4, 0xFF000000 | Draw.mix(col, 0xFFFFFF, shine));
		}
		g.pose().pushPose();
		g.pose().translate(x0 + aw / 2f, ay - 7.5f, 0);
		g.pose().scale(0.6f, 0.6f, 1);
		int lc = meterReady ? 0xFF000000 | Draw.mix(0xFFE27A, 0xFFFFFF, mPulse) : awakened ? 0xFF000000 | nameCol : 0xFFD8C090;
		g.drawString(font, alabel, -font.width(alabel) / 2, 0, lc, true);
		g.pose().popPose();

		// mana bar
		int by = y0 - 11, bw = panelW;
		float frac = Mth.clamp(ClientState.mana / PowerType.MAX_MANA, 0, 1);
		g.fill(x0 - 1, by - 1, x0 + bw + 1, by + 7, 0xC0000000);
		g.fill(x0, by, x0 + bw, by + 6, 0xFF1A1424);
		int fill = (int) (bw * frac);
		for (int i = 0; i < fill; i++) {
			float k = i / (float) bw;
			int col = Draw.mix(type.color, type.color2, k);
			float shine = (float) Math.max(0, Math.sin((i - time * 3) * 0.08)) * 0.5f;
			col = Draw.mix(col, 0xFFFFFF, shine * 0.6f);
			g.fill(x0 + i, by, x0 + i + 1, by + 6, 0xFF000000 | col);
		}
		g.fill(x0, by, x0 + fill, by + 1, 0x60FFFFFF);
		String mana = ClientState.infinite ? "∞" : (int) ClientState.mana + "";
		g.pose().pushPose();
		g.pose().translate(x0 + bw / 2f, by - 0.5f, 0);
		g.pose().scale(0.75f, 0.75f, 1);
		g.drawString(font, mana, -font.width(mana) / 2, 0, 0xFFFFFFFF, true);
		g.pose().popPose();

		// slots
		for (int i = 0; i < 5; i++) {
			int sx = x0 + i * (SLOT + GAP), sy = y0;
			int cd = ClientState.cooldowns[i];
			int max = Math.max(1, type.cooldown(i));
			boolean ready = cd <= 0;
			boolean affordable = ClientState.infinite || ClientState.mana >= PowerType.COST[i];
			boolean ult = i == 4;
			int border = ready && affordable ? type.color : 0x505050;
			if (ready && affordable && ult) {
				float pulse = 0.5f + 0.5f * Mth.sin(time * 0.25f);
				border = Draw.mix(type.color, 0xFFFFFF, pulse * 0.6f);
				g.fill(sx - 2, sy - 2, sx + SLOT + 2, sy + SLOT + 2, ScreenFx.argb(type.color, 0.25f + pulse * 0.25f));
			}
			g.fill(sx - 1, sy - 1, sx + SLOT + 1, sy + SLOT + 1, 0xFF000000 | border);
			g.fill(sx, sy, sx + SLOT, sy + SLOT, 0xE0120E18);
			g.fill(sx, sy, sx + SLOT, sy + SLOT / 2, ScreenFx.argb(type.color, 0.12f));
			if (!ready) {
				int ch = (int) (SLOT * cd / (float) max);
				g.fill(sx, sy + SLOT - ch, sx + SLOT, sy + SLOT, 0xB0000000);
			}
			String key = PowerType.KEYS[i];
			int keyCol = !affordable ? 0xFFFF5050 : ready ? 0xFFFFFFFF : 0xFF909090;
			g.drawString(font, key, sx + (SLOT - font.width(key)) / 2, sy + 4, keyCol, true);
			if (!ready) {
				String secs = String.format("%.1f", cd / 20f);
				g.pose().pushPose();
				g.pose().translate(sx + SLOT / 2f, sy + 15, 0);
				g.pose().scale(0.7f, 0.7f, 1);
				g.drawString(font, secs, -font.width(secs) / 2, 0, 0xFFFFD080, true);
				g.pose().popPose();
			} else if (!ClientState.infinite && PowerType.COST[i] > 0) {
				String cost = PowerType.COST[i] + "";
				g.pose().pushPose();
				g.pose().translate(sx + SLOT / 2f, sy + 15, 0);
				g.pose().scale(0.6f, 0.6f, 1);
				g.drawString(font, cost, -font.width(cost) / 2, 0, 0xFF7FB8FF, true);
				g.pose().popPose();
			}
			// ability name under the slot
			String an = type.abilityNames[i];
			float s = Math.min(0.5f, (SLOT + GAP - 1) / (float) font.width(an));
			g.pose().pushPose();
			g.pose().translate(sx + SLOT / 2f, sy + SLOT + 3, 0);
			g.pose().scale(s, s, 1);
			g.drawString(font, an, -font.width(an) / 2, 0, 0xFFC8C8C8, true);
			g.pose().popPose();
		}
	}
}
