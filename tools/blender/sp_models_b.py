# Batch B: god ring + disc, holy lance, god sword, meteor, demon spikes, planet, fire crystal,
# demon core, flame crown, demon tail, star crown, codex book.  Run after sp_lib.py + sp_models_a.py helpers.

GOLD = (1.0, 0.74, 0.22)
GOLD_LIGHT = (1.0, 0.9, 0.55)
WHITE = (1.0, 1.0, 1.0)


def box(c, sx, sy, sz, xf=None):
    c = Vector(c)
    v = [c + Vector((x * sx, y * sy, z * sz)) for x in (-1, 1) for y in (-1, 1) for z in (-1, 1)]
    f = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    if xf is not None:
        v = [xf @ p for p in v]
    return v, f


def ring_band(R, half_w, thick, seg=96):
    """Rectangular-section ring around Z: radial thickness `thick`, axial width 2*half_w."""
    prof = [(R - thick / 2, -half_w), (R + thick / 2, -half_w), (R + thick / 2, half_w), (R - thick / 2, half_w)]
    rings = []
    for i in range(seg):
        a = 2 * math.pi * i / seg
        rings.append([Vector((math.cos(a) * r, math.sin(a) * r, z)) for r, z in prof])
    v = [p for r in rings for p in r]
    f = []
    for i in range(seg):
        for j in range(4):
            a = i * 4 + j
            b = i * 4 + (j + 1) % 4
            c = ((i + 1) % seg) * 4 + (j + 1) % 4
            d = ((i + 1) % seg) * 4 + j
            f.append((a, b, c, d))
    return v, f


# ------------------------------------------------------------------ god

def god_ring():
    g = Geo()
    v, f = ring_band(1.0, 0.07, 0.05)
    g.add(v, f, color=GOLD, emit=0.35)
    v, f = torus_verts(1.0, 0.012, 0.012, seg=96, sides=6)
    # glowing inner seam
    g.add([p - Vector((0, 0, 0)) * 0 for p in v], f, color=(1, 0.97, 0.8), emit=1.0,
          xf=Matrix.Diagonal((0.972, 0.972, 1, 1)))
    for k in range(24):
        a = 2 * math.pi * k / 24
        big = k % 3 == 0
        xf = Matrix.Translation((math.cos(a) * 1.035, math.sin(a) * 1.035, 0)) @ Matrix.Rotation(a, 4, 'Z')
        v, f = box((0, 0, 0), 0.012, 0.03 if big else 0.018, 0.05 if big else 0.03, xf=xf)
        g.add(v, f, color=(1, 0.95, 0.75), emit=0.9 if big else 0.6)
    return g


def god_disc():
    """Sun disc in the Blender XZ plane, facing -Y (forward)."""
    g = Geo()
    to_xz = Matrix.Rotation(math.radians(90), 4, 'X')
    # domed centre
    v, f = sphere((0, 0, 0), 0.22, n=8, scale=(1, 1, 0.35))
    g.add(v, f, cfun=lambda i, p: mixc(WHITE, GOLD_LIGHT, abs(p.z) * 4), emit=0.9, xf=to_xz)
    v, f = torus_verts(0.3, 0.035, 0.02, seg=64, sides=8)
    g.add(v, f, color=GOLD, emit=0.5, xf=to_xz)
    v, f = torus_verts(0.64, 0.012, 0.012, seg=72, sides=6)
    g.add(v, f, color=GOLD_LIGHT, emit=0.9, xf=to_xz)
    for k in range(16):
        a = 2 * math.pi * k / 16
        length = 0.62 if k % 2 == 0 else 0.5
        d = Vector((math.cos(a), math.sin(a), 0))
        s = Vector((-d.y, d.x, 0))
        r0 = 0.34
        base_w = 0.06 if k % 2 == 0 else 0.045
        verts = [d * r0 + s * base_w + Vector((0, 0, 0.015)), d * r0 - s * base_w + Vector((0, 0, 0.015)), d * length + Vector((0, 0, 0.005)),
                 d * r0 + s * base_w - Vector((0, 0, 0.015)), d * r0 - s * base_w - Vector((0, 0, 0.015)), d * length - Vector((0, 0, 0.005))]
        faces = [(0, 1, 2), (3, 5, 4), (0, 2, 5, 3), (1, 4, 5, 2), (0, 3, 4, 1)]
        g.add(verts, faces, cfun=lambda i, p: mixc(GOLD, GOLD_LIGHT, p.length / 0.62), emit=0.7, xf=to_xz)
    return g


