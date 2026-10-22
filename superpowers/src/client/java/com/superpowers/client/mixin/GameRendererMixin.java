package com.superpowers.client.mixin;

import com.superpowers.client.Cutscene;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide the first-person hand while the cinematic camera is active. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void superpowers$hideHand(CallbackInfo ci) {
		if (Cutscene.active()) ci.cancel();
	}
}
