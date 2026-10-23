package com.superpowers.client.model;

import com.superpowers.Superpowers;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Vector3f;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A skinned, textured mesh (assets/superpowers/spm/*.sps): bones with pivots in model space, named sockets,
 * and indexed vertices with up to four bone weights each. Rest pose = identity, so a bone's global
 * transform is also its skinning matrix. Model space: +X right, +Y up, +Z forward, 1 unit = 1 block.
 */
public final class Skin {
	public final String[] boneNames;
	public final int[] parents;
	public final Vector3f[] pivots;
	public final Map<String, Socket> sockets = new HashMap<>();
	public final int vertCount;
	/** x y z per vertex. */
	public final float[] pos, nrm;
	/** u v per vertex. */
	public final float[] uv;
	/** baked r g b (0..1) per vertex, for drawing without the texture. */
	public final float[] col;
	public final int[] bones;
	public final float[] weights;
	/** Bone with the largest weight. */
	public final int[] dominant;
	public final int[] indices;

	public record Socket(int bone, Vector3f pos) {}

	private Skin(ByteBuffer buf) {
		int nb = buf.getInt();
		boneNames = new String[nb];
		parents = new int[nb];
		pivots = new Vector3f[nb];
		for (int i = 0; i < nb; i++) {
			boneNames[i] = str(buf);
			parents[i] = buf.getInt();
			pivots[i] = new Vector3f(buf.getFloat(), buf.getFloat(), buf.getFloat());
		}
		int ns = buf.getInt();
		for (int i = 0; i < ns; i++) {
			String n = str(buf);
			int b = buf.getInt();
			sockets.put(n, new Socket(b, new Vector3f(buf.getFloat(), buf.getFloat(), buf.getFloat())));
		}
		int nv = buf.getInt();
		vertCount = nv;
		pos = new float[nv * 3];
		nrm = new float[nv * 3];
		uv = new float[nv * 2];
		col = new float[nv * 3];
		bones = new int[nv * 4];
		weights = new float[nv * 4];
		dominant = new int[nv];
		for (int i = 0; i < nv; i++) {
			for (int k = 0; k < 3; k++) pos[i * 3 + k] = buf.getFloat();
			for (int k = 0; k < 3; k++) nrm[i * 3 + k] = buf.getFloat();
			uv[i * 2] = buf.getFloat();
			uv[i * 2 + 1] = buf.getFloat();
			for (int k = 0; k < 3; k++) col[i * 3 + k] = (buf.get() & 255) / 255f;
			buf.get();
			for (int k = 0; k < 4; k++) bones[i * 4 + k] = buf.get() & 255;
			float best = -1;
			for (int k = 0; k < 4; k++) {
				float w = buf.getFloat();
				weights[i * 4 + k] = w;
				if (w > best) {
					best = w;
					dominant[i] = bones[i * 4 + k];
				}
			}
		}
		int ni = buf.getInt();
		indices = new int[ni];
		for (int i = 0; i < ni; i++) indices[i] = buf.getInt();
	}

	static String str(ByteBuffer buf) {
		byte[] b = new byte[buf.getInt()];
		buf.get(b);
		return new String(b, StandardCharsets.UTF_8);
	}

	public int bone(String name) {
		for (int i = 0; i < boneNames.length; i++) {
			if (boneNames[i].equals(name)) return i;
		}
		return -1;
	}

	private static final Map<String, Skin> CACHE = new HashMap<>();

	/** Loads (once) a skinned model; null if it is missing or broken. */
	public static Skin get(String name) {
		if (CACHE.containsKey(name)) return CACHE.get(name);
		Skin s = null;
		try {
			Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(Superpowers.id("spm/" + name + ".sps"));
			if (res.isPresent()) {
				byte[] bytes;
				try (InputStream in = res.get().open()) {
					bytes = in.readAllBytes();
				}
				ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
				if (buf.get() != 'S' || buf.get() != 'P' || buf.get() != 'S' || buf.get() != '1') throw new IllegalStateException("bad magic");
				s = new Skin(buf);
				Superpowers.LOGGER.info("Loaded skinned model {} ({} verts, {} tris, {} bones)", name, s.vertCount, s.indices.length / 3, s.boneNames.length);
			} else {
				Superpowers.LOGGER.warn("Missing skinned model {}", name);
			}
		} catch (Exception e) {
			Superpowers.LOGGER.error("Failed to load skinned model " + name, e);
		}
		CACHE.put(name, s);
		return s;
	}

	public static void clearCache() {
		CACHE.clear();
	}
}
