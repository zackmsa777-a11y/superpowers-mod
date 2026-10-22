package com.superpowers.client;

import com.superpowers.Superpowers;
import com.superpowers.client.fx.FxManager;
import com.superpowers.client.screen.PowerBookScreen;
import com.superpowers.net.Net;
import com.superpowers.power.PowerType;
import com.superpowers.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only smoke test (enabled with -Dsuperpowers.autotest=1): makes a flat creative world,
 * awakens every power, fires every ability and screenshots the results, then quits.
 */
public final class AutoTest {
	private AutoTest() {}

	static final boolean ENABLED = System.getProperty("superpowers.autotest") != null;
	static boolean worldRequested;
	static int tick;
	static final List<String> LOG = new ArrayList<>();
	static final List<Step> STEPS = new ArrayList<>();
	static int stepIndex;
	static int wait;

	record Step(int delay, String name, Runnable action) {}

	static void step(int delay, String name, Runnable action) {
		STEPS.add(new Step(delay, name, action));
	}

	static void log(String s) {
		Superpowers.LOGGER.info("[AUTOTEST] " + s);
		LOG.add(s);
	}

	static void shot(String name) {
		Minecraft mc = Minecraft.getInstance();
		Screenshot.grab(mc.gameDirectory, "sp_" + name + ".png", mc.getMainRenderTarget(), c -> {});
		log("screenshot " + name + " effects=" + FxManager.count());
	}

