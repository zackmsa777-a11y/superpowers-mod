# Geometry + material helpers used by the Superpowers model scripts (loft, sphere, cone, tube...).
import bpy, bmesh, math, random
from mathutils import Vector, Matrix, Quaternion


def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def smooth(t):
    t = clamp(t)
    return t * t * (3 - 2 * t)


def fresh_material(name):
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    try:
        m.use_nodes = True
    except Exception:
        pass
    nt = m.node_tree
    if nt.animation_data:
        nt.animation_data_clear()
    nt.nodes.clear()
    return m, nt


def loft(rings, cap0=True, cap1=True, closed=True):
    n = len(rings[0])
    verts = [Vector(v) for r in rings for v in r]
    faces = []
    for i in range(len(rings) - 1):
        for j in range(n if closed else n - 1):
            a, b = i * n + j, i * n + (j + 1) % n
            faces.append((a, b, b + n, a + n))
    if cap0:
        faces.append(tuple(reversed(range(n))))
    if cap1:
        faces.append(tuple(range((len(rings) - 1) * n, len(rings) * n)))
    return verts, faces


def sphere(c, r, n=8, scale=(1, 1, 1)):
    c = Vector(c)
    seg = n * 2
    rings = []
    for i in range(1, n):
        phi = math.pi * i / n
        rings.append([c + Vector((r * scale[0] * math.sin(phi) * math.cos(2 * math.pi * k / seg),
                                  r * scale[1] * math.sin(phi) * math.sin(2 * math.pi * k / seg),
                                  r * scale[2] * math.cos(phi))) for k in range(seg)])
    v, f = loft(rings, False, False)
    top = len(v); v.append(c + Vector((0, 0, r * scale[2])))
    bot = len(v); v.append(c - Vector((0, 0, r * scale[2])))
    last = (len(rings) - 1) * seg
    for k in range(seg):
        f.append((top, (k + 1) % seg, k))
        f.append((bot, last + k, last + (k + 1) % seg))
    return v, f


def cone(base, direction, length, r, sides=6):
    base = Vector(base)
    d = Vector(direction).normalized()
    ref = Vector((0, 0, 1)) if abs(d.z) < 0.9 else Vector((1, 0, 0))
    a = d.cross(ref).normalized()
    b = d.cross(a)
    v = [base + r * (math.cos(2 * math.pi * k / sides) * a + math.sin(2 * math.pi * k / sides) * b) for k in range(sides)]
    v.append(base + d * length)
    f = [(k, (k + 1) % sides, sides) for k in range(sides)] + [tuple(reversed(range(sides)))]
    return v, f


def path_frames(path):
    n = len(path)
    T = [(path[min(i + 1, n - 1)] - path[max(i - 1, 0)]).normalized() for i in range(n)]
    ref = Vector((0, 0, 1)) if abs(T[0].z) < 0.9 else Vector((1, 0, 0))
    N = [(ref - ref.dot(T[0]) * T[0]).normalized()]
    for i in range(1, n):
        v = N[-1] - N[-1].dot(T[i]) * T[i]
        N.append(v.normalized() if v.length > 1e-9 else N[-1])
    return T, N


def tube(path, radius, sides=10, cap=True):
    """Sweep a circle along a list of Vectors. radius(t) with t in 0..1. Returns verts, faces, uv(u=t, v=around)."""
    path = [Vector(p) for p in path]
    T, N = path_frames(path)
    rings, uv = [], []
    for i, p in enumerate(path):
        t = i / (len(path) - 1)
        r = max(radius(t), 1e-5)
        B = T[i].cross(N[i])
        rings.append([p + r * (math.cos(2 * math.pi * k / sides) * N[i] + math.sin(2 * math.pi * k / sides) * B)
                      for k in range(sides)])
        uv += [(t, k / sides) for k in range(sides)]
    v, f = loft(rings, cap, cap)
    return v, f, uv


def bezier(p0, p1, p2, n):
    p0, p1, p2 = Vector(p0), Vector(p1), Vector(p2)
    return [(1 - t) ** 2 * p0 + 2 * (1 - t) * t * p1 + t * t * p2 for t in (i / (n - 1) for i in range(n))]

