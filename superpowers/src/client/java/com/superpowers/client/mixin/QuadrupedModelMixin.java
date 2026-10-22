package com.superpowers.client.mixin;

import com.superpowers.client.ClientState;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cows, pigs, sheep... headless corpses. */
@Mixin(QuadrupedModel.class)
public abstract class QuadrupedModelMixin {
	@Shadow @Final protected ModelPart head;
	@Unique
	private boolean superpowers$touched;

	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", at = @At("TAIL"))
	private void superpowers$gore(Entity entity, float a, float b, float c, float d, float e, CallbackInfo ci) {
		if (ClientState.isHeadless(entity.getId())) {
			head.visible = false;
			superpowers$touched = true;
		} else if (superpowers$touched) {
			superpowers$touched = false;
			head.visible = true;
		}
	}
}
