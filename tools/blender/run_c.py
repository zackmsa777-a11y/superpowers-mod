# Headless runner: blender -b --python run_c.py  (builds batch C, exports .spm, renders a preview)
import os, bpy, math
R = os.path.dirname(os.path.abspath(__file__))
g = globals()
exec(open(os.path.join(R, "sp_lib.py"), encoding="utf-8").read(), g)
exec(open(os.path.join(R, "sp_models_c.py"), encoding="utf-8").read(), g)

# quick preview render of the assembled creature
scn = bpy.context.scene
for o in list(scn.collection.objects):
    if o.type in ('MESH',) and o.name in ('Cube',):
        bpy.data.objects.remove(o, do_unlink=True)
cam = bpy.data.objects.new("PreviewCam", bpy.data.cameras.new("PreviewCam"))
scn.collection.objects.link(cam)
light = bpy.data.objects.new("PreviewSun", bpy.data.lights.new("PreviewSun", 'SUN'))
light.data.energy = 3.0
light.rotation_euler = (math.radians(50), 0, math.radians(-30))
scn.collection.objects.link(light)
scn.camera = cam
scn.render.resolution_x, scn.render.resolution_y = 900, 900
scn.world.color = (0.25, 0.25, 0.28)
for name, loc, rot in (("front", (0.6, -5.2, 2.2), (math.radians(87), 0, math.radians(7))),
                       ("side", (5.2, -1.2, 2.2), (math.radians(87), 0, math.radians(77))),
                       ("face", (0.0, -1.3, 3.4), (math.radians(90), 0, 0))):
    cam.location = loc
    cam.rotation_euler = rot
    cam.data.lens = 35 if name != "face" else 50
    scn.render.filepath = os.path.join(OUT, "preview_entity_" + name + ".png")
    bpy.ops.render.render(write_still=True)
print("previews in", OUT)
