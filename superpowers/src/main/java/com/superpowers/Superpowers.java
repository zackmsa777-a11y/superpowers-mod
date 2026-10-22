package com.superpowers;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.superpowers.net.Net;
import com.superpowers.power.Awakening;
import com.superpowers.power.Destruction;
import com.superpowers.power.Hunt;
import com.superpowers.power.PowerManager;
import com.superpowers.power.PowerType;
import com.superpowers.power.Tasks;
import com.superpowers.power.powers.GodPower;
import com.superpowers.registry.ModEntities;
import com.superpowers.registry.ModItems;
import com.superpowers.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class Superpowers implements ModInitializer {
	public static final String MODID = "superpowers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

	@Override
	public void onInitialize() {
		ModSounds.init();
		ModItems.init();
		ModEntities.init();
		Net.register();
		Destruction.init();
		PowerManager.POWER.identifier(); // force attachment registration
		Awakening.METER.identifier();

		ServerPlayNetworking.registerGlobalReceiver(Net.Ability.TYPE, (payload, ctx) -> PowerManager.use(ctx.player(), payload.slot()));
		ServerPlayNetworking.registerGlobalReceiver(Net.SelectPower.TYPE, (payload, ctx) -> {
			ServerPlayer p = ctx.player();
			if (!p.isCreative() && !p.getMainHandItem().is(ModItems.POWER_TOME) && !p.getOffhandItem().is(ModItems.POWER_TOME)) return;
			PowerManager.set(p, PowerType.byOrdinal(payload.power()));
		});

		ServerPlayNetworking.registerGlobalReceiver(Net.Awaken.TYPE, (payload, ctx) -> {
			if (payload.action() == 1) PowerManager.awaken(ctx.player());
			else PowerManager.stopAwakening(ctx.player());
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(Awakening::onDeath);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Tasks.tick();
			PowerManager.tick(server);
			Destruction.tick();
			Hunt.tick(server);
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register(Hunt::allowDeath);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((e, world) -> Hunt.onLoad(e));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PowerManager.onJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PowerManager.onLeave(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			GodPower.unfreezeAll();
			Tasks.clear();
			Destruction.clear();
			Awakening.clear();
			Hunt.clear();
			PowerManager.clear();
		});
		// the caster can't be hurt while their ultimate cutscene plays
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer sp && (PowerManager.inCutscene(sp) || Hunt.isEntityForm(sp))));

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
				Commands.literal("power")
						.then(Commands.literal("book").executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							p.getInventory().add(new ItemStack(ModItems.POWER_TOME));
							return 1;
						}))
						.then(Commands.literal("set").requires(src -> src.hasPermission(2))
								.then(Commands.argument("power", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(PowerType.values()).map(t -> t.id), b))
										.executes(ctx -> {
											ServerPlayer p = ctx.getSource().getPlayerOrException();
											PowerType t = PowerType.byId(StringArgumentType.getString(ctx, "power"));
											PowerManager.set(p, t);
											ctx.getSource().sendSuccess(() -> Component.literal("Power set to " + t.displayName), false);
											return 1;
										})))
						.then(Commands.literal("meter").requires(src -> src.hasPermission(2))
								.then(Commands.argument("percent", FloatArgumentType.floatArg(0, 100)).executes(ctx -> {
									ServerPlayer p = ctx.getSource().getPlayerOrException();
									float v = FloatArgumentType.getFloat(ctx, "percent");
									Awakening.setMeter(p, 0);
									if (v >= 100) Awakening.add(p, 100);
									else Awakening.setMeter(p, v);
									ctx.getSource().sendSuccess(() -> Component.literal("Awakening meter set to " + (int) v + "%"), false);
									return 1;
								})))
						.then(Commands.literal("clear").executes(ctx -> {
							PowerManager.set(ctx.getSource().getPlayerOrException(), PowerType.NONE);
							return 1;
						}))));

		LOGGER.info("Superpowers loaded: Fire, Space, Angel, Demon, God, Entity 666");
	}
}
