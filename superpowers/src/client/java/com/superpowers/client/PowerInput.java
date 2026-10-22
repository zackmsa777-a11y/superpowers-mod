package com.superpowers.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.superpowers.net.Net;
import com.superpowers.power.PowerType;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

/** Z X C V keys and the empty-hand M1. Hold sneak to punch/mine normally. */
public final class PowerInput {
	private PowerInput() {}

	static KeyMapping[] keys;
	private static int m1Cooldown;

	public static void register() {
		keys = new KeyMapping[]{
				KeyBindingHelper.registerKeyBinding(new KeyMapping("key.superpowers.z", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "category.superpowers")),
				KeyBindingHelper.registerKeyBinding(new KeyMapping("key.superpowers.x", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "category.superpowers")),
				KeyBindingHelper.registerKeyBinding(new KeyMapping("key.superpowers.c", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "category.superpowers")),
				KeyBindingHelper.registerKeyBinding(new KeyMapping("key.superpowers.v", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.superpowers")),
		};
		awakenKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.superpowers.awaken", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.superpowers"));
	}

	static KeyMapping awakenKey;

	/** Start of tick, before vanilla handles keys: F ends awakening instead of swapping hands. */
	public static void preTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null || ClientState.awakenLeft == 0 || Cutscene.active()) return;
		boolean stop = false;
		while (mc.options.keySwapOffhand.consumeClick()) stop = true;
		if (stop) ClientPlayNetworking.send(new Net.Awaken(0));
	}

	private static final boolean[] wasDown = new boolean[4];

	public static void tick() {
		if (m1Cooldown > 0) m1Cooldown--;
		Minecraft mc = Minecraft.getInstance();
		long window = mc.getWindow().getWindow();
		for (int i = 0; i < keys.length; i++) {
			boolean clicked = false;
			while (keys[i].consumeClick()) clicked = true;
			// Vanilla binds X/C to the creative toolbar activators and only one mapping per key receives
			// clicks, so also poll the bound key directly.
			InputConstants.Key k = KeyBindingHelper.getBoundKeyOf(keys[i]);
			boolean down = mc.screen == null && k.getType() == InputConstants.Type.KEYSYM && k.getValue() > 0
					&& InputConstants.isKeyDown(window, k.getValue());
			boolean edge = down && !wasDown[i];
			wasDown[i] = down;
			if (clicked || edge) use(i + 1);
		}
		while (awakenKey.consumeClick()) {
			if (mc.player != null && ClientState.power != PowerType.NONE && !Cutscene.active() && ClientState.awakenLeft == 0) {
				ClientPlayNetworking.send(new Net.Awaken(1));
			}
		}
	}

	public static void use(int slot) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || ClientState.power == PowerType.NONE || Cutscene.active()) return;
		if (ClientState.cooldowns[slot] > 0) return;
		ClientPlayNetworking.send(new Net.Ability(slot));
	}

	/** True when left click should be an M1 power attack instead of vanilla attack/mining. */
	public static boolean wantsM1() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || ClientState.power == PowerType.NONE || p.isSpectator()) return false;
		if (Cutscene.active()) return true;
		return p.getMainHandItem().isEmpty() && !p.isShiftKeyDown();
	}

	public static void tryM1() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || Cutscene.active() || m1Cooldown > 0) return;
		m1Cooldown = 5;
		ClientPlayNetworking.send(new Net.Ability(0));
		mc.player.swing(InteractionHand.MAIN_HAND);
	}
}
