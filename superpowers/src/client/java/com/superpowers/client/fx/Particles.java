package com.superpowers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Vanilla particle bursts layered under the custom geometry. */
public final class Particles {
	private Particles() {}

	static final RandomSource RND = RandomSource.create();

	static ClientLevel level() {
		return Minecraft.getInstance().level;
	}

	public static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(new Vector3f(Draw.r(rgb), Draw.g(rgb), Draw.b(rgb)), size);
	}

	public static void add(ParticleOptions p, Vec3 pos, Vec3 vel) {
		ClientLevel l = level();
		if (l != null) l.addParticle(p, true, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
	}

	static Vec3 randDir() {
		return new Vec3(RND.nextGaussian(), RND.nextGaussian(), RND.nextGaussian()).normalize();
	}

	/** Spherical burst. */
	public static void burst(ParticleOptions p, Vec3 c, int n, double spread, double speed) {
		for (int i = 0; i < n; i++) {
			Vec3 d = randDir();
			add(p, c.add(d.scale(spread * RND.nextDouble())), d.scale(speed * (0.4 + RND.nextDouble() * 0.6)));
		}
	}

	/** Flat ring moving outward. */
	public static void ring(ParticleOptions p, Vec3 c, double r, int n, double out, double up) {
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n + RND.nextDouble() * 0.2;
			Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
			add(p, c.add(d.scale(r)), d.scale(out).add(0, up, 0));
		}
	}

	/** Cone of particles along dir. */
	public static void cone(ParticleOptions p, Vec3 c, Vec3 dir, int n, double speed, double spread) {
		dir = dir.normalize();
		for (int i = 0; i < n; i++) {
			Vec3 d = dir.add(randDir().scale(spread)).normalize();
			add(p, c, d.scale(speed * (0.5 + RND.nextDouble() * 0.7)));
		}
	}

	/** Column rising from c. */
	public static void column(ParticleOptions p, Vec3 c, double r, int n, double up) {
		for (int i = 0; i < n; i++) {
			double a = RND.nextDouble() * Math.PI * 2, rr = RND.nextDouble() * r;
			add(p, c.add(Math.cos(a) * rr, RND.nextDouble() * 0.5, Math.sin(a) * rr), new Vec3(0, up * (0.5 + RND.nextDouble()), 0));
		}
	}

	/** Debris made of the block under the point. */
	public static void debris(Vec3 c, int n, double speed) {
		ClientLevel l = level();
		if (l == null) return;
		BlockPos bp = BlockPos.containing(c.x, c.y - 0.5, c.z);
		BlockState state = l.getBlockState(bp);
		if (state.isAir()) {
			bp = bp.below();
			state = l.getBlockState(bp);
		}
		if (state.isAir()) return;
		BlockParticleOption opt = new BlockParticleOption(ParticleTypes.BLOCK, state);
		for (int i = 0; i < n; i++) {
			double a = RND.nextDouble() * Math.PI * 2;
			Vec3 v = new Vec3(Math.cos(a) * speed * RND.nextDouble(), speed * (0.6 + RND.nextDouble()), Math.sin(a) * speed * RND.nextDouble());
			add(opt, c.add(Math.cos(a) * RND.nextDouble(), 0.1, Math.sin(a) * RND.nextDouble()), v);
		}
	}
}
