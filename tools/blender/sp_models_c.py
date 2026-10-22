# Batch C: Entity 666 - a starved, corpse-pale, long-limbed thing, built as separate rig parts.
# Organic parts are skin-modifier skeletons, subdivided, then displaced (ribs, spine, sockets) and
# coloured from curvature so dirt collects in the creases. Each part is modelled around its joint:
#   entity_torso  pivot = pelvis      (neck top at z 1.2, shoulders at x +-0.32 z 1.06)
#   entity_head   pivot = neck top    (jaw hinge at JAW_HINGE)
#   entity_jaw    pivot = jaw hinge   (lower half of the face, closed; the mod rotates it open)
#   entity_arm_u  pivot = shoulder    (elbow at z -0.95)
#   entity_arm_l  pivot = elbow       (hand + fingers to about z -1.75)
#   entity_leg_u  pivot = hip         (knee at z -0.95)
#   entity_leg_l  pivot = knee        (sole at z -1.0)
# Run after sp_lib.py.

PALE = (0.66, 0.63, 0.6)
GREY = (0.44, 0.42, 0.42)
BRUISE = (0.3, 0.24, 0.3)
DIRT = (0.1, 0.085, 0.08)
BLOOD = (0.3, 0.015, 0.02)
BLOOD_WET = (0.48, 0.02, 0.03)
TOOTH = (0.8, 0.76, 0.6)
NAIL = (0.12, 0.09, 0.07)
JAW_HINGE = (0.0, -0.02, 0.205)


def organic(verts, edges, radii, subdiv=2, disp=None):
    """Skin-modifier skeleton -> subdivided, displaced mesh. Returns verts, faces, normals, curvature."""
    me = bpy.data.meshes.new("sp_tmp_skin")
    me.from_pydata([tuple(v) for v in verts], edges, [])
    ob = bpy.data.objects.new("sp_tmp_skin", me)
    bpy.context.scene.collection.objects.link(ob)
    ob.modifiers.new("skin", 'SKIN')
    layer = me.skin_vertices[0].data
    for i, r in enumerate(radii):
        layer[i].radius = tuple(r) if isinstance(r, (tuple, list)) else (r, r)
        layer[i].use_root = i == 0
    if subdiv:
        s = ob.modifiers.new("sub", 'SUBSURF')
        s.levels = subdiv
        s.render_levels = subdiv
    dg = bpy.context.evaluated_depsgraph_get()
    m2 = bpy.data.meshes.new_from_object(ob.evaluated_get(dg))
    bm = bmesh.new()
    bm.from_mesh(m2)
    bm.normal_update()
    if disp:
        moves = [v.normal * disp(v.co.copy(), v.normal.copy()) for v in bm.verts]
        for v, d in zip(bm.verts, moves):
            v.co += d
        bm.normal_update()
    V, N, C, F = surface(bm)
    bm.free()
    bpy.data.objects.remove(ob, do_unlink=True)
    bpy.data.meshes.remove(me)
    bpy.data.meshes.remove(m2)
    return V, F, N, C


def surface(bm):
    """Positions, normals, a curvature estimate (+ = crease) and faces of a bmesh."""
    V, N, C = [], [], []
    for v in bm.verts:
        c, k = 0.0, 0
        for e in v.link_edges:
            d = e.other_vert(v).co - v.co
            if d.length > 1e-7:
                c += v.normal.dot(d) / d.length
                k += 1
        V.append(v.co.copy())
        N.append(v.normal.copy())
        C.append(c / max(k, 1))
    F = [tuple(x.index for x in f.verts) for f in bm.faces]
    return V, N, C, F


def gauss(p, c, r):
    return math.exp(-((p - Vector(c)).length_squared) / (r * r))


