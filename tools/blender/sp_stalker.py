# Entity 666 "Hollow Stalker": rig + skin + export of the Meshy model (hollow_stalker_meshy.blend).
# Builds a 20-bone skeleton inside the crouched model, decimates it to ~26k triangles, binds it with
# automatic weights and writes:
#   stalker.sps  - skinned mesh (bones, sockets, vertices with uv + 4 weights, indices)
#   stalker.png  - 1024px base colour texture
# to <temp>/sp_spm/. Copy them to superpowers/src/client/resources/assets/superpowers/spm/ and
# .../textures/entity/. Run in Blender 5.x with tools/blender as the working directory.
import bpy, os, struct, tempfile
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else os.getcwd()
SRC = os.path.join(HERE, "hollow_stalker_meshy.blend")
OUT = os.path.join(tempfile.gettempdir(), "sp_spm")
os.makedirs(OUT, exist_ok=True)

# Blender model space (front = -Y). "px" = the +X side (the player's right in game).
BONES = [
    ("pelvis", (-0.12, 0.40, 0.28), (-0.06, 0.27, 0.60), None),
    ("spine", (-0.06, 0.27, 0.60), (0.0, 0.03, 0.68), "pelvis"),
    ("chest", (0.0, 0.03, 0.68), (0.02, -0.20, 0.68), "spine"),
    ("neck", (0.02, -0.20, 0.68), (0.03, -0.30, 0.70), "chest"),
    ("head", (0.03, -0.30, 0.70), (0.03, -0.40, 0.93), "neck"),
    ("jaw", (0.03, -0.36, 0.66), (0.03, -0.48, 0.54), "head"),
    ("clav_px", (0.06, 0.0, 0.68), (0.28, 0.0, 0.64), "chest"),
    ("uparm_px", (0.28, 0.0, 0.64), (0.37, 0.22, 0.14), "clav_px"),
    ("forearm_px", (0.37, 0.22, 0.14), (0.33, -0.47, -0.31), "uparm_px"),
    ("hand_px", (0.33, -0.47, -0.31), (0.36, -0.62, -0.75), "forearm_px"),
    ("clav_nx", (-0.04, 0.0, 0.68), (-0.30, 0.0, 0.64), "chest"),
    ("uparm_nx", (-0.30, 0.0, 0.64), (-0.54, 0.11, 0.21), "clav_nx"),
    ("forearm_nx", (-0.54, 0.11, 0.21), (-0.27, -0.45, -0.33), "uparm_nx"),
    ("hand_nx", (-0.27, -0.45, -0.33), (-0.25, -0.60, -0.78), "forearm_nx"),
    ("thigh_px", (-0.02, 0.40, 0.24), (0.19, 0.08, -0.20), "pelvis"),
    ("shin_px", (0.19, 0.08, -0.20), (0.15, 0.59, -0.75), "thigh_px"),
    ("foot_px", (0.15, 0.59, -0.75), (0.15, 0.12, -0.93), "shin_px"),
    ("thigh_nx", (-0.25, 0.40, 0.24), (-0.38, 0.08, -0.19), "pelvis"),
    ("shin_nx", (-0.38, 0.08, -0.19), (-0.44, 0.59, -0.75), "thigh_nx"),
    ("foot_nx", (-0.44, 0.59, -0.75), (-0.44, 0.12, -0.93), "shin_nx"),
]
# eye glow points, mouth and chin (Blender space, rest pose)
SOCKETS = [
    ("eye_px", "head", (0.095, -0.539, 0.81)),
    ("eye_nx", "head", (-0.02, -0.531, 0.815)),
    ("mouth", "head", (0.03, -0.39, 0.62)),
    ("chin", "jaw", (0.03, -0.48, 0.55)),
]
SCALE, ZOFF = 1.55, 0.95   # crouched height ~2.95 blocks, feet on y = 0
DECIMATE = 0.24


def mc(p):
    return (SCALE * p[0], SCALE * (p[2] + ZOFF), -SCALE * p[1])


def mcn(n):
    return (n[0], n[2], -n[1])