def holy_lance():
    """Spear along -Y (forward). Tip at y = -1.35, butt at y = +1.3."""
    g = Geo()
    shaft = [Vector((0, y, 0)) for y in [1.3 - i * (1.8 / 30) for i in range(31)]]
    v, f, _ = tube(shaft, lambda t: 0.032, sides=10)
    g.add(v, f, color=(0.97, 0.93, 0.8), emit=0.4)
    for y in (1.2, 0.8, 0.2, -0.3, -0.45):
        v, f = torus_verts(0.038, 0.012, 0.018, seg=16, sides=6)
        g.add(v, f, color=GOLD, emit=0.6, xf=Matrix.Translation((0, y, 0)) @ Matrix.Rotation(math.radians(90), 4, 'X'))
    v, f = cone((0, 1.3, 0), (0, 1, 0), 0.14, 0.035, sides=8)
    g.add(v, f, color=GOLD, emit=0.6)
    # leaf blade: diamond cross-section lofted from y=-0.5 to tip
    rings = []
    n = 16
    for i in range(n + 1):
        s = i / n
        y = -0.5 - 0.85 * s
        w = 0.15 * math.sin(math.pi * min(1.0, 0.15 + s * 0.95)) ** 0.7 * (1 - s) ** 0.35 + 0.004
        t = 0.03 * (1 - s) + 0.003
        rings.append([Vector((w, y, 0)), Vector((0, y, t)), Vector((-w, y, 0)), Vector((0, y, -t))])
    v, f = loft(rings, True, True)
    g.add(v, f, cfun=lambda i, p: mixc((1, 1, 1), (0.75, 0.9, 1), (-p.y - 0.5) / 0.85) if i % 2 == 1 else (0.85, 0.95, 1.0),
          efun=lambda i, p: 0.95 if i % 2 == 0 else 0.7)
    # side wings at the base of the blade
    for sgn in (-1, 1):
        crv = bezier((0, -0.48, 0), (sgn * 0.16, -0.42, 0), (sgn * 0.2, -0.62, 0), 10)
        v, f, _ = tube(crv, lambda t: 0.02 * (1 - t) + 0.004, sides=6)
        g.add(v, f, color=GOLD_LIGHT, emit=0.8)
    return g


