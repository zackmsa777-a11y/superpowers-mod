package com.superpowers.client.screen;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.superpowers.client.fx.Draw;
import com.superpowers.client.model.Mesh;
import com.superpowers.power.PowerType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Rotating 3D power emblems (Blender models) drawn in GUI space with lighting and painter's sorting. */
public final class Emblem {
	private Emblem() {}

	private static final Vector3f LIGHT = new Vector3f(-0.45f, 0.65f, 0.65f).normalize();

	record Part(Mesh mesh, Matrix4f m, int tint, float tintAmt) {}

	/** Model parts for a power at a given time; units roughly fit a radius-1 sphere. */
	static List<Part> parts(PowerType type, float time, boolean small) {
		List<Part> out = new ArrayList<>();
		float spin = time * 0.035f;
		switch (type) {
			case FIRE -> {
				Mesh crystal = Mesh.get("fire_crystal");
				out.add(new Part(crystal, new Matrix4f().rotateY(spin * 1.5f).scale(0.95f), 0, 0));
				if (!small) out.add(new Part(Mesh.get("flame_crown"), new Matrix4f().translate(0, -0.95f, 0).rotateY(-spin).scale(2.2f, 1.4f, 2.2f), 0, 0));
			}
			case SPACE -> {
				out.add(new Part(Mesh.get("planet"), new Matrix4f().rotateY(spin).rotateX(0.35f).scale(1.05f), 0, 0));
				if (!small) {
					float a = time * 0.06f;
					out.add(new Part(Mesh.get("planet"), new Matrix4f().translate((float) Math.cos(a) * 1.25f, 0.35f, (float) Math.sin(a) * 1.25f)
							.rotateY(spin * 2).scale(0.22f), 0x5CD6FF, 0.6f));
				}
			}
			case ANGEL -> {
				out.add(new Part(Mesh.get("halo"), new Matrix4f().translate(0, 0.7f, 0).rotateY(spin).scale(2.0f), 0, 0));
				if (!small) {
					for (int s = -1; s <= 1; s += 2) {
						float flap = 0.25f * (float) Math.sin(time * 0.12f);
						out.add(new Part(Mesh.get("angel_wing"), new Matrix4f().rotateY(spin * 0.25f).translate(0.08f * s, 0.25f, -0.1f)
								.rotateY(s * 0.35f).rotateZ(s * (0.25f + flap)).scale(s * 0.42f, 0.42f, 0.42f), 0, 0));
					}
				}
			}
			case DEMON -> {
				out.add(new Part(Mesh.get("demon_core"), new Matrix4f().rotateY(spin * 1.3f).rotateX(spin * 0.7f).scale(0.85f), 0, 0));
				if (!small) {
					for (int s = -1; s <= 1; s += 2) {
						out.add(new Part(Mesh.get("demon_horn"), new Matrix4f().rotateY(spin * 0.3f).translate(0.32f * s, 0.55f, 0).scale(s * 1.3f, 1.3f, 1.3f), 0, 0));
					}
				}
			}
			case GOD -> {
				Mesh ring = Mesh.get("god_ring");
				float[] r = {0.55f, 0.75f, 0.95f};
				for (int i = 0; i < 3; i++) {
					float s = time * (0.04f + i * 0.02f) + i;
					out.add(new Part(ring, new Matrix4f().rotateY(s * 0.7f + i).rotateX(s).rotateZ(s * 0.4f).scale(r[i]), 0, 0));
				}
				if (!small) out.add(new Part(Mesh.get("god_disc"), new Matrix4f().rotateY(spin * 0.5f).scale(0.55f), 0, 0));
			}
			case ENTITY -> {
				Matrix4f head = new Matrix4f().rotateY((float) Math.sin(time * 0.03f) * 0.6f).scale(small ? 3.0f : 3.3f).translate(0, -0.26f, -0.04f);
				out.add(new Part(Mesh.get("entity_head"), head, 0, 0));
				float jaw = 0.2f + 0.25f * (float) Math.max(0, Math.sin(time * 0.05f));
				out.add(new Part(Mesh.get("entity_jaw"), new Matrix4f(head).translate(0, 0.205f, 0.02f).rotateX(jaw), 0, 0));
			}
			default -> {}
		}
		return out;
	}

	/** Draws the emblem centered at (cx, cy) with the given pixel scale. */
	public static void render(GuiGraphics g, PowerType type, float cx, float cy, float scale, float time, float alpha) {
		if (type == PowerType.NONE) return;
		draw(g, parts(type, time, scale < 14), type.color, type.color2, cx, cy, scale, alpha, 0.35f + 0.08f * (float) Math.sin(time * 0.05f));
	}

	/** Entity 666's face with the jaw at {@code jaw} radians, for the jumpscare. */
	public static void renderFace(GuiGraphics g, float cx, float cy, float scale, float jaw, float time, float alpha) {
		Matrix4f head = new Matrix4f().rotateY(0.12f * (float) Math.sin(time * 3)).rotateZ(0.1f * (float) Math.sin(time * 5)).translate(0, -0.27f, -0.05f);
		List<Part> p = List.of(
				new Part(Mesh.get("entity_head"), head, 0, 0),
				new Part(Mesh.get("entity_jaw"), new Matrix4f(head).translate(0, 0.205f, 0.02f).rotateX(jaw), 0, 0));
		draw(g, p, 0x500000, 0x200000, cx, cy, scale, alpha, 0.05f);
	}

