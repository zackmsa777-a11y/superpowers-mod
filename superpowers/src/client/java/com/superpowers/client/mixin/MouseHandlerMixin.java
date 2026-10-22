package com.superpowers.client.mixin;

import com.superpowers.client.Cutscene;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse look is locked during the cutscene (deltas are discarded right after). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void superpowers$lockLook(double movementTime, CallbackInfo ci) {
		if (Cutscene.active()) ci.cancel();
	}
}