def god_sword():
    """Blade along -Y. Guard at y=0, tip at y=-1.5, pommel at y=+0.4."""
    g = Geo()
    rings = []
    n = 24
    for i in range(n + 1):
        s = i / n
        y = -0.04 - 1.46 * s
        w = 0.085 * (1 - 0.3 * s)
        if s > 0.82:
            w *= max(0.0, 1 - (s - 0.82) / 0.18) ** 0.8
        w = max(w, 0.002)
        t = 0.018 * (1 - 0.6 * s) + 0.002
        fw = w * 0.35
        rings.append([Vector((w, y, 0)), Vector((fw, y, t)), Vector((0, y, t * 0.55)), Vector((-fw, y, t)),
                      Vector((-w, y, 0)), Vector((-fw, y, -t)), Vector((0, y, -t * 0.55)), Vector((fw, y, -t))])
    v, f = loft(rings, True, True)
    g.add(v, f, cfun=lambda i, p: (1, 0.97, 0.85) if i % 4 == 0 else ((1, 0.84, 0.4) if i % 8 in (2, 6) else (1, 0.9, 0.6)),
          efun=lambda i, p: 0.95 if i % 4 == 0 else 0.45)
    # crossguard with up-swept wings
    v, f = box((0, 0, 0), 0.11, 0.04, 0.035)
    g.add(v, f, color=GOLD, emit=0.3)
    for sgn in (-1, 1):
        crv = bezier((sgn * 0.08, 0, 0), (sgn * 0.3, 0.02, 0), (sgn * 0.36, -0.16, 0), 14)
        v, f, _ = tube(crv, lambda t: 0.035 * (1 - t) + 0.006, sides=8)
        g.add(v, f, cfun=lambda i, p: mixc(GOLD, GOLD_LIGHT, abs(p.x) / 0.36), emit=0.45)
        for k in range(3):
            fb = bezier((sgn * (0.12 + k * 0.07), 0.02, 0), (sgn * (0.2 + k * 0.08), 0.12, 0), (sgn * (0.26 + k * 0.06), 0.2 - k * 0.03, 0), 8)
            v, f, _ = tube(fb, lambda t: 0.016 * (1 - t) + 0.003, sides=5)
            g.add(v, f, color=GOLD_LIGHT, emit=0.55)
    v, f = octa((0, -0.045, 0), 0.03, 0.04)
    g.add(v, f, color=(0.55, 0.95, 1.0), emit=1.0)
    # grip with wrap ridges
    grip = [Vector((0, 0.04 + i * 0.3 / 24, 0)) for i in range(25)]
    v, f, _ = tube(grip, lambda t: 0.032 + 0.006 * math.sin(t * 50), sides=10)
    g.add(v, f, color=(0.45, 0.14, 0.08))
    v, f = sphere((0, 0.38, 0), 0.05, n=6)
    g.add(v, f, color=GOLD, emit=0.4)
    v, f = cone((0, 0.42, 0), (0, 1, 0), 0.08, 0.02, sides=6)
    g.add(v, f, color=GOLD_LIGHT, emit=0.7)
    return g


# ------------------------------------------------------------------ space

def meteor():
    g = Geo()
    v, f = ico(0.75, subdiv=4)
    rock_a, rock_b, lava = (0.09, 0.08, 0.09), (0.22, 0.19, 0.2), (1.0, 0.45, 0.08)
    crack = []
    out = []
    for p in v:
        d = p.normalized()
        h = 1 + 0.22 * noise.noise(d * 1.6) + 0.08 * noise.noise(d * 4.5 + Vector((3, 1, 2)))
        dist, _ = noise.voronoi(d * 3.2, distance_metric='DISTANCE')
        edge = dist[1] - dist[0]
        c = max(0.0, 1 - edge / 0.07)
        crack.append(c)
        out.append(d * 0.75 * (h - 0.05 * c))
    tone = [0.5 + 0.5 * noise.noise(p * 3) for p in out]
    g.add(out, f, cfun=lambda i, p: mixc(mixc(rock_a, rock_b, tone[i]), lava, crack[i] ** 1.5),
          efun=lambda i, p: crack[i] ** 1.2)
    return g


