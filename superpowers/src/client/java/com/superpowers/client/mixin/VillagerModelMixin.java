package com.superpowers.client.mixin;

import com.superpowers.client.ClientState;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Villagers (and witches, traders) lose their heads too. */
@Mixin(VillagerModel.class)
public abstract class VillagerModelMixin {
	@Shadow @Final private ModelPart root;
	@Shadow @Final private ModelPart head;
	@Unique
	private boolean superpowers$touched;

	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", at = @At("TAIL"))
	private void superpowers$gore(Entity entity, float a, float b, float c, float d, float e, CallbackInfo ci) {
		boolean headless = ClientState.isHeadless(entity.getId());
		boolean headOnly = ClientState.isHeadOnly(entity.getId());
		if (headless || headOnly) {
			head.visible = !headless;
			for (String part : new String[]{"body", "arms", "right_leg", "left_leg"}) {
				if (root.hasChild(part)) root.getChild(part).visible = !headOnly;
			}
			superpowers$touched = true;
		} else if (superpowers$touched) {
			superpowers$touched = false;
			head.visible = true;
			for (String part : new String[]{"body", "arms", "right_leg", "left_leg"}) {
				if (root.hasChild(part)) root.getChild(part).visible = true;
			}
		}
	}
}