def skin_col(p, curv, grime=0.0):
    """Corpse skin: mottled pale/grey, bruised veins, dirt in creases."""
    p = Vector(p)
    base = mixc(GREY, PALE, 0.55 + 0.9 * noise.noise(p * 3.5))
    dist, _ = noise.voronoi(p * 9.0, distance_metric='DISTANCE')
    vein = max(0.0, 1 - (dist[1] - dist[0]) / 0.06) * (0.5 + 0.5 * noise.noise(p * 2.0 + Vector((5, 1, 3))))
    base = mixc(base, BRUISE, 0.55 * vein)
    cav = max(0.0, min(1.0, curv * 5.0 + 0.15)) + grime
    return mixc(base, DIRT, max(0.0, min(0.85, cav)))


# ------------------------------------------------------------------ torso

def entity_torso():
    g = Geo()
    sp = [(0, 0.0, 0.0), (0, 0.02, 0.14), (0, 0.035, 0.3), (0, 0.025, 0.46), (0, 0.005, 0.62),
          (0, 0.01, 0.78), (0, 0.03, 0.92), (0, 0.06, 1.03), (0, 0.04, 1.13), (0, 0.0, 1.2)]
    rad = [(0.15, 0.1), (0.1, 0.075), (0.085, 0.065), (0.15, 0.11), (0.2, 0.13), (0.215, 0.14),
           (0.2, 0.125), (0.15, 0.09), (0.055, 0.055), (0.05, 0.05)]
    verts, radii = list(sp), list(rad)
    edges = [(i, i + 1) for i in range(len(sp) - 1)]
    for s in (-1, 1):
        a = len(verts)
        verts += [(s * 0.17, 0.06, 1.07), (s * 0.32, 0.05, 1.06)]
        radii += [(0.065, 0.055), (0.07, 0.07)]
        edges += [(7, a), (a, a + 1)]
        b = len(verts)
        verts += [(s * 0.13, 0.0, -0.02)]
        radii += [(0.075, 0.075)]
        edges += [(0, b)]

    def disp(p, n):
        d = 0.0
        front = max(0.0, -n.y)
        side = abs(n.x)
        # ribs pushing through
        if 0.45 < p.z < 0.98:
            k = (p.z - 0.45) * 14.0
            rib = max(0.0, math.sin(k * math.pi)) ** 3
            d += 0.016 * rib * (front + side * 0.8) * min(1.0, (0.98 - p.z) * 8)
            d -= 0.008 * (1 - rib) * (front + side)
        # sunken belly under the ribs
        if 0.12 < p.z < 0.44:
            d -= 0.022 * front * math.sin((p.z - 0.12) / 0.32 * math.pi)
        # sternum ridge
        d += 0.012 * gauss(Vector((p.x * 3, p.y, p.z)), (0, p.y, 0.8), 0.16) * front
        # spine knobs and shoulder blades
        back = max(0.0, n.y)
        if abs(p.x) < 0.035 and 0.1 < p.z < 1.1:
            d += 0.02 * back * max(0.0, math.sin(p.z * 2 * math.pi * 8)) ** 2
        for s in (-1, 1):
            d += 0.035 * back * gauss(p, (s * 0.12, 0.14, 0.86), 0.09)
            d += 0.018 * gauss(p, (s * 0.15, -0.06, 1.06), 0.05)  # collarbones
        d += 0.004 * noise.noise(p * 25)
        return d

    V, F, N, C = organic(verts, edges, radii, subdiv=2, disp=disp)

    def col(i, p):
        c = skin_col(p, C[i])
        # dried blood running down the chest from the mouth
        if N[i].y < -0.25:
            for x0, z0, w in ((-0.075, 0.82, 0.016), (-0.03, 0.62, 0.022), (0.012, 0.74, 0.013),
                              (0.05, 0.55, 0.02), (0.095, 0.88, 0.012)):
                wob = x0 + 0.012 * noise.noise(Vector((x0 * 9, 0, p.z * 9)))
                if abs(p.x - wob) < w * (0.4 + 0.6 * min(1.0, (p.z - z0) / 0.15)) and p.z > z0:
                    c = mixc(c, BLOOD, 0.9 if p.z - z0 > 0.02 else 0.6)
            if p.z > 1.0:
                c = mixc(c, BLOOD, min(0.85, (p.z - 1.0) * 6 * (0.6 + 0.4 * noise.noise(p * 9))))
        return c

    g.add(V, F, cfun=col)
    return g


