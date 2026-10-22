package com.superpowers.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.superpowers.client.FirstPerson;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First person: claw swipes sweep across the screen, and the awakened Entity sees its own long arms. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
	private void superpowers$arm(PoseStack ps, MultiBufferSource buffers, int light, float equip, float swing, HumanoidArm arm, CallbackInfo ci) {
		if (FirstPerson.creatureArm(ps, buffers, light, equip, arm)) {
			ci.cancel();
			return;
		}
		FirstPerson.applySweep(ps, arm);
	}

	@ModifyVariable(method = "renderPlayerArm", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private float superpowers$noPunch(float swing) {
		return FirstPerson.sweeping() ? 0f : swing;
	}

	@Inject(method = "renderArmWithItem", at = @At("HEAD"))
	private void superpowers$offArm(AbstractClientPlayer player, float pt, float pitch, InteractionHand hand, float swing, ItemStack stack,
									float equip, PoseStack ps, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (hand == InteractionHand.OFF_HAND && stack.isEmpty() && !player.isInvisible() && !player.isScoping() && FirstPerson.wantsOffArm()) {
			ps.pushPose();
			((ItemInHandRendererInvoker) (Object) this).superpowers$renderPlayerArm(ps, buffers, light, equip, 0f, player.getMainArm().getOpposite());
			ps.popPose();
		}
	}
}
