package com.superpowers.client.mixin;

import com.superpowers.client.ClientState;
import com.superpowers.client.PlayerAnims;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ability poses on top of vanilla animation, plus torn-off heads (headless bodies / heads on their own). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
	@Unique
	private boolean superpowers$touched;

	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void superpowers$pose(T entity, float limbSwing, float limbAmount, float age, float headYaw, float headPitch, CallbackInfo ci) {
		HumanoidModel<?> m = (HumanoidModel<?>) (Object) this;
		if (entity instanceof Player) PlayerAnims.apply(m, entity);
		boolean headless = ClientState.isHeadless(entity.getId());
		boolean headOnly = ClientState.isHeadOnly(entity.getId());
		if (headless || headOnly) {
			m.head.visible = !headless;
			m.hat.visible = !headless && m.hat.visible;
			boolean body = !headOnly;
			m.body.visible = body;
			m.rightArm.visible = body;
			m.leftArm.visible = body;
			m.rightLeg.visible = body;
			m.leftLeg.visible = body;
			if (m instanceof PlayerModel<?> pm) {
				pm.jacket.visible &= body;
				pm.leftSleeve.visible &= body;
				pm.rightSleeve.visible &= body;
				pm.leftPants.visible &= body;
				pm.rightPants.visible &= body;
			}
			superpowers$touched = true;
		} else if (superpowers$touched) {
			// models are shared between entities: put everything back (players reset their own parts each frame)
			superpowers$touched = false;
			if (!(entity instanceof Player)) {
				m.head.visible = m.hat.visible = true;
				m.body.visible = m.rightArm.visible = m.leftArm.visible = m.rightLeg.visible = m.leftLeg.visible = true;
			}
		}
	}
}