# ------------------------------------------------------------------ head
# One skull, split along a grin that runs back almost to the ears: the lower face is the jaw and
# hinges open like a trap. Plane of the cut: z = MOUTH_Z + MOUTH_SLOPE * (y + 0.22).

MOUTH_Z, MOUTH_SLOPE = 0.085, 0.6
HEAD_C = Vector((0, -0.06, 0.22))
EYES = [Vector((s * 0.064, -0.208, 0.26)) for s in (-1, 1)]


def skull_bm():
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=72, v_segments=52, radius=1.0)
    for v in bm.verts:
        u = v.co.copy()
        p = Vector((u.x * 0.145, u.y * 0.19, u.z * 0.28))
        if u.y > 0:
            p.y *= 1.18
        if u.z > 0:
            p.z *= 1.08
            p.y += 0.05 * u.z ** 2
        if u.z < -0.2:
            k = (-u.z - 0.2) / 0.8
            p.x *= 1 - 0.25 * k
        if u.z < 0 and u.y < 0:  # long jutting chin
            p.y -= 0.03 * (-u.z) * (-u.y)
        p += HEAD_C
        r = (p - HEAD_C).normalized()
        d = 0.0
        for e in EYES:
            d -= 0.055 * gauss(p, e, 0.046)
            d += 0.016 * gauss(p, e + Vector((0, 0.01, 0.065)), 0.05)
        for s in (-1, 1):
            d += 0.02 * gauss(p, (s * 0.105, -0.15, 0.17), 0.04)      # cheekbones
            d -= 0.03 * gauss(p, (s * 0.11, -0.13, 0.1), 0.05)        # hollow cheeks
            d -= 0.025 * gauss(p, (s * 0.14, -0.05, 0.3), 0.06)       # sunken temples
            d -= 0.012 * gauss(p, (s * 0.014, -0.205, 0.175), 0.012)  # nose slits
        d -= 0.03 * gauss(p, (0, -0.2, 0.19), 0.028)
        d += 0.004 * noise.noise(p * 30)
        v.co = p + r * d
    plane_no = Vector((0, -MOUTH_SLOPE, 1)).normalized()
    bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], dist=1e-5,
                           plane_co=Vector((0, -0.22, MOUTH_Z)), plane_no=plane_no)
    bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], dist=1e-5,
                           plane_co=Vector(JAW_HINGE), plane_no=Vector((0, 1, 0)))
    bm.normal_update()
    return bm


def below_mouth(p):
    return p.z < MOUTH_Z + MOUTH_SLOPE * (p.y + 0.22) - 1e-4 and p.y < JAW_HINGE[1] - 1e-4


def head_col(p, n, curv):
    c = skin_col(p, curv)
    for e in EYES:
        c = mixc(c, (0.02, 0.015, 0.015), min(1.0, gauss(p, e, 0.05) * 1.7))
        if abs(p.x - e.x) < 0.011 + 0.007 * noise.noise(p * 40) and e.z - 0.17 < p.z < e.z and n.y < -0.2:
            c = mixc(c, (0.03, 0.02, 0.02), 0.85)
    lip = MOUTH_Z + MOUTH_SLOPE * (p.y + 0.22)
    if p.z < lip and n.y < 0.1 and abs(math.sin(p.x * 70 + 2 * noise.noise(p * 6))) > 0.55:
        c = mixc(c, BLOOD, min(0.85, (lip - p.z) * 5 + 0.2))
    if n.y < 0.2 and abs(p.z - lip) < 0.06:
        c = mixc(c, BLOOD, max(0.0, 0.8 - abs(p.z - lip) * 14 + 0.3 * noise.noise(p * 14)))
    return c


