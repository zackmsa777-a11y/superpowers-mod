package com.superpowers.client.model;

import com.superpowers.Superpowers;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A triangle mesh exported from Blender (assets/superpowers/spm/*.spm).
 * Vertex layout in {@link #solid}/{@link #glow}: x y z, nx ny nz, r g b, emissive  (STRIDE floats).
 * Model space: +X right, +Y up, +Z forward, 1 unit = 1 block.
 */
public final class Mesh {
	public static final int STRIDE = 10;

	public final float[] solid;
	public final float[] glow;
	public final float minX, minY, minZ, maxX, maxY, maxZ;

	private Mesh(float[] solid, float[] glow) {
		this.solid = solid;
		this.glow = glow;
		float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (float[] arr : new float[][]{solid, glow}) {
			for (int i = 0; i < arr.length; i += STRIDE) {
				for (int k = 0; k < 3; k++) {
					b[k] = Math.min(b[k], arr[i + k]);
					b[k + 3] = Math.max(b[k + 3], arr[i + k]);
				}
			}
		}
		boolean empty = solid.length == 0 && glow.length == 0;
		minX = empty ? 0 : b[0];
		minY = empty ? 0 : b[1];
		minZ = empty ? 0 : b[2];
		maxX = empty ? 0 : b[3];
		maxY = empty ? 0 : b[4];
		maxZ = empty ? 0 : b[5];
	}

	public int triangles() {
		return (solid.length + glow.length) / STRIDE / 3;
	}

	public float radius() {
		float r = 0;
		for (float v : new float[]{minX, minY, minZ, maxX, maxY, maxZ}) r = Math.max(r, Math.abs(v));
		return r;
	}

	// ------------------------------------------------------------------ loading

	private static final Map<String, Mesh> CACHE = new HashMap<>();
	private static final Mesh EMPTY = new Mesh(new float[0], new float[0]);

	public static Mesh get(String name) {
		Mesh m = CACHE.get(name);
		if (m == null) {
			m = load(name);
			CACHE.put(name, m);
		}
		return m;
	}

	public static void clearCache() {
		CACHE.clear();
	}

	private static Mesh load(String name) {
		try {
			Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(Superpowers.id("spm/" + name + ".spm"));
			if (res.isEmpty()) {
				Superpowers.LOGGER.warn("Missing model {}", name);
				return EMPTY;
			}
			byte[] bytes;
			try (InputStream in = res.get().open()) {
				bytes = in.readAllBytes();
			}
			ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
			if (buf.get() != 'S' || buf.get() != 'P' || buf.get() != 'M' || buf.get() != '1') throw new IllegalStateException("bad magic");
			int parts = buf.getInt();
			float[] solid = new float[0], glow = new float[0];
			for (int p = 0; p < parts; p++) {
				int flags = buf.getInt();
				int count = buf.getInt();
				float[] data = new float[count * STRIDE];
				for (int i = 0; i < count; i++) {
					int o = i * STRIDE;
					for (int k = 0; k < 6; k++) data[o + k] = buf.getFloat();
					data[o + 6] = (buf.get() & 255) / 255f;
					data[o + 7] = (buf.get() & 255) / 255f;
					data[o + 8] = (buf.get() & 255) / 255f;
					buf.get(); // alpha
					data[o + 9] = (buf.get() & 255) / 255f;
					buf.position(buf.position() + 3);
				}
				if ((flags & 1) != 0) glow = data;
				else solid = data;
			}
			Mesh m = new Mesh(solid, glow);
			Superpowers.LOGGER.info("Loaded model {} ({} tris)", name, m.triangles());
			return m;
		} catch (Exception e) {
			Superpowers.LOGGER.error("Failed to load model " + name, e);
			return EMPTY;
		}
	}
}
