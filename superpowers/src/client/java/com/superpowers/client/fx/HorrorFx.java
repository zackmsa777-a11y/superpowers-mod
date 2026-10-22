package com.superpowers.client.fx;

import com.superpowers.client.ClientState;
import com.superpowers.client.ScreenFx;
import com.superpowers.client.model.MeshDraw;
import com.superpowers.client.screen.Emblem;
import com.superpowers.power.Fx;
import com.superpowers.power.Hunt;
import com.superpowers.power.PowerType;
import com.superpowers.registry.ModSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Entity 666 visuals: the feast, the hollow, apparitions, the jumpscare face and gore. */
public final class HorrorFx {
	private HorrorFx() {}

	static final int BLOOD = 0x6A0008, BLOOD_DARK = 0x2A0003, RED = 0xB0101A;
	static final Vec3 UP = new Vec3(0, 1, 0);

	static Entity entity(int id) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null || id < 0 ? null : mc.level.getEntity(id);
	}

	public static boolean isEntityForm(Player p) {
		return ClientState.powerOf(p) == PowerType.ENTITY && ClientState.isAwakened(p);
	}

	// ------------------------------------------------------------------ gore particles

	public static void blood(Vec3 at, int n, double spread, double speed) {
		Particles.burst(Particles.dust(BLOOD, 1.6f), at, n, spread, speed);
		Particles.burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), at, n / 2, spread, speed);
	}

	/** Neck fountain after the head comes off. */
	static void fountain(int victimId, int life) {
		FxManager.add(new FxHandler.Emitter(life, i -> {
			Entity v = entity(victimId);
			if (v == null) return;
			Vec3 neck = v.position().add(0, v.getBbHeight() * 0.85, 0);
			float k = 1 - i / (float) life;
			for (int j = 0; j < 3; j++) {
				Vec3 vel = new Vec3((Math.random() - 0.5) * 0.15, 0.25 + Math.random() * 0.25 * k, (Math.random() - 0.5) * 0.15);
				Particles.add(Particles.dust(j == 0 ? BLOOD_DARK : BLOOD, 1.4f), neck, vel);
			}
			if (i % 3 == 0) Particles.add(ParticleTypes.FALLING_LAVA, neck, Vec3.ZERO);
		}));
	}

	// ------------------------------------------------------------------ the feast

	/** One feast in progress. Draws its own creature unless the caster IS the creature (awakened). */
	public static class Feast extends Effect {
		final int casterId, victimId;
		final Vec3 base, fwd, right;
		final float yaw;
		final Hunt.Timeline tl;
		final boolean cinematic;
		Creature.Joints lastJoints;

		Feast(int casterId, int victimId, Vec3 base, Vec3 facing, boolean cinematic) {
			super((cinematic ? Hunt.CINEMATIC : Hunt.QUICK).end() + 2);
			this.casterId = casterId;
			this.victimId = victimId;
			this.base = base;
			this.fwd = new Vec3(facing.x, 0, facing.z).lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : new Vec3(facing.x, 0, facing.z).normalize();
			this.right = fwd.cross(UP).normalize();
			this.yaw = (float) Mth.atan2(-fwd.x, fwd.z);
			this.cinematic = cinematic;
			this.tl = cinematic ? Hunt.CINEMATIC : Hunt.QUICK;
		}

		boolean casterIsCreature() {
			return entity(casterId) instanceof Player p && isEntityForm(p);
		}

		/** Hands/mouth of whichever creature is doing the eating, as drawn last frame. */
		Creature.Joints joints() {
			return casterIsCreature() ? JOINTS.get(casterId) : lastJoints;
		}

		Vec3 mouthGuess() {
			return base.add(fwd.scale(0.8)).add(0, 2.95, 0);
		}

		@Override
		public void renderAlpha(float pt) {
			if (!casterIsCreature()) draw(pt, false);
		}

		@Override
		public void renderAdd(float pt) {
			if (!casterIsCreature()) draw(pt, true);
		}

		void draw(float pt, boolean add) {
			float t = age + pt;
			Creature.Pose ps = Creature.locomotion(ClientState.ticks + pt, 0, 0, false, 0, casterId);
			apply(ps, t, pt);
			float alpha = cinematic ? Mth.clamp((life - t) / 10f, 0, 1) : 1f;
			lastJoints = Creature.draw(base, yaw, ps, Math.max(0.35f, MeshDraw.brightness(base.add(0, 2, 0))), add, alpha);
		}

		/** rise, grab, lift, climb to the head, RIP (head + spine), throw the body, chew, toss, sink. */
		void apply(Creature.Pose ps, float t, float pt) {
			if (cinematic && t < tl.rise()) {
				float k = t / tl.rise();
				ps.sink = 3.9f * (1 - Effect.easeOut(k));
				ps.swing[0] = ps.swing[1] = 2.4f * (1 - k) + 0.6f;
				ps.elbow[0] = ps.elbow[1] = 0.3f;
				ps.hunch = 0.2f;
				ps.headPitch = 0.6f;
			}
			if (victimId < 0) {
				// nothing to eat: it screams at the sky
				if (t > 38 && t < 75) {
					float k = Mth.clamp((t - 38) / 6f, 0, 1) * Mth.clamp((75 - t) / 8f, 0, 1);
					ps.jaw = Mth.lerp(k, ps.jaw, 1.25f);
					ps.headPitch = Mth.lerp(k, ps.headPitch, -1.0f);
					ps.swing[0] = ps.swing[1] = Mth.lerp(k, 0, 2.6f);
					ps.spread[0] = ps.spread[1] = Mth.lerp(k, 0.1f, 0.6f);
					ps.headRoll = 0.15f * Mth.sin(t * 2.5f) * k;
				}
			} else {
				Entity v = entity(victimId);
				if (t < tl.rip()) {
					if (v != null && t >= (cinematic ? tl.rise() - 8 : 0)) {
						Vec3 chest = v.getPosition(pt).add(0, v.getBbHeight() * 0.55, 0);
						ps.reach[1] = chest.add(right.scale(-0.1));
						if (t < tl.liftEnd()) {
							ps.reach[0] = chest.add(0, 0.15, 0).add(right.scale(0.1));
						} else {
							// the right hand climbs up to the head and closes around it
							float k = Mth.clamp((t - tl.liftEnd()) / (float) Math.max(1, tl.rip() - tl.liftEnd() - 4), 0, 1);
							Vec3 head = v.getPosition(pt).add(0, v.getEyeHeight() + 0.1, 0);
							ps.reach[0] = chest.lerp(head, k * k * (3 - 2 * k));
						}
						ps.hunch = 0.3f;
						ps.headPitch = 0.05f;
					}
				} else {
					float since = t - tl.rip();
					Vec3 mouth = mouthGuess();
					if (t < tl.toss()) {
						// yank the head up and out (the spine slides out after it), then bring it to the jaws
						Vec3 yank = mouth.add(right.scale(0.75)).add(fwd.scale(0.5)).add(0, 0.85, 0);
						Vec3 bite = mouth.add(fwd.scale(0.4)).add(0, -0.15, 0);
						float k = Mth.clamp((since - 10) / 8f, 0, 1);
						ps.reach[0] = since < 10 ? yank : yank.lerp(bite, k * k * (3 - 2 * k));
					}
					if (t < tl.throwAt()) {
						ps.reach[1] = mouth.add(fwd.scale(0.35)).add(right.scale(-0.35)).add(0, -1.1, 0);
					} else {
						float k = Mth.clamp((t - tl.throwAt()) / 6f, 0, 1);
						ps.swing[1] = Mth.lerp(k, 1.3f, 0.25f);
						ps.sweep[1] = Mth.lerp(k, -0.5f, 1.1f);
					}
					ps.hunch = 0.3f;
					ps.headPitch = 0.1f;
				}
				ps.jaw = Math.max(ps.jaw, jawAt(t));
			}
			int sinkFrom = victimId < 0 ? tl.end() - 25 : tl.toss() + 4;
			if (cinematic && t > sinkFrom) {
				float k = Mth.clamp((t - sinkFrom) / (tl.end() - sinkFrom), 0, 1);
				ps.sink = 3.9f * Effect.easeIn(k);
			}
		}

		float jawAt(float t) {
			float open = 0;
			float r = tl.rip();
			if (t > r - 10 && t <= r + 6) open = 1.2f * Mth.clamp((t - (r - 10)) / 6f, 0, 1) * Mth.clamp((r + 6 - t) / 6f, 0, 1);
			for (int c : tl.chew()) {
				if (t > c - 6 && t <= c) open = Math.max(open, 1.0f * (t - (c - 6)) / 6f);
				else if (t > c && t < c + 2) open = Math.max(open, 1.0f * (1 - (t - c) / 2f));
			}
			return open;
		}
	}

	/** Last drawn joints of awakened Entity players (for props held in their hands). */
	static final java.util.Map<Integer, Creature.Joints> JOINTS = new java.util.HashMap<>();

	public static void rememberJoints(int playerId, Creature.Joints j) {
		JOINTS.put(playerId, j);
	}

	static Feast feastOfVictim(int victimId) {
		for (Feast f : FEASTS) {
			if (f.victimId == victimId && !f.dead()) return f;
		}
		return null;
	}

	// ------------------------------------------------------------------ gore props

	/**
	 * A client-side copy of the victim: the headless body (thrown down, lies in a pool of blood) or the
	 * torn-off head with its spine (held, chewed on, dropped).
	 */
	public static class GoreProp extends Effect {
		final LivingEntity dummy;
		final boolean corpse;
		final Feast feast;
		final int hand;
		final float headY;
		boolean held = true;
		Vec3 pos, prev, vel = Vec3.ZERO;
		float lie, lieO;
		final float yawDeg;
		int landedAt = -1;
		Vec3 swingVel = Vec3.ZERO;

		GoreProp(LivingEntity dummy, boolean corpse, Feast feast, Vec3 start, float headY) {
			super(900);
			this.dummy = dummy;
			this.corpse = corpse;
			this.feast = feast;
			this.hand = corpse ? 1 : 0;
			this.headY = headY;
			this.pos = this.prev = start;
			this.yawDeg = feast != null ? feast.yaw * Mth.RAD_TO_DEG + (corpse ? 90 : 180) : (float) (Math.random() * 360);
		}

		Vec3 anchor() {
			Creature.Joints j = feast != null ? feast.joints() : null;
			if (j == null || j.hand[hand] == Vec3.ZERO) return null;
			return j.hand[hand];
		}

		/** Where the prop sits relative to the hand holding it. */
		Vec3 heldPos(Vec3 a) {
			if (corpse) return a.subtract(0, dummy != null ? dummy.getBbHeight() * 0.55 : 1, 0);
			return a;
		}

		@Override
		public void tick() {
			super.tick();
			prev = pos;
			lieO = lie;
			if (held) {
				Vec3 a = anchor();
				if (a != null) pos = heldPos(a);
				swingVel = swingVel.scale(0.8).add(pos.subtract(prev).scale(0.5));
				return;
			}
			if (landedAt >= 0) return;
			vel = vel.add(0, -0.06, 0).scale(0.98);
			pos = pos.add(vel);
			lie = Math.min(1, lie + 0.13f);
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) return;
			double bottom = corpse ? pos.y : pos.y - 0.2;
			BlockPos bp = BlockPos.containing(pos.x, bottom, pos.z);
			VoxelShape shape = mc.level.getBlockState(bp).getCollisionShape(mc.level, bp);
			if (!shape.isEmpty()) {
				double top = bp.getY() + shape.max(Direction.Axis.Y);
				if (bottom < top) {
					pos = new Vec3(pos.x, top + (corpse ? 0 : 0.2), pos.z);
					land();
				}
			}
		}

		void land() {
			landedAt = age;
			vel = Vec3.ZERO;
			lie = 1;
			Minecraft mc = Minecraft.getInstance();
			if (mc.level != null) {
				mc.level.playLocalSound(pos.x, pos.y, pos.z, corpse ? SoundEvents.PLAYER_BIG_FALL : SoundEvents.PLAYER_SMALL_FALL,
						SoundSource.HOSTILE, 1.4f, 0.6f, false);
				mc.level.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1f, 0.5f, false);
			}
			blood(pos.add(0, 0.2, 0), corpse ? 45 : 20, 0.5, 0.2);
		}

		void release(Vec3 v) {
			held = false;
			vel = v;
		}

		float sink(float pt) {
			float left = life - age - pt;
			return left < 40 ? (1 - left / 40f) * 0.6f : 0;
		}

		Vec3 at(float pt) {
			return held ? pos : prev.lerp(pos, pt);
		}

		@Override
		public void renderAlpha(float pt) {
			Vec3 p = at(pt).subtract(0, sink(pt), 0);
			float l = Mth.lerp(pt, lieO, lie);
			if (landedAt >= 0) {
				// blood pool spreading under it
				float k = Math.min(1f, (age - landedAt + pt) / 50f);
				Vec3 c = corpse ? p.add(lieDir().scale(1.25)) : p.add(0, -0.2, 0);
				Draw.disc(c.add(0, 0.015, 0), UP, (corpse ? 1.1 : 0.5) * Effect.easeOut(k), BLOOD_DARK, 0.9f, 0.75f, 32);
				Draw.disc(c.add(0, 0.02, 0), UP, (corpse ? 0.7 : 0.3) * Effect.easeOut(k), BLOOD, 0.6f, 0.0f, 32);
			}
			if (!corpse) drawSpine(p, l, pt);
		}

		/** Direction from the corpse's feet toward its (missing) head once it lies down. */
		Vec3 lieDir() {
			double y = Math.toRadians(yawDeg);
			return new Vec3(Math.sin(y), 0, -Math.cos(y));
		}

		void drawSpine(Vec3 headPos, float l, float pt) {
			Vec3 hang = new Vec3(-swingVel.x * 2.2, -1, -swingVel.z * 2.2).normalize();
			double y = Math.toRadians(yawDeg);
			Vec3 flat = new Vec3(Math.cos(y), -0.05, Math.sin(y)).normalize();
			Vec3 dir = hang.lerp(flat, l).normalize();
			Vec3 top = headPos.add(dir.scale(0.26)).add(0, -0.05 * (1 - l), 0);
			Vec3 up = dir.scale(-1);
			Vec3 r = up.cross(Math.abs(up.y) > 0.9 ? new Vec3(0, 0, 1) : UP).normalize();
			Vec3 f = r.cross(up).normalize();
			MeshDraw.draw(com.superpowers.client.model.Mesh.get("torn_spine"), MeshDraw.frame(top, r, up, f, 1.7f),
					Math.max(0.35f, MeshDraw.brightness(top)), 0, 0, 1f);
		}

		void renderEntity(PoseStack ps, MultiBufferSource buffers, float pt) {
			if (dummy == null) return;
			Minecraft mc = Minecraft.getInstance();
			Vec3 p = at(pt).subtract(0, sink(pt), 0);
			float l = Mth.lerp(pt, lieO, lie);
			ps.pushPose();
			ps.translate(p.x - Draw.cx, p.y - Draw.cy, p.z - Draw.cz);
			ps.mulPose(Axis.YP.rotationDegrees(-yawDeg));
			if (corpse) {
				// on its back, sprawled where it fell
				ps.translate(0, 0.18 * l, 0);
				ps.mulPose(Axis.XP.rotationDegrees(-90 * l));
			} else {
				ps.mulPose(Axis.ZP.rotationDegrees(80 * l));
				ps.translate(0, -headY, 0);
			}
			int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p.add(0, 0.5, 0)));
			mc.getEntityRenderDispatcher().render(dummy, 0, 0, 0, 0f, pt, ps, buffers, light);
			ps.popPose();
		}
	}

	private static final List<GoreProp> PROPS = new ArrayList<>();

	/** A lookalike of the victim that isn't in the world: same type, skin, villager job and gear. */
	static LivingEntity copyOf(LivingEntity v, boolean headOnly) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		LivingEntity d;
		if (v instanceof AbstractClientPlayer pl) {
			d = new RemotePlayer(mc.level, pl.getGameProfile());
		} else {
			Entity e = v.getType().create(mc.level);
			if (!(e instanceof LivingEntity le)) return null;
			d = le;
			try {
				CompoundTag tag = v.saveWithoutId(new CompoundTag());
				tag.remove("UUID");
				tag.remove("Passengers");
				d.load(tag);
			} catch (Exception ignored) {
			}
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (headOnly && slot != EquipmentSlot.HEAD) continue;
			if (!headOnly && slot == EquipmentSlot.HEAD) continue;
			d.setItemSlot(slot, v.getItemBySlot(slot).copy());
		}
		d.deathTime = 0;
		d.hurtTime = 0;
		d.setHealth(Math.max(1f, d.getMaxHealth()));
		d.clearFire();
		d.setPos(v.position());
		d.setYRot(0);
		d.setXRot(0);
		d.yBodyRot = d.yBodyRotO = 0;
		d.yHeadRot = d.yHeadRotO = 0;
		ClientState.addProp(d.getId());
		if (headOnly) ClientState.setHeadOnly(d.getId(), 100000);
		else ClientState.setHeadless(d.getId(), 100000);
		return d;
	}

	/** Can we draw just this entity's head? (humanoids and villagers) */
	static boolean hasDrawableHead(LivingEntity v) {
		var r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(v);
		if (!(r instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> lr)) return false;
		return lr.getModel() instanceof net.minecraft.client.model.HumanoidModel<?> || lr.getModel() instanceof net.minecraft.client.model.VillagerModel<?>;
	}

	/** The head comes off with the spine: hide the real victim, swap in the two props. */
	public static void gore(int victimId, Vec3 headPos) {
		Entity e = entity(victimId);
		ClientState.hide(victimId, 400);
		ClientState.setHeadless(victimId, 400);
		blood(headPos, 80, 0.35, 0.5);
		fountain(victimId, 30);
		if (!(e instanceof LivingEntity v)) return;
		Feast f = feastOfVictim(victimId);
		boolean head = hasDrawableHead(v);
		float headY = v.getType() == net.minecraft.world.entity.EntityType.VILLAGER || v instanceof net.minecraft.world.entity.npc.AbstractVillager ? 1.81f : 1.75f;
		LivingEntity body = copyOf(v, false);
		if (body != null) {
			GoreProp c = FxManager.add(new GoreProp(body, true, f, v.position(), 0));
			PROPS.add(c);
			if (f == null) c.release(new Vec3(0, 0.1, 0));
		}
		GoreProp h = FxManager.add(new GoreProp(head ? copyOf(v, true) : null, false, f, headPos, headY));
		PROPS.add(h);
		if (f == null) h.release(new Vec3(0, 0.3, 0));
	}

	public static void throwBody(int victimId, Vec3 vel) {
		for (GoreProp p : PROPS) {
			if (p.corpse && p.held && p.feast != null && p.feast.victimId == victimId) p.release(vel);
		}
	}

	public static void toss(int victimId, Vec3 facing) {
		for (GoreProp p : PROPS) {
			if (!p.corpse && p.held && p.feast != null && p.feast.victimId == victimId) {
				p.release(facing.scale(0.15).add(0, 0.12, 0));
			}
		}
	}

	/** Entity models of the props; called after the effect passes, like the block debris. */
	public static void renderProps(float pt) {
		PROPS.removeIf(Effect::dead);
		if (PROPS.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		PoseStack ps = new PoseStack();
		for (GoreProp p : PROPS) p.renderEntity(ps, buffers, pt);
		buffers.endBatch();
	}

	private static final List<Feast> FEASTS = new ArrayList<>();

	public static void feast(int casterId, int victimId, Vec3 base, Vec3 facing, boolean cinematic) {
		Feast f = FxManager.add(new Feast(casterId, victimId, base, facing, cinematic));
		FEASTS.add(f);
		if (cinematic) {
			Particles.burst(ParticleTypes.LARGE_SMOKE, base.add(0, 0.3, 0), 50, 1.2, 0.15);
			FxManager.add(new FxHandler.Emitter(30, i -> {
				Particles.burst(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), base.add(0, 0.2, 0), 6, 0.9, 0.3);
				Particles.add(ParticleTypes.SMOKE, base.add(Particles.randDir().scale(0.8)), new Vec3(0, 0.05, 0));
			}));
		}
	}

	/** The feast the given caster is performing right now (its awakened body follows the same timeline). */
	public static Feast feastOf(int casterId) {
		FEASTS.removeIf(Effect::dead);
		for (Feast f : FEASTS) {
			if (f.casterId == casterId) return f;
		}
		return null;
	}

	// ------------------------------------------------------------------ player forms

	/** Pose for an awakened Entity player: locomotion + attacks + an ongoing feast. */
	public static Creature.Pose playerPose(Player p, float pt) {
		float time = ClientState.ticks + pt;
		float[] ws = ClientState.wingState(p, pt);
		Creature.Pose ps = Creature.locomotion(time, p.walkAnimation.position(pt), p.walkAnimation.speed(pt), p.isSprinting(), ws[0], p.getId());
		float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
		float headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
		ps.headYaw += Mth.wrapDegrees(headYaw - bodyYaw) * Mth.DEG_TO_RAD;
		ps.headPitch = -ps.hunch + 0.25f + p.getViewXRot(pt) * Mth.DEG_TO_RAD * 0.8f;
		ClientState.Anim a = ClientState.anim(p.getId());
		if (a != null) {
			float k = Mth.clamp((ClientState.ticks + pt - a.start()) / a.duration(), 0, 1);
			float w = Math.min(1f, k / 0.15f) * Math.min(1f, (1 - k) / 0.25f);
			boolean r = a.id() == Fx.Anim.SWEEP_R || a.id() == Fx.Anim.SWEEP_BOTH;
			boolean l = a.id() == Fx.Anim.SWEEP_L || a.id() == Fx.Anim.SWEEP_BOTH;
			if (r || l) {
				// claws rake sideways across the front: out wide, then all the way across
				float e = Mth.clamp((k - 0.08f) / 0.6f, 0, 1);
				float ang = Mth.lerp(e * e * (3 - 2 * e), 1.15f, -1.0f);
				for (int i = 0; i < 2; i++) {
					if (i == 0 ? !r : !l) continue;
					ps.swing[i] = Mth.lerp(w, ps.swing[i], 1.25f);
					ps.sweep[i] = ang * w;
					ps.elbow[i] = Mth.lerp(w, ps.elbow[i], 0.12f);
				}
				ps.jaw = Math.max(ps.jaw, 0.55f * w);
			} else {
				float wv = Mth.sin(k * (float) Math.PI);
				ps.swing[0] = Mth.lerp(wv, ps.swing[0], 1.9f);
				ps.swing[1] = Mth.lerp(wv, ps.swing[1], 1.9f);
			}
		}
		Feast f = feastOf(p.getId());
		if (f != null) f.apply(ps, f.age + pt, pt);
		return ps;
	}

	// ------------------------------------------------------------------ the hollow

	public static class Domain extends Effect {
		final Vec3 c;
		final double r;
		final int ownerId;

		Domain(Vec3 c, double r, int life, int ownerId) {
			super(life);
			this.c = c;
			this.r = r;
			this.ownerId = ownerId;
		}

		float grow(float pt) {
			return Effect.easeOut(Mth.clamp((age + pt) / 14f, 0, 1));
		}

		@Override
		public void renderAlpha(float pt) {
			float a = fade(pt, 0.02f, 0.08f);
			double rr = r * grow(pt);
			Draw.sphere(c, rr, 0x000000, 0.25f * a, 0.85f * a, 18, 36);
			Draw.disc(c.add(0, 0.05, 0), UP, rr, 0x050000, 0.9f * a, 0.6f * a, 64);
		}

		@Override
		public void renderAdd(float pt) {
			float a = fade(pt, 0.02f, 0.08f);
			float time = ClientState.ticks + pt;
			double rr = r * grow(pt);
			float pulse = 0.6f + 0.4f * Mth.sin(time * 0.15f);
			Draw.ring(c.add(0, 0.08, 0), UP, rr, 0.35, RED, 0.7f * a * pulse, 96);
			Draw.sigil(c.add(0, 0.07, 0), UP, Math.min(rr * 0.6, 9), time * 0.003, 0x6A0010, 0.45f * a, 6, 6);
			Draw.polygram(c.add(0, 0.075, 0), UP, Math.min(rr * 0.45, 7), 6, 6, 0.12, -time * 0.004, 0x8A0010, 0.4f * a);
		}

		boolean contains(Vec3 p) {
			return p.distanceTo(c) <= r;
		}
	}

	private static final List<Domain> DOMAINS = new ArrayList<>();

	public static void domain(Vec3 c, double r, int life, int ownerId) {
		Domain d = FxManager.add(new Domain(c, r, life, ownerId));
		DOMAINS.add(d);
		Particles.burst(ParticleTypes.SMOKE, c.add(0, 1, 0), 120, r * 0.4, 0.3);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && d.contains(mc.player.position()) && mc.player.getId() != ownerId) {
			ScreenFx.flash(0x000000, 1f, 30);
			ScreenFx.shake(0.6f, 20);
		}
	}

	/** The domain the local player is trapped in (null if none or it's their own). */
	public static Domain trappedIn() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return null;
		DOMAINS.removeIf(Effect::dead);
		for (Domain d : DOMAINS) {
			if (d.ownerId != mc.player.getId() && d.contains(mc.player.position())) return d;
		}
		return null;
	}

	// ------------------------------------------------------------------ apparitions

	/** Something stands behind you in the dark. Look at it and it's gone... and closer next time. */
	static final class Apparition {
		Vec3 pos;
		int born, seenFor;
		double dist = 9;
		int nextAt;
	}

	private static Apparition ghost;

	public static void tick() {
		FEASTS.removeIf(Effect::dead);
		Minecraft mc = Minecraft.getInstance();
		Domain d = trappedIn();
		if (d == null || mc.player == null) {
			ghost = null;
			return;
		}
		long now = ClientState.ticks;
		if (ghost == null) {
			ghost = new Apparition();
			ghost.nextAt = (int) now + 30;
		}
		Vec3 eye = mc.player.getEyePosition();
		Vec3 look = mc.player.getLookAngle();
		if (ghost.pos == null) {
			if (now < ghost.nextAt) return;
			Vec3 behind = new Vec3(-look.x, 0, -look.z).normalize().scale(ghost.dist);
			Vec3 side = new Vec3(-behind.z, 0, behind.x).normalize().scale((Math.random() - 0.5) * 3);
			Vec3 at = mc.player.position().add(behind).add(side);
			ghost.pos = new Vec3(at.x, mc.player.getY(), at.z);
			ghost.born = (int) now;
			ghost.seenFor = 0;
			return;
		}
		Vec3 to = ghost.pos.add(0, 2.5, 0).subtract(eye);
		boolean seen = to.normalize().dot(look) > 0.82;
		if (seen) ghost.seenFor++;
		boolean close = ghost.dist <= 2.6;
		if (ghost.seenFor > (close ? 3 : 10) || now - ghost.born > 140) {
			if (seen) {
				if (close) {
					jumpscare();
					ghost.dist = 9;
				} else {
					mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.STATIC, 0.9f, 0.5f));
					ScreenFx.flash(0x000000, 0.9f, 4);
					ghost.dist = Math.max(2.5, ghost.dist - 2.2);
				}
			} else {
				ghost.dist = Math.max(2.5, ghost.dist - 1.2);
			}
			ghost.pos = null;
			ghost.nextAt = (int) now + 30 + (int) (Math.random() * 50);
		}
	}

	public static void renderApparition(boolean add, float pt) {
		if (ghost == null || ghost.pos == null) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Vec3 to = mc.player.position().subtract(ghost.pos);
		float yaw = (float) Mth.atan2(-to.x, to.z);
		Creature.Pose ps = Creature.locomotion(ClientState.ticks + pt, 0, 0, false, 0, 666);
		ps.headPitch = -0.35f;
		ps.jaw = 0.25f + 0.2f * Mth.sin((ClientState.ticks + pt) * 0.3f);
		Creature.draw(ghost.pos, yaw, ps, 0.3f, add, 1f);
	}

	// ------------------------------------------------------------------ jumpscare

	private static long scareStart = -100;

	public static void jumpscare() {
		scareStart = ClientState.ticks;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SCREAM, 1.1f, 1.0f));
		ScreenFx.shake(2.4f, 18);
		ScreenFx.flash(0x400000, 0.7f, 22);
	}

	/** The face, filling the screen, for a moment. */
	public static void renderOverlay(GuiGraphics g, float pt) {
		float t = ClientState.ticks - scareStart + pt;
		if (t < 0 || t > 18) return;
		int w = g.guiWidth(), h = g.guiHeight();
		float a = t < 13 ? 1f : 1 - (t - 13) / 5f;
		g.fill(0, 0, w, h, ScreenFx.argb(0x000000, 0.92f * a));
		float jit = 6 * Mth.sin(t * 9f);
		float scale = h * (1.05f + 0.3f * Math.min(1f, t / 3f));
		float jaw = Math.min(1.1f, t * 0.3f);
		Emblem.renderFace(g, w / 2f + jit, h * 0.36f + jit * 0.5f, scale, jaw, t * 0.2f, a);
	}

	public static void reset() {
		FEASTS.clear();
		PROPS.clear();
		JOINTS.clear();
		DOMAINS.clear();
		ghost = null;
		scareStart = -100;
	}
}