	/** The SP Book itself, slowly turning. */
	public static void renderBook(GuiGraphics g, float cx, float cy, float scale, float time) {
		List<Part> p = List.of(new Part(Mesh.get("codex_book"), new Matrix4f().rotateY((float) Math.sin(time * 0.03f) * 0.7f + 0.35f), 0, 0));
		draw(g, p, 0x9A5CFF, 0xFFC93C, cx, cy, scale, 1f, 0.15f);
	}

	static void draw(GuiGraphics g, List<Part> parts, int glow1, int glow2, float cx, float cy, float scale, float alpha, float tilt) {
		g.flush();
		Matrix4f gm = g.pose().last().pose();

		RenderSystem.enableBlend();
		RenderSystem.disableDepthTest();
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
		glowDisc(bb, gm, cx, cy, scale * 2.0f, glow1, 0.5f * alpha);
		glowDisc(bb, gm, cx, cy, scale * 1.0f, glow2, 0.3f * alpha);
		end(bb);

		// transform + light every triangle, then sort back to front
		Matrix4f view = new Matrix4f().rotateX(tilt);
		List<float[]> tris = new ArrayList<>();
		Vector3f p = new Vector3f(), n = new Vector3f();
		Matrix3f nm = new Matrix3f();
		for (Part part : parts) {
			Matrix4f m = new Matrix4f(view).mul(part.m);
			nm.set(m).invert().transpose();
			float tr = Draw.r(part.tint), tg = Draw.g(part.tint), tb = Draw.b(part.tint);
			float[] d = part.mesh.solid;
			for (int pass = 0; pass < 2; pass++) {
				if (pass == 1) d = part.mesh.glow;
				for (int t = 0; t < d.length; t += Mesh.STRIDE * 3) {
					float[] tri = new float[19];
					float z = 0;
					for (int v = 0; v < 3; v++) {
						int o = t + v * Mesh.STRIDE;
						m.transformPosition(d[o], d[o + 1], d[o + 2], p);
						nm.transform(d[o + 3], d[o + 4], d[o + 5], n);
						float len = n.length();
						float diff = len > 1e-6f ? Math.abs(n.dot(LIGHT)) / len : 0.6f;
						float rim = len > 1e-6f ? (float) Math.pow(1 - Math.abs(n.z / len), 3) * 0.35f : 0f;
						float e = d[o + 9];
						float r = d[o + 6], gg = d[o + 7], b = d[o + 8];
						if (part.tintAmt > 0) {
							float lum = (0.3f * r + 0.59f * gg + 0.11f * b) * 1.35f;
							r += (Math.min(1, tr * lum) - r) * part.tintAmt;
							gg += (Math.min(1, tg * lum) - gg) * part.tintAmt;
							b += (Math.min(1, tb * lum) - b) * part.tintAmt;
						}
						float k = (0.32f + 0.75f * diff) * (1 - e) + 1.1f * e;
						tri[v * 3] = p.x;
						tri[v * 3 + 1] = p.y;
						tri[v * 3 + 2] = p.z;
						tri[9 + v * 3] = Math.min(1, r * k + rim * Draw.r(glow2));
						tri[9 + v * 3 + 1] = Math.min(1, gg * k + rim * Draw.g(glow2));
						tri[9 + v * 3 + 2] = Math.min(1, b * k + rim * Draw.b(glow2));
						z += p.z;
					}
					tri[18] = z;
					tris.add(tri);
				}
			}
		}
		tris.sort((a, b) -> Float.compare(a[18], b[18]));
		RenderSystem.defaultBlendFunc();
		bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
		for (float[] tri : tris) {
			for (int v = 0; v < 3; v++) {
				bb.addVertex(gm, cx + tri[v * 3] * scale, cy - tri[v * 3 + 1] * scale, 50)
						.setColor(tri[9 + v * 3], tri[9 + v * 3 + 1], tri[9 + v * 3 + 2], alpha);
			}
		}
		end(bb);
		RenderSystem.enableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.defaultBlendFunc();
	}

	static void glowDisc(BufferBuilder bb, Matrix4f m, float cx, float cy, float r, int rgb, float a) {
		int n = 32;
		for (int i = 0; i < n; i++) {
			double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
			bb.addVertex(m, cx, cy, 40).setColor(Draw.r(rgb), Draw.g(rgb), Draw.b(rgb), a);
			bb.addVertex(m, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, 40).setColor(Draw.r(rgb), Draw.g(rgb), Draw.b(rgb), 0f);
			bb.addVertex(m, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, 40).setColor(Draw.r(rgb), Draw.g(rgb), Draw.b(rgb), 0f);
		}
	}

	static void end(BufferBuilder bb) {
		MeshData mesh = bb.build();
		if (mesh != null) BufferUploader.drawWithShader(mesh);
	}
}
