package com.superpowers.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.superpowers.client.ScreenFx;
import com.superpowers.client.fx.Draw;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ultimates tint the fog/horizon (red hellscape, void, golden light...). */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Shadow private static float fogRed;
	@Shadow private static float fogGreen;
	@Shadow private static float fogBlue;

	@Inject(method = "setupColor", at = @At("TAIL"))
	private static void superpowers$tint(Camera camera, float pt, ClientLevel level, int renderDistance, float darkenAmount, CallbackInfo ci) {
		float k = ScreenFx.tintAmount(pt);
		if (k <= 0) return;
		int c = ScreenFx.tintColor();
		fogRed = Mth.lerp(k, fogRed, Draw.r(c));
		fogGreen = Mth.lerp(k, fogGreen, Draw.g(c));
		fogBlue = Mth.lerp(k, fogBlue, Draw.b(c));
		RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
	}
}