def split_skull():
    """Returns (cranium Geo, jaw Geo in hinge space, upper edge points, lower edge points)."""
    bm = skull_bm()
    V, N, C, F = surface(bm)
    bm.free()
    up, low = Geo(), Geo()
    maps = ({}, {})
    for fc in F:
        cen = sum((V[i] for i in fc), Vector()) / len(fc)
        side = 1 if below_mouth(cen) else 0
        g = low if side else up
        m = maps[side]
        ids = []
        for i in fc:
            if i not in m:
                m[i] = len(g.v)
                g.v.append(V[i].copy())
                g.col.append(head_col(V[i], N[i], C[i]))
                g.em.append(0.0)
            ids.append(m[i])
        g.f.append(tuple(ids))
    # vertices on the cut, front part only (where the teeth go)
    def on_cut(i):
        p = V[i]
        lip = MOUTH_Z + MOUTH_SLOPE * (p.y + 0.22)
        return abs(p.z - lip) < 2e-4 and p.y < JAW_HINGE[1] - 0.03
    edge = sorted({i for i in range(len(V)) if on_cut(i) and i in maps[0] and i in maps[1]}, key=lambda i: math.atan2(V[i].x, -V[i].y))
    return up, low, [V[i] for i in edge]


def teeth(g, edge, down, seed):
    random.seed(seed)
    n_plane = Vector((0, -MOUTH_SLOPE, 1)).normalized()
    for k, p in enumerate(edge):
        if k % 2:
            continue
        inward = Vector((-p.x, -(p.y - HEAD_C.y), 0))
        if inward.length < 1e-6:
            continue
        inward.normalize()
        L = random.uniform(0.022, 0.055) * (1.4 if k % 7 == 3 else 1.0)
        d = (-n_plane if down else n_plane) + inward * random.uniform(0.1, 0.35) + Vector((random.uniform(-0.12, 0.12), 0, 0))
        base = p + inward * 0.012 + (n_plane * 0.004 if down else -n_plane * 0.004)
        v, f = cone(base, d, L, random.uniform(0.0045, 0.0075), sides=5)
        g.add(v, f, cfun=lambda i, q: mixc(TOOTH, (0.42, 0.3, 0.18), 0.65 if i < 5 else 0.0))


def flesh_cap(g, below):
    """Wet dark flesh filling the open mouth (palate above the cut, mouth floor below)."""
    n_plane = Vector((0, -MOUTH_SLOPE, 1)).normalized()
    t_axis_ = Vector((0, 1, MOUTH_SLOPE)).normalized()
    centre = Vector((0, -0.09, MOUTH_Z + MOUTH_SLOPE * (0.13))) + n_plane * (-0.012 if below else 0.012)
    v, f = sphere((0, 0, 0), 1.0, n=10)
    m = Matrix((
        (0.118, 0, 0, 0),
        (0, t_axis_.y * 0.15, n_plane.y * 0.035, 0),
        (0, t_axis_.z * 0.15, n_plane.z * 0.035, 0),
        (0, 0, 0, 1)))
    xf = Matrix.Translation(centre) @ m
    g.add(v, f, cfun=lambda i, p: mixc((0.32, 0.02, 0.05), (0.03, 0.0, 0.0), max(0.0, min(1.0, (p.y + 0.12) / 0.12))),
          emit=0.08, xf=xf)


def entity_head():
    up, low, edge = split_skull()
    flesh_cap(up, False)
    teeth(up, edge, True, 66)
    for e in EYES:
        v, f = sphere(e + Vector((0, 0.035, 0)), 0.032, n=6)
        up.add(v, f, color=(0.0, 0.0, 0.0))
        v, f = sphere(e + Vector((0, 0.012, -0.004)), 0.0045, n=4)
        up.add(v, f, color=(1.0, 0.85, 0.8), emit=0.9)
    random.seed(13)
    for k in range(26):
        a = math.pi * random.uniform(0.0, 1.0)
        root = HEAD_C + Vector((-math.cos(a) * 0.13, 0.06 + math.sin(a) * 0.15, 0.18 + random.uniform(-0.04, 0.07)))
        L = random.uniform(0.35, 0.7)
        pts = [root + Vector((random.uniform(-0.012, 0.012) * j, 0.025 + 0.012 * j, -L * (j / 7) ** 1.3)) for j in range(8)]
        v, f, _ = tube(pts, lambda t: 0.007 * (1 - t) + 0.0015, sides=4)
        up.add(v, f, color=(0.035, 0.03, 0.03))
    flesh_cap(low, True)
    teeth(low, edge, False, 99)
    h = Vector(JAW_HINGE)
    low.v = [p - h for p in low.v]
    return up, low


