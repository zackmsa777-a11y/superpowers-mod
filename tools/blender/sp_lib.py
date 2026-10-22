# Superpowers model toolkit for Blender 5.x.
# Models are built procedurally, previewed in the "SP_Models" scene and exported to the mod's
# .spm format (triangles with normal, sRGB color and emissive factor; solid + additive-glow parts).
#
# Coordinates: 1 Blender metre = 1 Minecraft block. Model front is Blender -Y, up is +Z.
# Exported as Minecraft model space: +X right, +Y up, +Z forward  ->  (x, z, -y).
import bpy, bmesh, math, random, struct, os
from mathutils import Vector, Matrix, noise

HERE = os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else os.getcwd()
exec(open(os.path.join(HERE, "sp_geom.py"), encoding="utf-8").read())

# The project path is too long for Blender's file API, so models go to a short temp folder first.
import tempfile
OUT = os.path.join(tempfile.gettempdir(), "sp_spm")
os.makedirs(OUT, exist_ok=True)


def hexc(h):
    return ((h >> 16 & 255) / 255.0, (h >> 8 & 255) / 255.0, (h & 255) / 255.0)


def mixc(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def lin(c):
    return tuple(x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in c)


# ------------------------------------------------------------------ scene

def sp_scene():
    scn = bpy.data.scenes.get("SP_Models") or bpy.data.scenes.new("SP_Models")
    for w in bpy.context.window_manager.windows:
        w.scene = scn
    scn.render.engine = 'BLENDER_EEVEE'
    if scn.world is None:
        scn.world = bpy.data.worlds.new("SP_World")
    return scn


def sp_collection(name):
    scn = sp_scene()
    col = bpy.data.collections.get(name)
    if col is None:
        col = bpy.data.collections.new(name)
        scn.collection.children.link(col)
    else:
        for o in list(col.objects):
            bpy.data.objects.remove(o, do_unlink=True)
        if col.name not in scn.collection.children:
            scn.collection.children.link(col)
    return col


def preview_material(glow=False):
    name = "SP_Glow" if glow else "SP_Solid"
    m = bpy.data.materials.get(name)
    if m is not None:
        return m
    m, nt = fresh_material(name)
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    col = nt.nodes.new("ShaderNodeAttribute")
    col.attribute_type = 'GEOMETRY'
    col.attribute_name = "spcol"
    em = nt.nodes.new("ShaderNodeAttribute")
    em.attribute_type = 'GEOMETRY'
    em.attribute_name = "spemit"
    if glow:
        e = nt.nodes.new("ShaderNodeEmission")
        e.inputs["Strength"].default_value = 3.0
        nt.links.new(col.outputs["Color"], e.inputs["Color"])
        tr = nt.nodes.new("ShaderNodeBsdfTransparent")
        add = nt.nodes.new("ShaderNodeAddShader")
        nt.links.new(e.outputs[0], add.inputs[0])
        nt.links.new(tr.outputs[0], add.inputs[1])
        nt.links.new(add.outputs[0], out.inputs["Surface"])
        m.surface_render_method = 'BLENDED'
    else:
        b = nt.nodes.new("ShaderNodeBsdfPrincipled")
        b.inputs["Roughness"].default_value = 0.45
        nt.links.new(col.outputs["Color"], b.inputs["Base Color"])
        nt.links.new(col.outputs["Color"], b.inputs["Emission Color"])
        mul = nt.nodes.new("ShaderNodeMath")
        mul.operation = 'MULTIPLY'
        mul.inputs[1].default_value = 3.0
        nt.links.new(em.outputs["Fac"], mul.inputs[0])
        nt.links.new(mul.outputs[0], b.inputs["Emission Strength"])
        nt.links.new(b.outputs[0], out.inputs["Surface"])
    m.use_backface_culling = False
    return m


class Geo:
    """Mesh accumulator with per-vertex sRGB color and emissive factor."""

    def __init__(self):
        self.v, self.f, self.col, self.em = [], [], [], []

    def add(self, verts, faces, color=(1, 1, 1), emit=0.0, cfun=None, efun=None, xf=None):
        o = len(self.v)
        for i, p in enumerate(verts):
            p = Vector(p)
            if xf is not None:
                p = xf @ p
            self.v.append(p)
            self.col.append(cfun(i, p) if cfun else color)
            self.em.append(efun(i, p) if efun else emit)
        self.f += [tuple(i + o for i in fc) for fc in faces]
        return o

    def mirrored_x(self):
        g = Geo()
        g.v = [Vector((-p.x, p.y, p.z)) for p in self.v]
        g.f = [tuple(reversed(fc)) for fc in self.f]
        g.col, g.em = list(self.col), list(self.em)
        return g

    def obj(self, name, col, glow=False, smooth=True, sharp=35, loc=(0, 0, 0)):
        me = bpy.data.meshes.new(name)
        me.from_pydata([tuple(v) for v in self.v], [], self.f)
        me.validate()
        bm = bmesh.new()
        bm.from_mesh(me)
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        bm.to_mesh(me)
        bm.free()
        for p in me.polygons:
            p.use_smooth = smooth
        if smooth and sharp:
            try:
                me.set_sharp_from_angle(angle=math.radians(sharp))
            except Exception:
                pass
        ca = me.color_attributes.new("spcol", 'FLOAT_COLOR', 'POINT')
        flat = []
        for c in self.col[:len(me.vertices)]:
            flat += [*lin(c), 1.0]
        ca.data.foreach_set("color", flat)
        ea = me.attributes.new("spemit", 'FLOAT', 'POINT')
        ea.data.foreach_set("value", [float(e) for e in self.em[:len(me.vertices)]])
        me.materials.append(preview_material(glow))
        ob = bpy.data.objects.new(name, me)
        ob["sp_glow"] = 1 if glow else 0
        ob["sp_col"] = [x for c in self.col[:len(me.vertices)] for x in c]
        col.objects.link(ob)
        ob.location = loc
        return ob


# ------------------------------------------------------------------ export

def export_spm(objs, filename, origin=None):
    """Writes triangles of objs (relative to origin, default the first object's location)."""
    deps = bpy.context.evaluated_depsgraph_get()
    parts = {0: bytearray(), 1: bytearray()}
    counts = {0: 0, 1: 0}
    origin = Vector(origin) if origin is not None else objs[0].location.copy()
    for ob in objs:
        glow = int(ob.get("sp_glow", 0))
        srgb = list(ob.get("sp_col", []))
        ev = ob.evaluated_get(deps)
        me = ev.to_mesh()
        me.calc_loop_triangles()
        mw = Matrix.Translation(-origin) @ ob.matrix_world
        nm = mw.to_3x3().inverted().transposed()
        cn = me.corner_normals
        ea = me.attributes.get("spemit")
        for tri in me.loop_triangles:
            for li, vi in zip(tri.loops, tri.vertices):
                co = mw @ me.vertices[vi].co
                n = (nm @ cn[li].vector).normalized()
                r, g, b = srgb[vi * 3: vi * 3 + 3] if len(srgb) >= vi * 3 + 3 else (1, 1, 1)
                e = ea.data[vi].value if ea else 0.0
                parts[glow] += struct.pack('<6f5B3x', co.x, co.z, -co.y, n.x, n.z, -n.y,
                                           int(max(0, min(1, r)) * 255), int(max(0, min(1, g)) * 255),
                                           int(max(0, min(1, b)) * 255), 255, int(max(0, min(1, e)) * 255))
                counts[glow] += 1
        ev.to_mesh_clear()
    path = os.path.join(OUT, filename + ".spm")
    with open(path, "wb") as f:
        f.write(b"SPM1")
        f.write(struct.pack("<i", sum(1 for k in counts if counts[k])))
        for k in (0, 1):
            if counts[k]:
                f.write(struct.pack("<ii", k, counts[k]))
                f.write(parts[k])
    print(f"exported {filename}: {counts[0] // 3} solid tris, {counts[1] // 3} glow tris")
    return path


# ------------------------------------------------------------------ shape helpers

def ribbon(curve_l, curve_r):
    """Quad strip between two equal-length point lists."""
    v = [Vector(p) for p in curve_l] + [Vector(p) for p in curve_r]
    n = len(curve_l)
    f = [(i, i + 1, n + i + 1, n + i) for i in range(n - 1)]
    return v, f


def grid(fn, nu, nv):
    """Surface from fn(u, v) -> Vector, u,v in [0,1]."""
    v = [fn(i / nu, j / nv) for j in range(nv + 1) for i in range(nu + 1)]
    f = []
    for j in range(nv):
        for i in range(nu):
            a = j * (nu + 1) + i
            f.append((a, a + 1, a + nu + 2, a + nu + 1))
    return v, f


def catmull(pts, n):
    """Smooth curve through pts with n samples."""
    pts = [Vector(p) for p in pts]
    out = []
    segs = len(pts) - 1
    for k in range(n):
        t = k / (n - 1) * segs
        i = min(int(t), segs - 1)
        u = t - i
        p0 = pts[max(i - 1, 0)]
        p1, p2 = pts[i], pts[i + 1]
        p3 = pts[min(i + 2, segs)]
        out.append(0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u))
    return out


def ico(r, subdiv=2):
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=subdiv, radius=r)
    v = [vt.co.copy() for vt in bm.verts]
    f = [tuple(vt.index for vt in fc.verts) for fc in bm.faces]
    bm.free()
    return v, f


def frame_from(fwd, up_hint=Vector((0, 0, 1))):
    fwd = Vector(fwd).normalized()
    side = fwd.cross(up_hint)
    if side.length < 1e-6:
        side = fwd.cross(Vector((1, 0, 0)))
    side.normalize()
    up = side.cross(fwd).normalized()
    return fwd, side, up


def rot(axis, ang):
    return Matrix.Rotation(ang, 4, Vector(axis))


print("sp_lib ready ->", OUT)
