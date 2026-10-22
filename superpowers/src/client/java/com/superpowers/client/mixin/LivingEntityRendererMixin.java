package com.superpowers.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.superpowers.client.ClientState;
import com.superpowers.client.fx.HorrorFx;
import com.superpowers.power.PowerType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides devoured bodies and players who have become Entity 666 (the creature rig is drawn instead). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
	private void superpowers$noPropNames(LivingEntity e, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (ClientState.isProp(e.getId())) cir.setReturnValue(false);
	}

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), cancellable = true)
	private void superpowers$hide(LivingEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (ClientState.isHidden(e.getId())) {
			ci.cancel();
			return;
		}
		if (e instanceof Player p && ClientState.powerOf(p) == PowerType.ENTITY) {
			if (HorrorFx.isEntityForm(p)) {
				ci.cancel();
				return;
			}
			// the human disguise glitches out for a frame now and then
			long t = ClientState.ticks + p.getId() * 37L;
			if (t % 113 < 2 || t % 61 == 0) ci.cancel();
		}
	}
}