# ------------------------------------------------------------------ limbs

def chain(pts, rads, subdiv=2, bump=0.003, grime=None, extra=None):
    edges = [(i, i + 1) for i in range(len(pts) - 1)]
    if extra:
        for (a, p, r) in extra:
            edges.append((a, len(pts)))
            pts = pts + [p]
            rads = rads + [r]
    V, F, N, C = organic(pts, edges, rads, subdiv=subdiv, disp=lambda p, n: bump * noise.noise(p * 28))
    g = Geo()
    g.add(V, F, cfun=lambda i, p: skin_col(p, C[i], grime(p) if grime else 0.0))
    return g


def entity_arm_u():
    pts = [(0, 0, 0), (0, 0, -0.22), (0, 0.005, -0.5), (0, 0.0, -0.78), (0, 0.0, -0.92), (0, 0.0, -0.97)]
    rads = [0.062, 0.048, 0.036, 0.032, 0.058, 0.046]
    return chain(pts, rads)


def entity_arm_l():
    pts = [(0, 0, 0), (0, 0, -0.3), (0, 0.0, -0.62), (0, 0.0, -0.9), (0, -0.005, -0.97),
           (0, -0.015, -1.06), (0, -0.02, -1.14)]
    rads = [0.044, 0.036, 0.03, 0.026, 0.03, (0.062, 0.024), (0.066, 0.022)]

    def grime(p):
        return max(0.0, min(0.5, (-p.z - 0.9) * 1.2))

    g = chain(pts, rads, grime=grime)
    random.seed(7)
    # four very long knuckled fingers curling forward, and a thumb
    for k, x in enumerate((-0.05, -0.017, 0.017, 0.05)):
        L = 0.5 + 0.12 * (1 - abs(x) / 0.05)
        base = Vector((x, -0.02, -1.14))
        fp, fr = [], []
        for j in range(10):
            t = j / 9
            curl = 0.55 * t ** 1.7
            fp.append(base + Vector((x * 0.25 * t, -L * math.sin(curl) * 0.6, -L * t * math.cos(curl * 0.5))))
            knuckle = 0.0045 if j in (3, 6) else 0.0
            fr.append(0.017 * (1 - 0.55 * t) + knuckle)
        fg = chain(fp, fr, subdiv=1, grime=lambda p: min(0.6, max(0.0, (-p.z - 1.3) * 1.5)))
        o = len(g.v)
        g.v += fg.v
        g.col += fg.col
        g.em += fg.em
        g.f += [tuple(i + o for i in fc) for fc in fg.f]
        tipd = (fp[-1] - fp[-2]).normalized()
        v, f = cone(fp[-1] - tipd * 0.01, tipd, 0.07, 0.009, sides=5)
        g.add(v, f, cfun=lambda i, p: mixc(NAIL, BLOOD, 0.5 if i == 5 else 0.0))
        # blood soaked fingertips
        for j in range(len(fg.v)):
            q = fg.v[j]
            if -q.z > 1.14 + L * 0.7:
                g.col[o + j] = mixc(g.col[o + j], BLOOD, 0.7)
    tp = [Vector((-0.058 - 0.008 * j, -0.03 - 0.012 * j, -1.05 - 0.045 * j)) for j in range(6)]
    tg = chain(tp, [0.016 - 0.0017 * j for j in range(6)], subdiv=1)
    o = len(g.v)
    g.v += tg.v
    g.col += tg.col
    g.em += tg.em
    g.f += [tuple(i + o for i in fc) for fc in tg.f]
    return g


def entity_leg_u():
    pts = [(0, 0, 0), (0, -0.01, -0.3), (0, 0.0, -0.62), (0, 0.0, -0.88), (0, -0.01, -0.96), (0, 0, -1.0)]
    rads = [0.078, 0.058, 0.046, 0.04, 0.072, 0.056]
    return chain(pts, rads)


