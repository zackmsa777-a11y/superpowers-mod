package com.superpowers.client.mixin;

import com.superpowers.client.ScreenFx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sky and clouds follow the ultimate tint. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
	private void superpowers$sky(Vec3 pos, float pt, CallbackInfoReturnable<Vec3> cir) {
		cir.setReturnValue(ScreenFx.applyTint(cir.getReturnValue(), pt));
	}

	@Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
	private void superpowers$clouds(float pt, CallbackInfoReturnable<Vec3> cir) {
		cir.setReturnValue(ScreenFx.applyTint(cir.getReturnValue(), pt));
	}
}
