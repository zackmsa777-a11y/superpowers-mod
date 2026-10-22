package com.superpowers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.superpowers.client.fx.HorrorFx;
import com.superpowers.client.model.Mesh;
import com.superpowers.client.model.TomeRenderer;
import com.superpowers.power.Fx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/** First-person arms: sideways claw swipes, and the Entity's own long arms when awakened. */
public final class FirstPerson {
	private FirstPerson() {}

	static ClientState.Anim sweepAnim() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return null;
		ClientState.Anim a = ClientState.anim(mc.player.getId());
		if (a == null) return null;
		return a.id() == Fx.Anim.SWEEP_R || a.id() == Fx.Anim.SWEEP_L || a.id() == Fx.Anim.SWEEP_BOTH ? a : null;
	}

	public static boolean sweeping() {
		return sweepAnim() != null;
	}

	static boolean creatureForm() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && HorrorFx.isEntityForm(mc.player);
	}

	/** Draw the off arm too: always as the creature, or while it is the one swiping. */
	public static boolean wantsOffArm() {
		if (creatureForm()) return true;
		ClientState.Anim a = sweepAnim();
		return a != null && a.id() != Fx.Anim.SWEEP_R;
	}

	/** Swings the whole arm around the camera: from its own side across to the other one. */
	public static void applySweep(PoseStack ps, HumanoidArm arm) {
		ClientState.Anim a = sweepAnim();
		if (a == null) return;
		boolean right = arm == HumanoidArm.RIGHT;
		if (right && a.id() == Fx.Anim.SWEEP_L) return;
		if (!right && a.id() == Fx.Anim.SWEEP_R) return;
		float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float k = Mth.clamp((ClientState.ticks + pt - a.start()) / a.duration(), 0, 1);
		float env = Math.min(1f, k / 0.12f) * Math.min(1f, (1 - k) / 0.25f);
		float e = Mth.clamp((k - 0.05f) / 0.6f, 0, 1);
		e = e * e * (3 - 2 * e);
		float h = right ? 1 : -1;
		float yaw = h * Mth.lerp(e, -40f, 85f) * env;
		ps.mulPose(Axis.YP.rotationDegrees(yaw));
		ps.translate(0, 0.12f * env, 0.05f * env);
		ps.mulPose(Axis.ZP.rotationDegrees(h * 18f * env));
	}

	/** The awakened Entity sees its own arms: long grey forearms ending in hooked claws. */
	public static boolean creatureArm(PoseStack ps, MultiBufferSource buffers, int light, float equip, HumanoidArm arm) {
		if (!creatureForm()) return false;
		float h = arm == HumanoidArm.RIGHT ? 1 : -1;
		float time = ClientState.ticks + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		ps.pushPose();
		applySweep(ps, arm);
		ps.translate(h * 0.42f, -0.4f - equip * 0.6f + 0.025f * Mth.sin(time * 0.09f + h), 0.0f);
		ps.mulPose(Axis.YP.rotationDegrees(h * 14f));
		ps.mulPose(Axis.XP.rotationDegrees(104f + 4f * Mth.sin(time * 0.07f + h)));
		ps.mulPose(Axis.ZP.rotationDegrees(h * 6f * Mth.sin(time * 0.05f)));
		ps.scale(h * 0.72f, 0.72f, 0.72f);
		TomeRenderer.emit(Mesh.get("entity_arm_l"), ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
		ps.popPose();
		return true;
	}
}
