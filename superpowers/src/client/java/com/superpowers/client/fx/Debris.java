package com.superpowers.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.superpowers.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/** Real, textured block chunks flung out of craters. */
public final class Debris {
	private Debris() {}

	private static final int MAX = 700;
	private static final RandomSource RND = RandomSource.create();

	private static final class Chunk {
		final BlockState state;
		double x, y, z, px, py, pz, vx, vy, vz;
		final Quaternionf rot = new Quaternionf(), prevRot = new Quaternionf();
		final float ax, ay, az, size;
		int age;
		final int life;
		boolean resting;

		Chunk(BlockState state, double x, double y, double z, double vx, double vy, double vz, float size, int life) {
			this.state = state;
			this.x = px = x;
			this.y = py = y;
			this.z = pz = z;
			this.vx = vx;
			this.vy = vy;
			this.vz = vz;
			this.size = size;
			this.life = life;
			ax = (RND.nextFloat() - 0.5f) * 0.6f;
			ay = (RND.nextFloat() - 0.5f) * 0.6f;
			az = (RND.nextFloat() - 0.5f) * 0.6f;
			rot.rotateXYZ(RND.nextFloat() * 6, RND.nextFloat() * 6, RND.nextFloat() * 6);
		}
	}

	private static final List<Chunk> CHUNKS = new ArrayList<>();

	public static void handle(Net.Debris pkt) {
		Vec3 c = new Vec3(pkt.x(), pkt.y(), pkt.z());
		for (int i = 0; i < pkt.states().length && CHUNKS.size() < MAX; i++) {
			BlockState st = Block.stateById(pkt.states()[i]);
			if (st.isAir() || st.getRenderShape() != RenderShape.MODEL) continue;
			double ox = pkt.offsets()[i * 3], oy = pkt.offsets()[i * 3 + 1], oz = pkt.offsets()[i * 3 + 2];
			Vec3 out = new Vec3(ox, Math.max(0.2, oy + 1.2), oz);
			if (out.lengthSqr() < 1e-4) out = new Vec3(RND.nextGaussian(), 1, RND.nextGaussian());
			out = out.normalize();
			double sp = pkt.power() * (0.35 + RND.nextDouble() * 0.75);
			double vy = Math.abs(out.y) * sp + pkt.power() * (0.25 + RND.nextDouble() * 0.45);
			float size = 0.35f + RND.nextFloat() * 0.5f;
			CHUNKS.add(new Chunk(st, c.x + ox, c.y + oy, c.z + oz, out.x * sp, vy, out.z * sp, size, 45 + RND.nextInt(40)));
		}
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			CHUNKS.clear();
			return;
		}
		CHUNKS.removeIf(ch -> {
			ch.px = ch.x;
			ch.py = ch.y;
			ch.pz = ch.z;
			ch.prevRot.set(ch.rot);
			if (!ch.resting) {
				ch.vy -= 0.045;
				ch.vx *= 0.985;
				ch.vz *= 0.985;
				double nx = ch.x + ch.vx, ny = ch.y + ch.vy, nz = ch.z + ch.vz;
				BlockPos below = BlockPos.containing(nx, ny - ch.size * 0.4, nz);
				if (ch.vy < 0 && mc.level.getBlockState(below).blocksMotion()) {
					ny = below.getY() + 1 + ch.size * 0.4;
					ch.vy *= -0.3;
					ch.vx *= 0.6;
					ch.vz *= 0.6;
					if (Math.abs(ch.vy) < 0.08) ch.resting = true;
					if (ch.age > 3 && RND.nextInt(3) == 0) {
						mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ch.state), nx, ny, nz, 0, 0.1, 0);
					}
				}
				ch.x = nx;
				ch.y = ny;
				ch.z = nz;
				ch.rot.rotateXYZ(ch.ax, ch.ay, ch.az);
			}
			return ++ch.age >= ch.life;
		});
	}

	public static int count() {
		return CHUNKS.size();
	}

	public static void clear() {
		CHUNKS.clear();
	}

	/** Called after the effect passes with the camera rotation still in the model-view matrix. */
	public static void render(float pt) {
		if (CHUNKS.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		BlockRenderDispatcher blocks = mc.getBlockRenderer();
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		PoseStack ps = new PoseStack();
		Quaternionf q = new Quaternionf();
		for (Chunk ch : CHUNKS) {
			double x = Mth.lerp(pt, ch.px, ch.x), y = Mth.lerp(pt, ch.py, ch.y), z = Mth.lerp(pt, ch.pz, ch.z);
			float k = ch.age > ch.life - 12 ? (ch.life - ch.age - pt) / 12f : 1f;
			float s = ch.size * Math.max(0.01f, k);
			ps.pushPose();
			ps.translate(x - Draw.cx, y - Draw.cy, z - Draw.cz);
			ch.prevRot.slerp(ch.rot, pt, q);
			ps.mulPose(q);
			ps.scale(s, s, s);
			ps.translate(-0.5, -0.5, -0.5);
			int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(x, y, z));
			blocks.renderSingleBlock(ch.state, ps, buffers, light, OverlayTexture.NO_OVERLAY);
			ps.popPose();
		}
		buffers.endBatch();
	}
}
