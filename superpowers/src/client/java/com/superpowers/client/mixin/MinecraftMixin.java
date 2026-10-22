package com.superpowers.client.mixin;

import com.superpowers.client.PowerInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Left click with an empty hand becomes the power M1 combo. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void superpowers$m1(CallbackInfoReturnable<Boolean> cir) {
		if (PowerInput.wantsM1()) {
			PowerInput.tryM1();
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void superpowers$m1Hold(boolean leftClick, CallbackInfo ci) {
		if (PowerInput.wantsM1()) {
			if (leftClick) PowerInput.tryM1();
			ci.cancel();
		}
	}
}
