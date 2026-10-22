package com.superpowers.client;

import com.superpowers.client.fx.Debris;
import com.superpowers.client.fx.FxHandler;
import com.superpowers.client.fx.FxManager;
import com.superpowers.client.screen.PowerBookScreen;
import com.superpowers.net.Net;
import com.superpowers.power.PowerType;
import com.superpowers.client.model.TomeRenderer;
import com.superpowers.registry.ModEntities;
import com.superpowers.registry.ModItems;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;

public class SuperpowersClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		PowerInput.register();
		EntityRendererRegistry.register(ModEntities.PROJECTILE, NoopRenderer::new);
		BuiltinItemRendererRegistry.INSTANCE.register(ModItems.POWER_TOME, new TomeRenderer());

		ClientPlayNetworking.registerGlobalReceiver(Net.Sync.TYPE, (p, ctx) -> {
			ClientState.power = PowerType.byOrdinal(p.power());
			ClientState.mana = p.mana();
			ClientState.infinite = p.infinite();
			System.arraycopy(p.cooldowns(), 0, ClientState.cooldowns, 0, 5);
			ClientState.meter = p.meter();
			ClientState.awakenLeft = p.awakenLeft();
		});
		ClientPlayNetworking.registerGlobalReceiver(Net.PowerOf.TYPE, (p, ctx) -> {
			ClientState.setPowerOf(p.entityId(), PowerType.byOrdinal(p.power()));
			ClientState.setAwakened(p.entityId(), p.awakened());
			ClientState.setFlying(p.entityId(), p.flying());
		});
		ClientPlayNetworking.registerGlobalReceiver(Net.Debris.TYPE, (p, ctx) -> Debris.handle(p));
		ClientPlayNetworking.registerGlobalReceiver(Net.Effect.TYPE, (p, ctx) -> FxHandler.handle(p));
		ClientPlayNetworking.registerGlobalReceiver(Net.Anim.TYPE, (p, ctx) -> ClientState.playAnim(p.entityId(), p.anim(), p.duration()));
		ClientPlayNetworking.registerGlobalReceiver(Net.Cutscene.TYPE, (p, ctx) -> Cutscene.start(p));
		ClientPlayNetworking.registerGlobalReceiver(Net.OpenBook.TYPE, (p, ctx) -> Minecraft.getInstance().setScreen(new PowerBookScreen()));

		WorldRenderEvents.AFTER_TRANSLUCENT.register(FxManager::render);
		HudRenderCallback.EVENT.register((g, dt) -> PowerHud.render(g, dt.getGameTimeDeltaPartialTick(false)));

		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (mc.level != null && !mc.isPaused()) {
				Cutscene.tick();
				PowerInput.preTick();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level != null && !mc.isPaused()) {
				ClientState.tick();
				ScreenFx.tick();
				FxManager.tick();
				PowerInput.tick();
			}
			AutoTest.tick(mc);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			ClientState.reset();
			ScreenFx.reset();
			FxManager.clear();
			Cutscene.stop();
		});
	}
}
