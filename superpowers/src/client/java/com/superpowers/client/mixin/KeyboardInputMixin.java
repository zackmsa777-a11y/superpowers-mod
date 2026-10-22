package com.superpowers.client.mixin;

import com.superpowers.client.Cutscene;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No walking around while the ultimate cutscene plays. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void superpowers$freeze(boolean slow, float slowFactor, CallbackInfo ci) {
		if (!Cutscene.active()) return;
		Input in = (Input) (Object) this;
		in.forwardImpulse = 0;
		in.leftImpulse = 0;
		in.up = in.down = in.left = in.right = false;
		in.jumping = false;
		in.shiftKeyDown = false;
	}
}
