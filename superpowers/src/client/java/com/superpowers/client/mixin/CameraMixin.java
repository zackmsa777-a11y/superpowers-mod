package com.superpowers.client.mixin;

import com.superpowers.client.Cutscene;
import com.superpowers.client.ScreenFx;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cinematic camera for ultimates + camera shake. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow private boolean detached;

	@Shadow protected abstract void setRotation(float yRot, float xRot);

	@Shadow protected abstract void setPosition(Vec3 pos);

	@Shadow public abstract Vec3 getPosition();

	@Shadow public abstract float getXRot();

	@Shadow public abstract float getYRot();

	@Inject(method = "setup", at = @At("TAIL"))
	private void superpowers$cinematic(BlockGetter level, Entity entity, boolean thirdPerson, boolean mirrored, float pt, CallbackInfo ci) {
		double[] cam = Cutscene.camera(getPosition(), getYRot(), getXRot(), pt);
		if (cam != null) {
			setPosition(new Vec3(cam[0], cam[1], cam[2]));
			setRotation((float) cam[3], (float) cam[4]);
			this.detached = true;
		}
		float[] shake = ScreenFx.shakeOffset(pt);
		if (shake != null) setRotation(getYRot() + shake[0], getXRot() + shake[1]);
	}
}
