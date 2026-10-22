package com.superpowers.client.mixin;

import com.superpowers.client.Cutscene;
import com.superpowers.client.ScreenFx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During an ultimate the HUD is replaced by letterbox bars and the title card. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void superpowers$cinematicHud(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
		if (Cutscene.active()) {
			float pt = dt.getGameTimeDeltaPartialTick(false);
			ScreenFx.renderOverlay(g, pt);
			Cutscene.renderOverlay(g, pt);
			com.superpowers.client.fx.HorrorFx.renderOverlay(g, pt);
			ci.cancel();
		}
	}

	/** One health bar instead of five rows of hearts. */
	@Inject(method = "renderHearts", at = @At("HEAD"), cancellable = true)
	private void superpowers$healthBar(GuiGraphics g, net.minecraft.world.entity.player.Player player, int x, int y, int lines, int regen,
									   float maxHealth, int health, int displayHealth, int absorption, boolean blink, CallbackInfo ci) {
		if (com.superpowers.client.HealthBar.active()) {
			com.superpowers.client.HealthBar.render(g, x, y, player);
			ci.cancel();
		}
	}

	/** Keep armor/air rows where they'd be with 20 health. */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "renderPlayerHealth", at = @At("STORE"), ordinal = 0)
	private float superpowers$oneRow(float maxHealth) {
		return com.superpowers.client.HealthBar.active() ? Math.min(maxHealth, 20f) : maxHealth;
	}
}
