package com.superpowers.client.screen;

import com.superpowers.client.ClientState;
import com.superpowers.client.ScreenFx;
import com.superpowers.client.fx.Draw;
import com.superpowers.net.Net;
import com.superpowers.power.PowerType;
import com.superpowers.registry.ModSounds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.List;

/** The SP Book: pick a power on the left page, read about it on the right, then AWAKEN. */
public class PowerBookScreen extends Screen {
	static final PowerType[] POWERS = {PowerType.FIRE, PowerType.SPACE, PowerType.ANGEL, PowerType.DEMON, PowerType.GOD, PowerType.ENTITY};

	private PowerType selected;
	private int openTicks;
	private float[][] stars;

	private int bx, by, bw, bh;

	public PowerBookScreen() {
		super(Component.literal("SP Book"));
		selected = ClientState.power != PowerType.NONE ? ClientState.power : PowerType.FIRE;
	}

	public PowerType selected() {
		return selected;
	}

	public void select(PowerType t) {
		if (t == selected) return;
		selected = t;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2f, 1f));
	}

	@Override
	protected void init() {
		bw = Math.min(440, width - 20);
		bh = Math.min(250, height - 20);
		bx = (width - bw) / 2;
		by = (height - bh) / 2;
		if (stars == null) {
			RandomSource r = RandomSource.create(42);
			stars = new float[140][3];
			for (float[] s : stars) {
				s[0] = r.nextFloat();
				s[1] = r.nextFloat();
				s[2] = r.nextFloat() * 10;
			}
		}
	}

	@Override
	public void tick() {
		openTicks++;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	float time(float pt) {
		return openTicks + pt;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
		float t = time(pt);
		g.fillGradient(0, 0, width, height, 0xE0080412, 0xF0150828);
		for (float[] s : stars) {
			float tw = 0.4f + 0.6f * (0.5f + 0.5f * Mth.sin(t * 0.15f + s[2]));
			int x = (int) (s[0] * width), y = (int) (s[1] * height);
			int a = (int) (tw * 200);
			g.fill(x, y, x + 1, y + 1, (a << 24) | 0xFFFFFF);
			if (s[2] > 8.5f) {
				g.fill(x - 1, y, x + 2, y + 1, ((a / 3) << 24) | 0xFFFFFF);
				g.fill(x, y - 1, x + 1, y + 2, ((a / 3) << 24) | 0xFFFFFF);
			}
		}
		ScreenFx.vignette(g, width, height, selected.color, 0.6f);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
		renderBackground(g, mouseX, mouseY, pt);
		float t = time(pt);
		float open = Math.min(1f, t / 7f);
		float ease = 1 - (1 - open) * (1 - open) * (1 - open);
		float sc = 0.86f + 0.14f * ease;
		g.pose().pushPose();
		g.pose().translate(width / 2f, height / 2f, 0);
		g.pose().scale(sc, sc, 1);
		g.pose().translate(-width / 2f, -height / 2f, 0);
		drawBook(g, mouseX, mouseY, t, ease);
		g.pose().popPose();
	}

	private void drawBook(GuiGraphics g, int mx, int my, float t, float alpha) {
		Font font = this.font;
		int col = selected.color;
		// cover + glow edge
		for (int i = 6; i > 0; i--) {
			g.fill(bx - i, by - i, bx + bw + i, by + bh + i, ScreenFx.argb(col, 0.05f * (7 - i) * alpha));
		}
		g.fill(bx - 3, by - 3, bx + bw + 3, by + bh + 3, 0xFF2A1238);
		g.fill(bx, by, bx + bw, by + bh, 0xFF1B1024);
		int mid = bx + bw / 2;
		// pages
		g.fillGradient(bx + 4, by + 4, mid - 2, by + bh - 4, 0xFF2A1D38, 0xFF1E142A);
		g.fillGradient(mid + 2, by + 4, bx + bw - 4, by + bh - 4, 0xFF2A1D38, 0xFF1E142A);
		g.fill(mid - 2, by + 2, mid + 2, by + bh - 2, 0xFF0E0814);
		g.fill(mid - 1, by + 2, mid, by + bh - 2, ScreenFx.argb(col, 0.6f));
		// gold corners
		int gold = 0xFFE8B84A;
		for (int[] c : new int[][]{{bx, by}, {bx + bw - 12, by}, {bx, by + bh - 12}, {bx + bw - 12, by + bh - 12}}) {
			g.fill(c[0], c[1], c[0] + 12, c[1] + 2, gold);
			g.fill(c[0], c[1] + 10, c[0] + 12, c[1] + 12, gold);
			g.fill(c[0], c[1], c[0] + 2, c[1] + 12, gold);
			g.fill(c[0] + 10, c[1], c[0] + 12, c[1] + 12, gold);
		}

		// ------------------------------------------------ left page: power list
		int lx = bx + 12, ly = by + 12, lw = mid - bx - 22;
		String head = "S P   B O O K";
		g.drawString(font, head, lx + (lw - font.width(head)) / 2 + 6, ly, 0xFFE8B84A, true);
		Emblem.renderBook(g, lx + (lw - font.width(head)) / 2f - 6, ly + 3, 11, t);
		g.fill(lx + 10, ly + 11, lx + lw - 10, ly + 12, 0x80E8B84A);
		int rowH = Math.min(38, (bh - 40) / POWERS.length);
		for (int i = 0; i < POWERS.length; i++) {
			PowerType p = POWERS[i];
			int ry = ly + 18 + i * rowH;
			boolean hover = mx >= lx && mx < lx + lw && my >= ry && my < ry + rowH - 3;
			boolean sel = p == selected;
			int bg = sel ? ScreenFx.argb(p.color, 0.28f) : hover ? ScreenFx.argb(p.color, 0.14f) : 0x40000000;
			g.fill(lx, ry, lx + lw, ry + rowH - 3, bg);
			if (sel) {
				g.fill(lx, ry, lx + 2, ry + rowH - 3, 0xFF000000 | p.color);
				g.fill(lx, ry, lx + lw, ry + 1, ScreenFx.argb(p.color, 0.7f));
				g.fill(lx, ry + rowH - 4, lx + lw, ry + rowH - 3, ScreenFx.argb(p.color, 0.7f));
			}
			Emblem.render(g, p, lx + 18, ry + (rowH - 3) / 2f, (rowH - 3) * 0.3f, t + i * 13, alpha);
			int nameCol = 0xFF000000 | p.color;
			g.drawString(font, p.displayName.toUpperCase(), lx + 38, ry + 5, nameCol, true);
			String ult = "Ultimate: " + p.abilityNames[4];
			g.pose().pushPose();
			g.pose().translate(lx + 38, ry + 17, 0);
			g.pose().scale(0.75f, 0.75f, 1);
			g.drawString(font, ult, 0, 0, 0xFFB8A8C8, false);
			g.pose().popPose();
			if (ClientState.power == p) {
				String act = "ACTIVE";
				g.drawString(font, act, lx + lw - font.width(act) - 4, ry + 5, 0xFF7CFF8A, true);
			}
		}

		// ------------------------------------------------ right page: details
		int rx = mid + 12, rw = bx + bw - 12 - rx;
		PowerType p = selected;
		float ex = rx + rw / 2f, ey = by + 44;
		Emblem.render(g, p, ex, ey, 22, t * 1.2f, alpha);
		String title = p.displayName.toUpperCase();
		g.pose().pushPose();
		g.pose().translate(rx + rw / 2f, by + 76, 0);
		g.pose().scale(2f, 2f, 1);
		g.drawString(font, title, -font.width(title) / 2, 0, 0xFF000000 | p.color, true);
		g.pose().popPose();
		int ty = by + 96;
		List<FormattedCharSequence> lines = font.split(Component.literal(p.description), (int) (rw / 0.75f));
		g.pose().pushPose();
		g.pose().translate(rx, ty, 0);
		g.pose().scale(0.75f, 0.75f, 1);
		for (int i = 0; i < lines.size(); i++) {
			g.drawString(font, lines.get(i), 0, i * 10, 0xFFD8CCE8, false);
		}
		g.pose().popPose();
		ty += lines.size() * 8 + 3;
		String awk = "✦ AWAKENING: " + p.awakenedName + " — " + p.meterHint + "  ✦ Awakened: double-tap SPACE to fly";
		List<FormattedCharSequence> awl = font.split(Component.literal(awk), (int) (rw / 0.6f));
		g.pose().pushPose();
		g.pose().translate(rx, ty, 0);
		g.pose().scale(0.6f, 0.6f, 1);
		for (int i = 0; i < awl.size(); i++) g.drawString(font, awl.get(i), 0, i * 10, 0xFFFFD27A, false);
		g.pose().popPose();
		ty += awl.size() * 6 + 4;
		int listBottom = by + bh - 18 - 14;
		int rowStep = Math.max(11, Math.min(17, (listBottom - ty) / 5));
		boolean compact = rowStep < 15;
		for (int i = 0; i < 5; i++) {
			int rowY = ty + i * rowStep;
			String key = PowerType.KEYS[i];
			int kc = i == 4 ? 0xFF000000 | p.color : 0xFFFFFFFF;
			int boxH = compact ? rowStep - 1 : 14;
			g.fill(rx, rowY, rx + 16, rowY + boxH, i == 4 ? ScreenFx.argb(p.color, 0.35f) : 0x60000000);
			g.drawString(font, key, rx + 8 - font.width(key) / 2, rowY + (boxH - 8) / 2 + 1, kc, true);
			int nameCol = i == 4 ? 0xFF000000 | p.color : 0xFFFFFFFF;
			if (compact) {
				g.drawString(font, p.abilityNames[i], rx + 20, rowY + (boxH - 8) / 2 + 1, nameCol, false);
				continue;
			}
			g.drawString(font, p.abilityNames[i], rx + 20, rowY, nameCol, false);
			g.pose().pushPose();
			g.pose().translate(rx + 20, rowY + 9, 0);
			g.pose().scale(0.6f, 0.6f, 1);
			g.drawString(font, p.abilityDescs[i], 0, 0, 0xFFA898B8, false);
			g.pose().popPose();
		}

		// AWAKEN button
		int bW = 92, bH = 18;
		int bX = rx + (rw - bW) / 2, bY = by + bh - bH - 10;
		boolean active = ClientState.power == p;
		boolean hb = mx >= bX && mx < bX + bW && my >= bY && my < bY + bH;
		float pulse = 0.5f + 0.5f * Mth.sin(t * 0.2f);
		int btnCol = active ? 0x3A3A3A : p.color;
		g.fill(bX - 2, bY - 2, bX + bW + 2, bY + bH + 2, ScreenFx.argb(btnCol, active ? 0.3f : 0.25f + pulse * 0.25f));
		g.fill(bX, bY, bX + bW, bY + bH, hb && !active ? 0xFF000000 | Draw.mix(p.color, 0xFFFFFF, 0.25f) : 0xFF000000 | Draw.scale(btnCol, 0.55f));
		String label = active ? "ACTIVE" : "AWAKEN";
		g.drawString(font, label, bX + (bW - font.width(label)) / 2, bY + 5, 0xFFFFFFFF, true);
		if (ClientState.power != PowerType.NONE) {
			String rel = "release power";
			int relX = bx + 12, relY = by + bh - 16;
			boolean hr = mx >= relX && mx < relX + font.width(rel) && my >= relY && my < relY + 9;
			g.drawString(font, rel, relX, relY, hr ? 0xFFFF8080 : 0xFF8A7A9A, false);
		}
		String tip = "Empty hand = M1  •  Sneak to punch normally  •  G awaken, F end";
		g.pose().pushPose();
		g.pose().translate(bx + bw / 2f, Math.min(by + bh + 8, height - 8), 0);
		g.pose().scale(0.7f, 0.7f, 1);
		g.drawString(font, tip, -font.width(tip) / 2, 0, 0xFF9A8AAA, false);
		g.pose().popPose();
	}

	/** Maps a screen-space click back into the (scaled-open) book. */
	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0) return super.mouseClicked(mx, my, button);
		int mid = bx + bw / 2;
		int lx = bx + 12, ly = by + 12, lw = mid - bx - 22;
		int rowH = Math.min(38, (bh - 40) / POWERS.length);
		for (int i = 0; i < POWERS.length; i++) {
			int ry = ly + 18 + i * rowH;
			if (mx >= lx && mx < lx + lw && my >= ry && my < ry + rowH - 3) {
				select(POWERS[i]);
				return true;
			}
		}
		int rx = mid + 12, rw = bx + bw - 12 - rx;
		int bW = 92, bH = 18;
		int bX = rx + (rw - bW) / 2, bY = by + bh - bH - 10;
		if (mx >= bX && mx < bX + bW && my >= bY && my < bY + bH && ClientState.power != selected) {
			awaken(selected);
			return true;
		}
		if (ClientState.power != PowerType.NONE) {
			int relX = bx + 12, relY = by + bh - 16;
			if (mx >= relX && mx < relX + font.width("release power") && my >= relY && my < relY + 9) {
				ClientPlayNetworking.send(new Net.SelectPower(PowerType.NONE.ordinal()));
				onClose();
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	public void awaken(PowerType t) {
		ClientPlayNetworking.send(new Net.SelectPower(t.ordinal()));
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SELECT, 1f, 0.8f));
		onClose();
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		// arrow keys / W S to browse, Enter to awaken
		int idx = java.util.Arrays.asList(POWERS).indexOf(selected);
		if (key == 264 || key == 83) {
			select(POWERS[(idx + 1) % POWERS.length]);
			return true;
		}
		if (key == 265 || key == 87) {
			select(POWERS[(idx + POWERS.length - 1) % POWERS.length]);
			return true;
		}
		if (key == 257 || key == 335) {
			if (ClientState.power != selected) awaken(selected);
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}
}