def entity_leg_l():
    pts = [(0, 0, 0), (0, 0.015, -0.28), (0, 0.01, -0.6), (0, 0.0, -0.88), (0, 0.02, -0.97),
           (0, -0.06, -0.995), (0, -0.16, -1.0)]
    rads = [0.05, 0.04, 0.032, 0.027, 0.034, (0.045, 0.022), (0.05, 0.018)]
    g = chain(pts, rads, grime=lambda p: max(0.0, min(0.6, (-p.z - 0.6) * 1.2)))
    for x in (-0.03, 0.0, 0.03):
        base = Vector((x, -0.16, -1.0))
        tp = [base + Vector((x * 0.5 * t, -0.16 * t, 0.01 * math.sin(t * math.pi))) for t in (j / 5 for j in range(6))]
        tg = chain(tp, [0.014 - 0.0015 * j for j in range(6)], subdiv=1, grime=lambda p: 0.5)
        o = len(g.v)
        g.v += tg.v
        g.col += tg.col
        g.em += tg.em
        g.f += [tuple(i + o for i in fc) for fc in tg.f]
        v, f = cone(tp[-1], (0, -1, -0.3), 0.045, 0.008, sides=5)
        g.add(v, f, color=NAIL)
    return g


# ------------------------------------------------------------------ build, export, preview

col = sp_collection("SP_BatchC_Entity666")
old = bpy.data.collections.get("SP_BatchC_Pose")
if old:
    for o in list(old.objects):
        bpy.data.objects.remove(o, do_unlink=True)

_head, _jaw = entity_head()
builders = {
    "entity_torso": entity_torso, "entity_head": lambda: _head, "entity_jaw": lambda: _jaw,
    "entity_arm_u": entity_arm_u, "entity_arm_l": entity_arm_l,
    "entity_leg_u": entity_leg_u, "entity_leg_l": entity_leg_l,
}
geos = {}
objs = {}
for name, fn in builders.items():
    geos[name] = fn()
    ob = geos[name].obj(name, col, loc=(0, 0, 0), sharp=0)
    objs[name] = ob
    export_spm([ob], name)
    print(name, len(geos[name].f), "faces")


def place(ob, parent, loc, rot=(0, 0, 0)):
    ob.parent = parent
    ob.matrix_parent_inverse.identity()
    ob.location = loc
    ob.rotation_euler = [math.radians(a) for a in rot]


# hunched pose, the way it stands in game: torso pitched forward, head hanging, jaw agape,
# arms dangling to the ground, knees bent
HIP = 1.86
place(objs["entity_torso"], None, (0, 0, HIP), (28, 0, 0))
place(objs["entity_head"], objs["entity_torso"], (0, 0.0, 1.2), (8, 0, 9))
place(objs["entity_jaw"], objs["entity_head"], JAW_HINGE, (55, 0, 0))
for s, side in ((1, ""), (-1, "_R")):
    if side:
        au = geos["entity_arm_u"].mirrored_x().obj("entity_arm_u_R", col, sharp=0)
        al = geos["entity_arm_l"].mirrored_x().obj("entity_arm_l_R", col, sharp=0)
        lu = geos["entity_leg_u"].mirrored_x().obj("entity_leg_u_R", col, sharp=0)
        ll = geos["entity_leg_l"].mirrored_x().obj("entity_leg_l_R", col, sharp=0)
    else:
        au, al, lu, ll = objs["entity_arm_u"], objs["entity_arm_l"], objs["entity_leg_u"], objs["entity_leg_l"]
    place(au, objs["entity_torso"], (s * 0.32, 0.05, 1.06), (-26, 0, s * 6))
    place(al, au, (0, 0, -0.95), (-12 + (8 if s < 0 else 0), 0, 0))
    place(lu, None, (s * 0.13, 0, HIP), (20, 0, s * 4))
    place(ll, lu, (0, 0, -0.95), (-26, 0, 0))
print("entity 666 v2 done")