	static void cmd(String c) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.connection.sendCommand(c);
	}

	static void build() {
		Minecraft mc = Minecraft.getInstance();
		step(40, "setup", () -> {
			mc.options.pauseOnLostFocus = false;
			cmd("time set day");
			cmd("weather clear");
			cmd("gamerule doDaylightCycle false");
			cmd("gamerule doMobSpawning false");
			cmd("tp @s 0 -49 0 0 0");
		});
		step(30, "tome check", () -> {
			boolean has = mc.player.getInventory().contains(new net.minecraft.world.item.ItemStack(ModItems.POWER_TOME));
			log("creative tome given: " + has);
		});
		step(5, "open book", () -> mc.setScreen(new PowerBookScreen()));
		step(20, "book fire", () -> shot("book_fire"));
		step(2, "book select god", () -> ((PowerBookScreen) mc.screen).select(PowerType.GOD));
		step(15, "book god", () -> shot("book_god"));
		step(2, "book close", () -> mc.setScreen(null));

		String only = System.getProperty("superpowers.autotest", "");
		boolean awakenOnly = only.contains("awaken");
		for (PowerType t : new PowerType[]{PowerType.FIRE, PowerType.SPACE, PowerType.ANGEL, PowerType.DEMON, PowerType.GOD, PowerType.ENTITY}) {
			if (!only.isEmpty() && !only.equals("1") && !only.contains(t.id)) continue;
			String n = t.id;
			step(10, n + " arena", () -> {
				cmd("kill @e[type=!player]");
				cmd("fill -30 -60 -10 30 -51 25 stone");
				cmd("fill -30 -60 26 30 -51 60 stone");
				cmd("fill -30 -50 -10 30 -50 60 grass_block");
				cmd("fill -30 -49 -10 30 -40 25 air");
				cmd("fill -30 -49 26 30 -40 60 air");
				cmd("tp @s 0 -49 0 0 0");
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
			step(10, n + " targets", () -> spawnTargets());
			step(5, n + " awaken", () -> ClientPlayNetworking.send(new Net.SelectPower(t.ordinal())));
			step(25, n + " hud", () -> {
				log(n + " client power=" + ClientState.power + " mana=" + ClientState.mana + " infinite=" + ClientState.infinite);
				shot(n + "_1_hud");
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			if (!awakenOnly) {
				step(6, n + " aura", () -> shot(n + "_1b_aura"));
				for (int i = 0; i < 4; i++) {
					final int k = i;
					step(7, n + " m1 " + i, () -> ClientPlayNetworking.send(new Net.Ability(0)));
					if (i == 3) step(2, n + " m1 shot", () -> shot(n + "_2_m1_" + k));
				}
				step(20, n + " z", () -> ClientPlayNetworking.send(new Net.Ability(1)));
				step(20, n + " z shot", () -> shot(n + "_3_z"));
				step(30, n + " x", () -> ClientPlayNetworking.send(new Net.Ability(2)));
				step(8, n + " x shot", () -> shot(n + "_4_x"));
				step(45, n + " back", () -> cmd("tp @s 0 -49 0 0 0"));
				step(10, n + " c", () -> ClientPlayNetworking.send(new Net.Ability(3)));
				step(24, n + " c shot", () -> shot(n + "_5_c"));
				step(90, n + " v", () -> {
					cmd("tp @s 0 -49 0 0 0");
					spawnTargets();
					ClientPlayNetworking.send(new Net.Ability(4));
				});
				step(45, n + " v 45", () -> shot(n + "_6_v_45"));
				step(43, n + " v 88", () -> {
					shot(n + "_6_v_88");
					log(n + " debris chunks=" + com.superpowers.client.fx.Debris.count());
				});
				step(t.ultDuration - 88 + 30, n + " v done", () -> shot(n + "_7_after"));
			}
			if (t == PowerType.ENTITY) {
				// first-person claw swipes: right hand right-to-left, then left hand left-to-right
				step(10, n + " fp", () -> {
					cmd("kill @e[type=!player]");
					cmd("tp @s 0 -49 0 0 0");
					mc.player.getInventory().selected = 4;
					mc.options.setCameraType(CameraType.FIRST_PERSON);
				});
				step(30, n + " fp m1 a", () -> ClientPlayNetworking.send(new Net.Ability(0)));
				step(3, n + " fp m1 a shot", () -> shot(n + "_6a_fp_sweepR"));
				step(5, n + " fp m1 b", () -> ClientPlayNetworking.send(new Net.Ability(0)));
				step(3, n + " fp m1 b shot", () -> shot(n + "_6b_fp_sweepL"));
				step(25, n + " tp front", () -> {
					mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
					ClientPlayNetworking.send(new Net.Ability(0));
				});
				step(4, n + " tp front shot", () -> shot(n + "_6c_tp_sweep"));
				// the feast on a villager: head + spine ripped out, body thrown down
				step(30, n + " villager", () -> {
					mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
					cmd("kill @e[type=!player]");
					cmd("tp @s 0 -49 0 0 0");
					cmd("summon villager 0 -49 7 {NoAI:1b,PersistenceRequired:1b,VillagerData:{profession:\"minecraft:farmer\",level:2,type:\"minecraft:plains\"}}");
				});
				step(20, n + " villager v", () -> ClientPlayNetworking.send(new Net.Ability(4)));
				step(84, n + " rip shot", () -> shot(n + "_6d_rip"));
				step(16, n + " throw shot", () -> shot(n + "_6e_thrown"));
				step(15, n + " chew shot", () -> shot(n + "_6f_chew"));
				step(60, n + " aftermath", () -> {
					cmd("tp @s 0 -49 -3 0 25");
					shot(n + "_6g_corpse");
				});
				step(20, n + " aftermath close", () -> {
					cmd("tp @s 2 -47 3 140 50");
					mc.options.setCameraType(CameraType.FIRST_PERSON);
				});
				step(6, n + " aftermath close shot", () -> shot(n + "_6h_corpse_close"));
				step(2, n + " cam back", () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				// trapped in someone else's domain (simulated client-side): darkness + the thing behind you
				step(10, n + " trapped", () -> {
					cmd("tp @s 0 -49 0 0 0");
					cmd("effect give @s blindness 12 0 true");
					mc.options.setCameraType(CameraType.FIRST_PERSON);
					com.superpowers.client.fx.HorrorFx.domain(mc.player.position(), 14, 260, -1);
				});
				step(45, n + " trapped look back", () -> cmd("tp @s 0 -49 0 180 10"));
				step(3, n + " trapped shot", () -> shot(n + "_7a_apparition"));
				step(20, n + " jumpscare", () -> com.superpowers.client.fx.HorrorFx.jumpscare());
				step(4, n + " jumpscare shot", () -> shot(n + "_7b_jumpscare"));
				step(30, n + " survival hp", () -> {
					cmd("effect clear @s");
					cmd("tp @s 0 -49 0 0 0");
					cmd("gamemode survival");
				});
				step(10, n + " damage", () -> cmd("damage @s 30 minecraft:generic"));
				step(25, n + " hp shot", () -> {
					log(n + " hp=" + mc.player.getHealth() + "/" + mc.player.getMaxHealth());
					shot(n + "_7c_healthbar");
					cmd("gamemode creative");
				});
			}
			// awakening
			step(20, n + " meter", () -> {
				cmd("tp @s 0 -49 0 0 0");
				cmd("power meter 100");
				spawnTargets();
			});
			step(20, n + " meter check", () -> log(n + " meter=" + ClientState.meter));
			step(2, n + " G", () -> ClientPlayNetworking.send(new Net.Awaken(1)));
			for (int at : new int[]{30, 63, 70, 92}) {
				step(at == 30 ? 30 : at == 63 ? 33 : at == 70 ? 7 : 22, n + " awaken " + at, () -> shot(n + "_8_awaken_" + at));
			}
			step(PowerType.AWAKEN_CUTSCENE - 92 + 15, n + " awakened", () -> {
				log(n + " awakened left=" + ClientState.awakenLeft + " form=" + ClientState.isAwakened(mc.player));
				shot(n + "_9_awakened_form");
				cmd("tp @s 0 -49 0 0 0");
			});
			if (t == PowerType.ENTITY) {
				step(5, n + " aw fp", () -> {
					mc.player.getInventory().selected = 4;
					mc.options.setCameraType(CameraType.FIRST_PERSON);
				});
				step(12, n + " aw fp shot", () -> shot(n + "_9f_fp_arms"));
				step(2, n + " aw fp m1", () -> ClientPlayNetworking.send(new Net.Ability(0)));
				step(3, n + " aw fp m1 shot", () -> shot(n + "_9g_fp_sweep"));
				step(10, n + " aw fp back", () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			}
			// wings while walking / running, then flight
			step(5, n + " walk", () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.options.keyUp.setDown(true);
			});
			step(14, n + " walk shot", () -> shot(n + "_9a_walk"));
			step(2, n + " run", () -> mc.options.keySprint.setDown(true));
			step(12, n + " run shot", () -> shot(n + "_9b_run"));
			step(2, n + " takeoff", () -> {
				mc.options.keyUp.setDown(false);
				mc.options.keySprint.setDown(false);
				cmd("tp @s 0 -49 0 0 0");
				mc.player.getAbilities().flying = true;
				mc.player.onUpdateAbilities();
				mc.options.keyJump.setDown(true);
			});
			step(12, n + " hover", () -> {
				mc.options.keyJump.setDown(false);
				log(n + " flying=" + mc.player.getAbilities().flying + " flight=" + ClientState.flight(mc.player, 0));
			});
			step(10, n + " hover shot", () -> shot(n + "_9c_hover"));
			step(2, n + " fly fwd", () -> {
				mc.options.keyUp.setDown(true);
				mc.options.keySprint.setDown(true);
			});
			step(16, n + " fly front", () -> shot(n + "_9d_fly_front"));
			step(2, n + " fly back cam", () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			step(4, n + " fly back", () -> shot(n + "_9e_fly_back"));
			step(2, n + " land", () -> {
				mc.options.keyUp.setDown(false);
				mc.options.keySprint.setDown(false);
				mc.player.getAbilities().flying = false;
				mc.player.onUpdateAbilities();
				cmd("tp @s 0 -49 0 0 0");
			});
			for (int i = 0; i < 4; i++) {
				final int k = i;
				step(7, n + " aw m1 " + i, () -> ClientPlayNetworking.send(new Net.Ability(0)));
				if (i == 3) step(2, n + " aw m1 shot", () -> shot(n + "_A_m1"));
			}
			step(15, n + " aw z", () -> ClientPlayNetworking.send(new Net.Ability(1)));
			step(18, n + " aw z shot", () -> shot(n + "_B_z"));
			step(20, n + " aw x", () -> ClientPlayNetworking.send(new Net.Ability(2)));
			step(8, n + " aw x shot", () -> shot(n + "_C_x"));
			step(30, n + " aw back", () -> cmd("tp @s 0 -49 0 0 0"));
			step(10, n + " aw c", () -> ClientPlayNetworking.send(new Net.Ability(3)));
			step(26, n + " aw c shot", () -> shot(n + "_D_c"));
			step(60, n + " aw v", () -> {
				cmd("tp @s 0 -49 0 0 0");
				spawnTargets();
				ClientPlayNetworking.send(new Net.Ability(4));
			});
			step(45, n + " aw v 45", () -> shot(n + "_E_v_45"));
			step(43, n + " aw v 88", () -> {
				shot(n + "_E_v_88");
				log(n + " awakened debris chunks=" + com.superpowers.client.fx.Debris.count());
			});
			step(t.ultDuration - 88 + 40, n + " aw after", () -> shot(n + "_F_after"));
			step(5, n + " F", () -> ClientPlayNetworking.send(new Net.Awaken(0)));
			step(25, n + " ended", () -> {
				log(n + " after F awakened=" + ClientState.isAwakened(mc.player) + " left=" + ClientState.awakenLeft);
				shot(n + "_G_ended");
			});
		}
		step(20, "finish", () -> {
			log("DONE");
			try {
				Files.write(Path.of(mc.gameDirectory.getPath(), "autotest.log"), LOG);
			} catch (IOException e) {
				Superpowers.LOGGER.error("autotest log", e);
			}
			mc.stop();
		});
	}

	static void spawnTargets() {
		cmd("summon zombie 0 -49 7 {NoAI:1b,PersistenceRequired:1b}");
		cmd("summon zombie 2 -49 8 {NoAI:1b,PersistenceRequired:1b}");
		cmd("summon zombie -2 -49 8 {NoAI:1b,PersistenceRequired:1b}");
		cmd("summon husk 0 -49 14 {NoAI:1b,PersistenceRequired:1b}");
		cmd("summon cow 4 -49 18 {NoAI:1b,PersistenceRequired:1b}");
		cmd("summon villager -4 -49 18 {NoAI:1b,PersistenceRequired:1b}");
	}

	public static void tick(Minecraft mc) {
		if (!ENABLED) return;
		tick++;
		if (!worldRequested && mc.screen instanceof TitleScreen && tick > 40) {
			worldRequested = true;
			log("creating world");
			GameRules rules = new GameRules();
			LevelSettings settings = new LevelSettings("sp_autotest_" + System.currentTimeMillis(), GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
			mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, new WorldOptions(12345L, false, false),
					reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
					mc.screen);
			return;
		}
		if (mc.level == null || mc.player == null) return;
		if (STEPS.isEmpty()) {
			build();
			wait = STEPS.get(0).delay;
		}
		if (stepIndex >= STEPS.size()) return;
		if (--wait > 0) return;
		Step s = STEPS.get(stepIndex++);
		try {
			s.action.run();
		} catch (Exception e) {
			log("STEP FAILED " + s.name + ": " + e);
			Superpowers.LOGGER.error("autotest step failed", e);
		}
		if (stepIndex < STEPS.size()) wait = STEPS.get(stepIndex).delay;
	}
}