def demon_spike(seed):
    rnd = random.Random(seed)
    g = Geo()
    sides = 9
    levels = [(0.0, 0.32), (0.12, 0.3), (0.28, 0.23), (0.45, 0.18), (0.62, 0.12), (0.8, 0.065), (0.9, 0.03)]
    twist = rnd.uniform(0.3, 0.7)
    lean = Vector((rnd.uniform(-0.1, 0.1), rnd.uniform(-0.1, 0.1), 0))
    rings = []
    for z, r in levels:
        ring = []
        for k in range(sides):
            a = 2 * math.pi * k / sides + twist * z
            rr = r * rnd.uniform(0.75, 1.15)
            ring.append(Vector((math.cos(a) * rr, math.sin(a) * rr, z)) + lean * z)
        rings.append(ring)
    v, f = loft(rings, True, False)
    tip = len(v)
    v.append(Vector((0, 0, 1.0)) + lean)
    last = (len(rings) - 1) * sides
    for k in range(sides):
        f.append((last + k, last + (k + 1) % sides, tip))
    g.add(v, f, cfun=lambda i, p: mixc((0.08, 0.04, 0.06), (0.95, 0.1, 0.15), max(0, p.z - 0.6) / 0.4) if i % 2 else (0.14, 0.07, 0.09),
          efun=lambda i, p: max(0, p.z - 0.55) / 0.45 * (0.9 if i % 2 else 0.4))
    # side shards
    for s in range(2):
        a = rnd.uniform(0, 2 * math.pi)
        base = Vector((math.cos(a) * 0.22, math.sin(a) * 0.22, 0))
        d = Vector((math.cos(a) * 0.5, math.sin(a) * 0.5, 1)).normalized()
        v, f = cone(base, d, rnd.uniform(0.35, 0.5), 0.09, sides=5)
        g.add(v, f, cfun=lambda i, p: (0.9, 0.12, 0.15) if i == 5 else (0.1, 0.05, 0.07), efun=lambda i, p: 0.8 if i == 5 else 0.0)
    return g


def planet():
    g = Geo()
    v, f = sphere((0, 0, 0), 0.5, n=12)
    bands = [(0.42, 0.24, 0.85), (0.32, 0.55, 0.95), (0.62, 0.36, 0.95), (0.25, 0.75, 0.9)]
    g.add(v, f, cfun=lambda i, p: bands[int((p.z + 0.5 + 0.06 * noise.noise(p * 6)) * 9) % 4], emit=0.25)
    tilt = Matrix.Rotation(math.radians(24), 4, 'X')
    rv, rf = [], []
    seg = 64
    for i in range(seg):
        a = 2 * math.pi * i / seg
        for r in (0.68, 0.84, 0.86, 1.02):
            rv.append(Vector((math.cos(a) * r, math.sin(a) * r, 0)))
    for i in range(seg):
        for j in range(3):
            a = i * 4 + j
            b = ((i + 1) % seg) * 4 + j
            rf.append((a, b, b + 1, a + 1))
    g.add(rv, rf, cfun=lambda i, p: (0.8, 0.7, 1.0) if (i % 4) < 2 else (0.55, 0.9, 1.0), emit=0.7, xf=tilt)
    return g


def star_shape(c, R, r, thick, n=4, xf=None):
    v, f = [], []
    pts = []
    for k in range(n * 2):
        a = math.pi * k / n
        rr = R if k % 2 == 0 else r
        pts.append(Vector((math.cos(a) * rr, 0, math.sin(a) * rr)))
    c = Vector(c)
    front = c + Vector((0, -thick, 0))
    back = c + Vector((0, thick, 0))
    v = [c + p for p in pts] + [front, back]
    m = len(pts)
    for k in range(m):
        f.append((k, (k + 1) % m, m))
        f.append(((k + 1) % m, k, m + 1))
    if xf is not None:
        v = [xf @ p for p in v]
    return v, f


def star_crown():
    g = Geo()
    v, f = torus_verts(0.36, 0.012, 0.012, seg=64, sides=6)
    g.add(v, f, color=(0.75, 0.85, 1.0), emit=0.9)
    for k in range(7):
        a = 2 * math.pi * k / 7
        xf = Matrix.Translation((math.cos(a) * 0.36, math.sin(a) * 0.36, 0.1)) @ Matrix.Rotation(a + math.pi / 2, 4, 'Z')
        big = k == 0
        v, f = star_shape((0, 0, 0), 0.12 if big else 0.08, 0.025, 0.012, xf=xf)
        g.add(v, f, cfun=lambda i, p: WHITE if i >= 8 else (0.7, 0.85, 1.0), emit=1.0)
    return g


# ------------------------------------------------------------------ fire

