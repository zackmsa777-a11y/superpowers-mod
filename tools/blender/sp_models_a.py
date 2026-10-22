# Batch A: angel wing, demon wing, demon horn, halo.  Run after sp_lib.py.

def smoothstep(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


# ------------------------------------------------------------------ angel wing (right side)

def feather(g, base, d, n, length, width, c0, c1, e0, e1, lead=0.55, segs=10, bend=0.06):
    d = d.normalized()
    n = n.normalized()
    side = d.cross(n).normalized()
    left, mid, right = [], [], []
    for k in range(segs + 1):
        s = k / segs
        c = base + d * (length * s) + n * (bend * length * s * s)
        # elongated vane with a rounded tip
        w = width * (math.sin(math.pi * (0.08 + 0.92 * s) ** 0.8) ** 0.55) * (1 - 0.15 * s)
        if k == segs:
            w = 0.0
        left.append(c - side * (w * lead))
        mid.append(c + n * 0.012)
        right.append(c + side * w)
    v = left + mid + right
    m = segs + 1
    f = []
    for k in range(segs):
        f.append((k, k + 1, m + k + 1, m + k))
        f.append((m + k, m + k + 1, 2 * m + k + 1, 2 * m + k))

    def cf(i, p):
        s = (i % m) / segs
        col = mixc(c0, c1, s ** 1.3)
        if m <= i < 2 * m:
            col = mixc(col, (1.0, 0.88, 0.55), 0.5)
        return col

    g.add(v, f, cfun=cf, efun=lambda i, p: e0 + (e1 - e0) * ((i % m) / segs))


def angel_wing():
    g = Geo()
    arm_pts = [(0, 0, 0), (0.42, 0.1, 0.45), (0.95, 0.2, 0.78), (1.55, 0.3, 0.86), (2.15, 0.45, 0.68), (2.6, 0.6, 0.38)]
    arm = catmull(arm_pts, 56)
    v, f, _ = tube(arm, lambda t: 0.06 * (1 - t) + 0.016, sides=8)
    g.add(v, f, color=(1.0, 0.9, 0.62), emit=0.3)

    def at(s):
        i = min(len(arm) - 2, int(s * (len(arm) - 1)))
        return arm[i], (arm[i + 1] - arm[i]).normalized()

    def normal(d, t):
        n = d.cross(t)
        return -n if n.y < 0 else n

    down = Vector((0.1, 0.28, -1)).normalized()
    out_tip = Vector((1, 0.22, -0.35)).normalized()
    gold, cream, white, sky = (1.0, 0.85, 0.52), (1.0, 0.96, 0.86), (1.0, 1.0, 1.0), (0.68, 0.84, 1.0)

    # primaries: long flight feathers fanning out from the wrist to the tip
    for k in range(12):
        s = 0.55 + 0.45 * k / 11
        p, t = at(s)
        d = down.lerp(out_tip, 0.1 + 0.85 * k / 11).normalized()
        length = 1.55 + 0.55 * math.sin(math.pi * (k + 2) / 14)
        feather(g, p, d, normal(d, t), length, length * 0.16, gold, sky, 0.12, 0.5, lead=0.5)
    # secondaries hanging down along the forearm
    for k in range(14):
        s = 0.03 + 0.54 * k / 13
        p, t = at(s)
        d = (down + t * (0.04 + 0.1 * k / 13) + Vector((0, 0.1, 0))).normalized()
        length = 1.05 + 0.4 * k / 13
        feather(g, p + normal(d, t) * 0.012, d, normal(d, t), length, length * 0.18, gold, sky, 0.1, 0.45)
    # tertials close to the body
    for k in range(4):
        p, t = at(0.01 + 0.03 * k)
        d = (down + Vector((-0.08, 0.15, 0))).normalized()
        feather(g, p + Vector((0, 0.02, 0)), d, normal(d, t), 0.8 + 0.1 * k, 0.16, gold, cream, 0.1, 0.4)
    # greater coverts on top
    for k in range(16):
        s = 0.03 + 0.9 * k / 15
        p, t = at(s)
        d = (down + t * 0.18 * k / 15).normalized()
        feather(g, p + normal(d, t) * 0.04, d, normal(d, t), 0.62 + 0.14 * math.sin(math.pi * k / 15), 0.13,
                cream, white, 0.2, 0.4, segs=8)
    # marginal coverts along the leading edge
    for k in range(20):
        s = 0.02 + 0.95 * k / 19
        p, t = at(s)
        d = (down * 0.8 + t * 0.6).normalized()
        feather(g, p + normal(d, t) * 0.065, d, normal(d, t), 0.32, 0.1, white, white, 0.25, 0.45, segs=6, bend=0.1)
    return g


# ------------------------------------------------------------------ demon wing (right side)

def demon_wing():
    g = Geo()
    bone, bone_dark = (0.2, 0.12, 0.12), (0.1, 0.06, 0.07)
    root, elbow, wrist = Vector((0, 0, 0)), Vector((0.55, 0.18, 0.5)), Vector((1.25, 0.32, 0.72))
    arm = catmull([root, elbow, wrist], 26)
    v, f, _ = tube(arm, lambda t: 0.075 - 0.025 * t, sides=10)
    g.add(v, f, color=bone)
    for c, r in ((elbow, 0.085), (wrist, 0.075), (root, 0.08)):
        v, f = sphere(c, r, n=6)
        g.add(v, f, color=bone_dark)
    # thumb claw
    claw = bezier(wrist, wrist + Vector((0.05, -0.1, 0.16)), wrist + Vector((-0.02, -0.22, 0.2)), 10)
    v, f, _ = tube(claw, lambda t: 0.035 * (1 - t) + 0.002, sides=8)
    g.add(v, f, cfun=lambda i, p: mixc((0.85, 0.78, 0.66), (1, 0.25, 0.15), (i // 8) / 9),
          efun=lambda i, p: smoothstep(0.6, 1.0, (i // 8) / 9))

    tips = [Vector((2.75, 0.5, 0.75)), Vector((2.6, 0.72, -0.05)), Vector((2.05, 0.84, -0.78)), Vector((1.2, 0.76, -1.18))]
    fingers = []
    for tip in tips:
        mid = wrist.lerp(tip, 0.5) + Vector((0, 0.06, 0.14))
        crv = bezier(wrist, mid, tip, 18)
        fingers.append(crv)
        v, f, _ = tube(crv, lambda t: 0.042 * (1 - t) + 0.012, sides=8)
        g.add(v, f, color=bone)
        hook_dir = (crv[-1] - crv[-3]).normalized()
        v, f = cone(tip, hook_dir + Vector((0, 0, -0.4)), 0.12, 0.018, sides=6)
        g.add(v, f, color=(0.9, 0.2, 0.15), emit=0.8)
    body = bezier(root, Vector((0.2, 0.25, -0.5)), Vector((0.18, 0.38, -1.0)), 18)
    ribs = fingers + [body]
    inner_mid = root.lerp(wrist, 0.5)

    for a, b in zip(ribs[:-1], ribs[1:]):
        na = len(a) - 1

        def P(u, vv, a=a, b=b, na=na):
            idx = vv * na
            i0 = min(int(idx), na - 1)
            fr = idx - i0
            pa = a[i0].lerp(a[i0 + 1], fr)
            pb = b[i0].lerp(b[i0 + 1], fr)
            p = pa.lerp(pb, u)
            sag = math.sin(math.pi * u)
            p = p + (inner_mid - p) * (0.32 * sag * vv * vv)
            p = p + Vector((0, 0.11 * sag * math.sin(math.pi * vv), 0))
            return p

        v, f = grid(P, 10, 12)

        def cf(i, p):
            u = (i % 11) / 10
            vv = (i // 11) / 12
            vein = max(math.exp(-((u - 0.33) / 0.04) ** 2), math.exp(-((u - 0.66) / 0.04) ** 2)) * (0.3 + 0.7 * vv)
            base = mixc((0.28, 0.02, 0.05), (0.55, 0.05, 0.09), math.sin(math.pi * u) * 0.8)
            return mixc(base, (1.0, 0.18, 0.12), vein * 0.8)

        def ef(i, p):
            u = (i % 11) / 10
            vv = (i // 11) / 12
            vein = max(math.exp(-((u - 0.33) / 0.04) ** 2), math.exp(-((u - 0.66) / 0.04) ** 2)) * (0.3 + 0.7 * vv)
            return 0.15 + 0.6 * vein

        g.add(v, f, cfun=cf, efun=ef)
    return g


# ------------------------------------------------------------------ demon horn (right side)

def demon_horn():
    g = Geo()
    path = catmull([(0, 0, 0), (0.1, 0.02, 0.16), (0.2, 0.12, 0.3), (0.25, 0.3, 0.38), (0.21, 0.47, 0.47), (0.13, 0.53, 0.62)], 40)
    sides = 12

    def r(t):
        ridge = 1 + 0.09 * (1 - t) * math.sin(t * 52)
        return max(0.003, 0.08 * (1 - t) ** 0.9 * ridge)

    v, f, _ = tube(path, r, sides=sides)

    def cf(i, p):
        t = (i // sides) / (len(path) - 1)
        c = mixc((0.1, 0.05, 0.05), (0.3, 0.06, 0.06), t / 0.7) if t < 0.7 else mixc((0.3, 0.06, 0.06), (1.0, 0.32, 0.12), (t - 0.7) / 0.3)
        k = i % sides
        if k in (0, 1):
            c = mixc(c, (0.45, 0.1, 0.08), 0.5)
        return c

    g.add(v, f, cfun=cf, efun=lambda i, p: smoothstep(0.76, 1.0, (i // sides) / (len(path) - 1)))
    return g


# ------------------------------------------------------------------ halo

def torus_verts(R, w, h, seg=96, sides=10, z=0.0):
    v, f = [], []
    for i in range(seg):
        a = 2 * math.pi * i / seg
        radial = Vector((math.cos(a), math.sin(a), 0))
        for j in range(sides):
            b = 2 * math.pi * j / sides
            v.append(radial * (R + w * math.cos(b)) + Vector((0, 0, z + h * math.sin(b))))
    for i in range(seg):
        for j in range(sides):
            a = i * sides + j
            bb = i * sides + (j + 1) % sides
            c = ((i + 1) % seg) * sides + (j + 1) % sides
            d = ((i + 1) % seg) * sides + j
            f.append((a, bb, c, d))
    return v, f


def octa(c, s, sz=None):
    sz = sz or s
    c = Vector(c)
    v = [c + Vector(o) for o in ((s, 0, 0), (-s, 0, 0), (0, s, 0), (0, -s, 0), (0, 0, sz), (0, 0, -sz))]
    f = [(0, 2, 4), (2, 1, 4), (1, 3, 4), (3, 0, 4), (2, 0, 5), (1, 2, 5), (3, 1, 5), (0, 3, 5)]
    return v, f


def halo():
    g = Geo()
    gold, pale = (1.0, 0.76, 0.28), (1.0, 0.9, 0.6)
    v, f = torus_verts(0.3, 0.03, 0.013, seg=72, sides=8)
    g.add(v, f, color=gold, emit=0.85)
    for R in (0.355, 0.255):
        v, f = torus_verts(R, 0.006, 0.006, seg=64, sides=5)
        g.add(v, f, color=pale, emit=0.95)
    for k in range(12):
        a = 2 * math.pi * k / 12
        c = Vector((math.cos(a) * 0.3, math.sin(a) * 0.3, 0.016))
        v, f = octa(c, 0.016, 0.024)
        g.add(v, f, color=(1, 1, 1), emit=1.0)
    for k in range(24):
        a = 2 * math.pi * (k + 0.5) / 24
        d = Vector((math.cos(a), math.sin(a), 0))
        length = 0.09 if k % 2 == 0 else 0.05
        v, f = cone(d * 0.335, d, length, 0.01, sides=4)
        g.add(v, f, color=pale, emit=0.9)
    return g


col = sp_collection("SP_BatchA")
models_a = {
    "angel_wing": (angel_wing(), (0, 0, 0)),
    "demon_wing": (demon_wing(), (4, 0, 0)),
    "demon_horn": (demon_horn(), (8, 0, 0)),
    "halo": (halo(), (10, 0, 0)),
}
for name, (geo, loc) in models_a.items():
    ob = geo.obj(name, col, loc=loc)
    export_spm([ob], name)