def build():
    scn = bpy.data.scenes.get("SP_Stalker") or bpy.data.scenes.new("SP_Stalker")
    for w in bpy.context.window_manager.windows:
        w.scene = scn
    vl = scn.view_layers[0]
    with bpy.data.libraries.load(SRC, link=False) as (src, dst):
        dst.objects = list(src.objects)
    mesh = next(o for o in dst.objects if o is not None and o.type == 'MESH')
    scn.collection.objects.link(mesh)
    mesh.name = "SP_Stalker"
    dec = mesh.modifiers.new("dec", 'DECIMATE')
    dec.ratio = DECIMATE
    dec.use_collapse_triangulate = True
    with bpy.context.temp_override(scene=scn, view_layer=vl, active_object=mesh, object=mesh, selected_objects=[mesh]):
        vl.objects.active = mesh
        bpy.ops.object.modifier_apply(modifier="dec")
    arm = bpy.data.armatures.new("SP_Rig")
    rig = bpy.data.objects.new("SP_Rig", arm)
    scn.collection.objects.link(rig)
    with bpy.context.temp_override(scene=scn, view_layer=vl, active_object=rig, object=rig, selected_objects=[rig]):
        vl.objects.active = rig
        bpy.ops.object.mode_set(mode='EDIT')
        for n, h, t, par in BONES:
            b = arm.edit_bones.new(n)
            b.head, b.tail = Vector(h), Vector(t)
            if par:
                b.parent = arm.edit_bones[par]
        bpy.ops.object.mode_set(mode='OBJECT')
    for o in scn.objects:
        o.select_set(False)
    mesh.select_set(True)
    rig.select_set(True)
    vl.objects.active = rig
    with bpy.context.temp_override(scene=scn, view_layer=vl, active_object=rig, object=rig,
                                   selected_objects=[mesh, rig], selected_editable_objects=[mesh, rig]):
        bpy.ops.object.parent_set(type='ARMATURE_AUTO')
    return mesh, rig


def export(mesh, rig):
    bones = list(rig.data.bones)
    bidx = {b.name: i for i, b in enumerate(bones)}
    img = next(n.image for n in mesh.data.materials[0].node_tree.nodes
               if n.type == 'TEX_IMAGE' and any(l.to_socket.name == "Base Color" for l in n.outputs[0].links))
    W, H = img.size
    px = img.pixels[:]

    def sample(u, v):
        x = min(W - 1, max(0, int(u * W)))
        y = min(H - 1, max(0, int(v * H)))
        i = (y * W + x) * 4
        return px[i:i + 3]

    me = mesh.data
    me.calc_loop_triangles()
    uvl = me.uv_layers.active.data
    groups = {g.index: g.name for g in mesh.vertex_groups}

    def weights(v):
        ws = sorted([(g.weight, bidx[groups[g.group]]) for g in v.groups
                     if groups.get(g.group) in bidx and g.weight > 0.001], reverse=True)[:4]
        t = sum(w for w, _ in ws) or 1
        return [(w / t, b) for w, b in ws] + [(0.0, 0)] * (4 - len(ws))

    verts, key, idx = [], {}, []
    for tri in me.loop_triangles:
        for li in tri.loops:
            vi = me.loops[li].vertex_index
            uv = uvl[li].uv
            k = (vi, round(uv.x, 5), round(uv.y, 5))
            if k not in key:
                v = me.vertices[vi]
                key[k] = len(verts)
                verts.append((v.co.copy(), v.normal.copy(), (uv.x, 1 - uv.y), sample(uv.x, uv.y), weights(v)))
            idx.append(key[k])
    with open(os.path.join(OUT, "stalker.sps"), "wb") as f:
        f.write(b"SPS1")

        def s(t):
            b = t.encode()
            f.write(struct.pack("<i", len(b)))
            f.write(b)

        f.write(struct.pack("<i", len(bones)))
        for b in bones:
            s(b.name)
            f.write(struct.pack("<i", bidx[b.parent.name] if b.parent else -1))
            f.write(struct.pack("<3f", *mc(b.head_local)))
        f.write(struct.pack("<i", len(SOCKETS)))
        for nm, bn, p in SOCKETS:
            s(nm)
            f.write(struct.pack("<i", bidx[bn]))
            f.write(struct.pack("<3f", *mc(p)))
        f.write(struct.pack("<i", len(verts)))
        for co, n, uv, col, ws in verts:
            f.write(struct.pack("<3f3f2f", *mc(co), *mcn(n), *uv))
            f.write(bytes([int(max(0, min(1, c)) * 255) for c in col] + [255]))
            f.write(bytes([b for _, b in ws]))
            f.write(struct.pack("<4f", *[w for w, _ in ws]))
        f.write(struct.pack("<i", len(idx)))
        f.write(struct.pack("<%di" % len(idx), *idx))
    tex = img.copy()
    tex.scale(1024, 1024)
    tex.filepath_raw = os.path.join(OUT, "stalker.png")
    tex.file_format = 'PNG'
    tex.save()
    bpy.data.images.remove(tex)
    print("stalker exported:", len(verts), "verts", len(idx) // 3, "tris ->", OUT)


mesh, rig = build()
export(mesh, rig)