def fire_crystal():
    g = Geo()
    sides = 8
    rnd = random.Random(5)
    rings = []
    for z, r in ((-0.75, 0.01), (-0.5, 0.2), (-0.25, 0.31), (0.1, 0.35), (0.45, 0.27), (0.75, 0.13), (0.95, 0.01)):
        rings.append([Vector((math.cos(2 * math.pi * k / sides + z * 0.6) * r * rnd.uniform(0.85, 1.1),
                              math.sin(2 * math.pi * k / sides + z * 0.6) * r * rnd.uniform(0.85, 1.1), z)) for k in range(sides)])
    v, f = loft(rings, True, True)
    g.add(v, f, cfun=lambda i, p: mixc((0.85, 0.2, 0.03), (1.0, 0.92, 0.5), (p.z + 0.75) / 1.7) if i % 2 else (1.0, 0.6, 0.15),
          efun=lambda i, p: 0.7 + 0.3 * ((p.z + 0.75) / 1.7))
    for k in range(5):
        a = 2 * math.pi * k / 5 + 0.4
        c = Vector((math.cos(a) * 0.62, math.sin(a) * 0.62, -0.35 + 0.18 * k))
        v, f = octa(c, 0.07, 0.15)
        g.add(v, f, color=(1.0, 0.75, 0.25), emit=0.95)
    return g


