package com.superpowers.power;

import com.superpowers.net.Net;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Craters, scorched ground and flying debris. Removal is spread over ticks so big blasts don't freeze the server. */
public final class Destruction {
	private Destruction() {}

	public static GameRules.Key<GameRules.BooleanValue> RULE;

	public static final double MAX_RADIUS = 18;
	private static final int BUDGET_PER_TICK = 3500;
	private static final RandomSource RND = RandomSource.create();

	private record Job(ServerLevel level, List<BlockPos> blocks, int[] index) {}

	private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();

	public static void init() {
		RULE = GameRuleRegistry.register("superpowersBlockBreaking", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));
	}

	public static boolean enabled(ServerLevel level) {
		return RULE != null && level.getGameRules().getBoolean(RULE);
	}

	static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
		float hardness = state.getDestroySpeed(level, pos);
		// negative hardness = unbreakable (bedrock, barriers, portal frames); reinforced deepslate (55) and below can go
		return hardness >= 0 && hardness < 60;
	}

	/**
	 * Carves a noisy ellipsoid crater.
	 *
	 * @param depth  vertical radius as a fraction of r (0.5 = shallow bowl)
	 * @param fire   chance (0..1) to set fire on the rim
	 * @param debris how many chunks to fling
	 */
	public static void crater(ServerLevel level, Entity source, Vec3 c, double r, double depth, float fire, int debris) {
		if (!enabled(level) || r < 0.6) return;
		r = Math.min(r, MAX_RADIUS);
		double ry = Math.max(0.6, r * depth);
		int R = (int) Math.ceil(r * 1.2), RY = (int) Math.ceil(ry * 1.2);
		BlockPos center = BlockPos.containing(c);
		List<BlockPos> hits = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		long seed = center.asLong();
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				for (int dy = -RY; dy <= RY; dy++) {
					double nx = dx / r, ny = dy / ry, nz = dz / r;
					double d = nx * nx + ny * ny + nz * nz;
					double wobble = 1 + 0.18 * (Mth.sin((float) (dx * 1.7 + seed % 13)) * Mth.cos((float) (dz * 1.3 + dy * 0.7)));
					if (d > wobble * wobble) continue;
					m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState st = level.getBlockState(m);
					if (breakable(level, m, st)) hits.add(m.immutable());
				}
			}
		}
		if (hits.isEmpty()) return;
		sendDebris(level, c, hits, debris, (float) Math.min(1.8, 0.45 + r * 0.09));
		hits.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(c)));
		QUEUE.add(new Job(level, hits, new int[]{0}));
		if (fire > 0) scorch(level, source, c, r, fire);
	}

	/** Breaks the blocks along a cylinder (beams/pillars), top to bottom. */
	public static void shaft(ServerLevel level, Vec3 c, double r, int down, int up, int debris) {
		if (!enabled(level)) return;
		r = Math.min(r, MAX_RADIUS);
		BlockPos center = BlockPos.containing(c);
		List<BlockPos> hits = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int R = (int) Math.ceil(r);
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				if (dx * dx + dz * dz > r * r) continue;
				int depth = down - (int) ((dx * dx + dz * dz) / (r * r) * down * 0.6);
				for (int dy = -depth; dy <= up; dy++) {
					m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState st = level.getBlockState(m);
					if (breakable(level, m, st)) hits.add(m.immutable());
				}
			}
		}
		if (hits.isEmpty()) return;
		sendDebris(level, c, hits, debris, 1.2f);
		QUEUE.add(new Job(level, hits, new int[]{0}));
	}

	/** Sets fire on exposed tops around a point and credits Fire users' meters. */
	public static void scorch(ServerLevel level, Entity source, Vec3 c, double r, float chance) {
		if (!enabled(level)) return;
		int tries = (int) Math.min(80, r * 8);
		int placed = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int i = 0; i < tries; i++) {
			if (RND.nextFloat() > chance) continue;
			double a = RND.nextDouble() * Math.PI * 2, rr = r * (0.7 + RND.nextDouble() * 0.8);
			int x = Mth.floor(c.x + Math.cos(a) * rr), z = Mth.floor(c.z + Math.sin(a) * rr);
			for (int y = Mth.floor(c.y) + 4; y > c.y - 6; y--) {
				m.set(x, y, z);
				BlockState below = level.getBlockState(m.below());
				if (level.getBlockState(m).isAir() && below.isSolidRender(level, m.below())) {
					BlockState fire = BaseFireBlock.getState(level, m);
					if (fire.canSurvive(level, m)) {
						level.setBlock(m, fire, Block.UPDATE_ALL);
						placed++;
					}
					break;
				}
			}
		}
		if (placed > 0 && source instanceof ServerPlayer sp) Awakening.onSetFire(sp, placed);
	}

	private static void sendDebris(ServerLevel level, Vec3 c, List<BlockPos> hits, int count, float power) {
		if (count <= 0) return;
		count = Math.min(count, Math.min(120, hits.size()));
		// prefer blocks near the surface: they are the ones you'd see fly
		List<BlockPos> pool = new ArrayList<>(hits);
		pool.sort(Comparator.comparingInt(p -> -p.getY()));
		int n = Math.min(pool.size(), count * 3);
		int[] states = new int[count];
		float[] offs = new float[count * 3];
		for (int i = 0; i < count; i++) {
			BlockPos p = pool.get(RND.nextInt(n));
			states[i] = Block.getId(level.getBlockState(p));
			offs[i * 3] = (float) (p.getX() + 0.5 - c.x);
			offs[i * 3 + 1] = (float) (p.getY() + 0.5 - c.y);
			offs[i * 3 + 2] = (float) (p.getZ() + 0.5 - c.z);
		}
		Net.Debris payload = new Net.Debris(c.x, c.y, c.z, power, states, offs);
		for (ServerPlayer pl : PlayerLookup.around(level, c, 160)) ServerPlayNetworking.send(pl, payload);
	}

	public static void tick() {
		int budget = BUDGET_PER_TICK;
		BlockState air = Blocks.AIR.defaultBlockState();
		while (budget > 0 && !QUEUE.isEmpty()) {
			Job job = QUEUE.peek();
			int i = job.index[0];
			for (; i < job.blocks.size() && budget > 0; i++, budget--) {
				BlockPos p = job.blocks.get(i);
				BlockState st = job.level.getBlockState(p);
				if (breakable(job.level, p, st)) {
					job.level.setBlock(p, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			}
			job.index[0] = i;
			if (i >= job.blocks.size()) QUEUE.poll();
		}
	}

	public static void clear() {
		QUEUE.clear();
	}
}
