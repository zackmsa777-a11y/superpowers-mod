# Batch D: torn_spine - a spine ripped out together with the head. Origin = where it leaves the skull,
# hanging down -Z about 0.8 blocks; ragged bloody tissue at the top, rib stubs, dripping end.
# Run after sp_lib.py and sp_models_c.py (uses organic(), chain helpers and colours from there).

BONE_A = (0.86, 0.8, 0.66)
BONE_B = (0.62, 0.52, 0.4)
FLESH = (0.42, 0.04, 0.05)
FLESH_WET = (0.62, 0.06, 0.07)


def torn_spine():
    g = Geo()
    random.seed(4)
    n = 14
    L = 0.78
    path = []
    for i in range(n):
        t = i / (n - 1)
        path.append(Vector((0.02 * math.sin(t * 5.0), 0.03 * math.sin(t * 3.2 + 0.5), -L * t)))
    for i, c in enumerate(path):
        t = i / (n - 1)
        r = 0.045 - 0.018 * t
        blood = max(0.0, 1 - t * 2.2) * 0.85 + 0.25 * random.random()
        # vertebral body
        v, f = sphere(c, r, n=6, scale=(1.0, 0.85, 0.55))
        g.add(v, f, cfun=lambda k, p, b=blood: mixc(mixc(BONE_A, BONE_B, 0.5 + 0.5 * noise.noise(p * 30)), FLESH, b))
        # spinous process sticking out the back, transverse processes to the sides
        v, f = cone(c + Vector((0, 0.02, 0)), (0, 1, -0.5), r * 1.4, r * 0.35, sides=5)
        g.add(v, f, color=mixc(BONE_A, FLESH, blood * 0.8))
        for s in (-1, 1):
            v, f = cone(c, (s, 0.3, -0.1), r * 1.25, r * 0.28, sides=4)
            g.add(v, f, color=mixc(BONE_B, FLESH, blood * 0.8))
        # disc / tissue between vertebrae
        if i < n - 1:
            mid = (c + path[i + 1]) / 2
            v, f = sphere(mid, r * 0.8, n=5, scale=(1, 0.8, 0.45))
            g.add(v, f, color=FLESH_WET, emit=0.08)
    # rib stubs, snapped
    for k, i in enumerate((2, 3, 4, 5)):
        c = path[i]
        for s in (-1, 1):
            L2 = random.uniform(0.07, 0.16)
            pts = [c + Vector((s * 0.03, 0.01, 0)), c + Vector((s * (0.03 + L2 * 0.6), -0.02, -0.02)), c + Vector((s * (0.03 + L2), -0.06, -0.05))]
            v, f, _ = tube(pts, lambda t: 0.011 * (1 - 0.5 * t), sides=5)
            g.add(v, f, cfun=lambda kk, p: mixc(BONE_A, FLESH, 0.4 + 0.3 * random.random()))
    # ragged flesh and torn cord at the top
    for k in range(10):
        a = random.uniform(0, 2 * math.pi)
        L3 = random.uniform(0.05, 0.16)
        top = Vector((math.cos(a) * 0.035, math.sin(a) * 0.035, 0.02))
        pts = [top, top + Vector((math.cos(a) * 0.02, math.sin(a) * 0.02, -L3 * 0.5)), top + Vector((math.cos(a) * 0.03, math.sin(a) * 0.03, -L3))]
        v, f, _ = tube(pts, lambda t: 0.012 * (1 - t) + 0.003, sides=4)
        g.add(v, f, color=FLESH_WET, emit=0.1)
    v, f = sphere((0, 0, 0.02), 0.05, n=6, scale=(1, 1, 0.6))
    g.add(v, f, color=FLESH, emit=0.08)
    # blood dripping off the end
    end = path[-1]
    for k in range(3):
        off = Vector((random.uniform(-0.015, 0.015), random.uniform(-0.015, 0.015), 0))
        Ld = random.uniform(0.04, 0.12)
        v, f, _ = tube([end + off, end + off + Vector((0, 0, -Ld))], lambda t: 0.006 * (1 - t * 0.4), sides=4)
        g.add(v, f, color=FLESH_WET, emit=0.1)
        v, f = sphere(end + off + Vector((0, 0, -Ld - 0.006)), 0.008, n=4)
        g.add(v, f, color=FLESH_WET, emit=0.1)
    return g


col_d = sp_collection("SP_BatchD_Gore")
ob = torn_spine().obj("torn_spine", col_d, loc=(0, 0, 0), sharp=40)
export_spm([ob], "torn_spine")
ob.location = (2.0, 0, 1.2)
print("torn spine done")