def flame_crown():
    g = Geo()
    n = 9
    for k in range(n):
        a = 2 * math.pi * k / n
        d = Vector((math.cos(a), math.sin(a), 0))
        base = d * 0.28
        h = 0.32 if k % 2 == 0 else 0.22
        crv = bezier(base, base + d * 0.06 + Vector((0, 0, h * 0.5)), base - d * 0.04 + Vector((0, 0, h)), 12)
        v, f, _ = tube(crv, lambda t: 0.055 * (1 - t) ** 1.2 + 0.002, sides=6)
        g.add(v, f, cfun=lambda i, p: mixc((1.0, 0.35, 0.05), (1.0, 0.95, 0.5), (i // 6) / 11), emit=1.0)
    v, f = torus_verts(0.28, 0.025, 0.02, seg=48, sides=6)
    g.add(v, f, color=(1.0, 0.5, 0.1), emit=0.9)
    return g


# ------------------------------------------------------------------ demon

def demon_core():
    g = Geo()
    v, f = ico(0.42, subdiv=1)
    g.add(v, f, color=(0.95, 0.12, 0.15), emit=0.9)
    pts, _ = ico(1.0, subdiv=1)
    for p in pts:
        d = p.normalized()
        v, f = cone(d * 0.38, d, 0.55, 0.11, sides=5)
        g.add(v, f, cfun=lambda i, p: (1, 0.3, 0.2) if i == 5 else (0.18, 0.04, 0.06), efun=lambda i, p: 0.9 if i == 5 else 0.0)
    return g


def demon_tail():
    """Starts at origin (lower back), runs backward (+Y) and down, then curls up to a spade."""
    g = Geo()
    path = catmull([(0, 0, 0), (0, 0.35, -0.25), (0.05, 0.8, -0.5), (0.0, 1.25, -0.4), (-0.05, 1.55, -0.05), (0, 1.62, 0.25)], 48)
    v, f, _ = tube(path, lambda t: 0.065 * (1 - t) + 0.014 + 0.006 * math.sin(t * 60) * (1 - t), sides=10)
    g.add(v, f, cfun=lambda i, p: mixc((0.18, 0.05, 0.06), (0.45, 0.06, 0.08), (i // 10) / 47), emit=0.0)
    tip = path[-1]
    d = (path[-1] - path[-4]).normalized()
    fwd, side, up = frame_from(d, Vector((0, 1, 0)))
    spade = [tip, tip + side * 0.12 + fwd * 0.06, tip + fwd * 0.26, tip - side * 0.12 + fwd * 0.06,
             tip + up * 0.025 + fwd * 0.08, tip - up * 0.025 + fwd * 0.08]
    sf = [(0, 1, 4), (1, 2, 4), (2, 3, 4), (3, 0, 4), (1, 0, 5), (2, 1, 5), (3, 2, 5), (0, 3, 5)]
    g.add(spade, sf, color=(1.0, 0.18, 0.12), emit=0.85)
    return g


# ------------------------------------------------------------------ the book

def codex_book():
    """Closed tome, front cover facing -Y. 0.62 wide, 0.8 tall, 0.2 thick."""
    g = Geo()
    purple, purple_dark, page = (0.36, 0.14, 0.55), (0.2, 0.07, 0.3), (0.93, 0.88, 0.76)
    for y, c in ((-0.085, purple), (0.085, purple)):
        v, f = box((0.01, y, 0), 0.32, 0.018, 0.405)
        g.add(v, f, color=c)
    v, f = box((0.03, 0, 0), 0.285, 0.07, 0.385)
    g.add(v, f, cfun=lambda i, p: page if p.x > 0 else (0.85, 0.8, 0.68))
    spine = []
    for k in range(9):
        a = math.pi / 2 + math.pi * k / 8
        spine.append(Vector((-0.3 + math.cos(a) * 0.06, math.sin(a) * 0.105, 0)))
    rings = [[p + Vector((0, 0, z)) for p in spine] for z in (-0.405, 0.405)]
    v, f = loft(rings, True, True, closed=False)
    g.add(v, f, color=purple_dark)
    for z in (-0.25, 0.25):
        v, f = box((-0.33, 0, z), 0.012, 0.1, 0.025)
        g.add(v, f, color=GOLD, emit=0.3)
    for sx in (-1, 1):
        for sz in (-1, 1):
            v, f = box((0.01 + sx * 0.28, -0.1, sz * 0.36), 0.045, 0.006, 0.045)
            g.add(v, f, color=GOLD, emit=0.35)
    v, f = torus_verts(0.13, 0.014, 0.008, seg=40, sides=6)
    g.add(v, f, color=GOLD, emit=0.4, xf=Matrix.Translation((0.01, -0.106, 0)) @ Matrix.Rotation(math.radians(90), 4, 'X'))
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        v, f = cone((0.01 + math.cos(a) * 0.13, -0.106, math.sin(a) * 0.13), (math.cos(a), 0, math.sin(a)), 0.08, 0.02, sides=4)
        g.add(v, f, color=GOLD_LIGHT, emit=0.5)
    v, f = octa((0.01, -0.12, 0), 0.06, 0.085)
    g.add(v, f, cfun=lambda i, p: (1.0, 0.55, 1.0) if i == 3 else (0.75, 0.25, 0.95), emit=1.0)
    v, f = box((0.33, -0.05, 0), 0.03, 0.06, 0.06)
    g.add(v, f, color=GOLD, emit=0.3)
    return g


col = sp_collection("SP_BatchB")
models_b = {
    "god_ring": (god_ring(), (0, 6, 0)),
    "god_disc": (god_disc(), (3, 6, 0)),
    "holy_lance": (holy_lance(), (5, 6, 0)),
    "god_sword": (god_sword(), (6.5, 6, 0)),
    "meteor": (meteor(), (8.5, 6, 0)),
    "demon_spike_a": (demon_spike(7), (10.5, 6, 0)),
    "demon_spike_b": (demon_spike(21), (11.5, 6, 0)),
    "planet": (planet(), (13.5, 6, 0)),
    "fire_crystal": (fire_crystal(), (0, 9, 0)),
    "flame_crown": (flame_crown(), (2, 9, 0)),
    "demon_core": (demon_core(), (4, 9, 0)),
    "demon_tail": (demon_tail(), (6, 9, 0)),
    "star_crown": (star_crown(), (8, 9, 0)),
    "codex_book": (codex_book(), (10, 9, 0)),
}
for name, (geo, loc) in models_b.items():
    ob = geo.obj(name, col, loc=loc, sharp=40)
    export_spm([ob], name)
