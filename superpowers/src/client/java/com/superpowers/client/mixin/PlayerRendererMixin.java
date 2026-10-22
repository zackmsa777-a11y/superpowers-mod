package com.superpowers.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.superpowers.client.ClientState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leans the body into the flight direction (God goes fully horizontal, superman style). */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("TAIL"))
	private void superpowers$flightLean(AbstractClientPlayer player, PoseStack pose, float bob, float bodyRot, float pt, float scale, CallbackInfo ci) {
		if (player.isFallFlying() || player.getSwimAmount(pt) > 0) return;
		float tilt = ClientState.flightTilt(player, pt);
		if (tilt < 0.01f) return;
		pose.translate(0, 0.9, 0);
		pose.mulPose(Axis.XP.rotationDegrees(-tilt));
		pose.translate(0, -0.9, 0);
	}
}
