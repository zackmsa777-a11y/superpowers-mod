# Superpowers

A Fabric mod for Minecraft **1.21.1** with six superpowers, each with an M1 combo, three abilities, a cinematic
ultimate, and an **Awakening** form. Every 3D model was built procedurally in Blender; every sound was synthesized.

| Power | Ultimate (V) | Awakened form |
|---|---|---|
| 🔥 Fire | Supernova | Infernal Sovereign – blue flame wings |
| 🌌 Space | Big Bang | Cosmic Arbiter – rides a meteor |
| 😇 Angel | Judgment | Seraph Ascendant – six wings |
| 😈 Demon | Pandemonium | Demon King – red wings, horns, tail |
| ⚡ God | Let There Be Light | Absolute Divinity – flies like superman |
| 👁 Entity 666 | The Feast | Becomes the Hollow Stalker itself |

## Controls

| Key | Action |
|---|---|
| Left click (empty hand) | M1 combo (4 hits) |
| Z / X / C | Abilities |
| V | Ultimate (with cutscene) |
| G | Awaken (when the meter is full) |
| F | End awakening |
| Double-tap Space | Fly (while awakened) |

In creative you get the **SP Book** automatically – right-click it to pick a power. Mana and health are infinite in
creative. Each power gives 100 health and its own health bar.

The awakening meter fills by killing the right things (Angel: hostile mobs, Demon: villagers and animals, Fire:
burning targets, Space: airborne targets, God: anything – stronger is better, Entity 666: kills in the dark or inside
its domain). While awakened, damage is ×10, areas ×2, projectiles ×3 and every power can fly.

Abilities break blocks. Turn that off with `/gamerule superpowersBlockBreaking false`.

### Commands
- `/power book` – get the SP Book
- `/power set <fire|space|angel|demon|god|entity>` (op)
- `/power meter <0-100>` (op) – fill the awakening meter for testing
- `/power clear`

## Install

1. Install Fabric Loader for Minecraft 1.21.1.
2. Put `superpowers-<version>.jar` and [Fabric API](https://modrinth.com/mod/fabric-api) in `mods/`.

**Multiplayer:** the server and every player need both jars. The mod draws its models, effects, HUD and cutscenes
on the client, so it can't be server-only.

## Build

Requires JDK 21+ (the Loom version used here runs on Java 25).

```
cd superpowers
./gradlew build
```

The jar ends up in `superpowers/build/libs/`.

`./gradlew runClient -Pautotest=1` starts a dev client that creates a flat world, fires every ability of every power
and saves screenshots to `run/screenshots/` (`-Pautotest=entity,awaken` limits it to some powers).

## Project layout

```
superpowers/                 the Fabric mod (Gradle, split client/common source sets)
  src/main/java/...          server logic: powers, awakening, destruction, the Entity 666 hunt
  src/client/java/...        rendering: 3D models, effects, creature rig, HUD, cutscenes, mixins
  src/client/resources/.../spm/   the exported 3D models (.spm static, .sps skinned)
tools/
  blender/                   procedural Blender scripts that build every model (+ the .blend)
                             sp_stalker.py rigs, skins and exports Entity 666 (hollow_stalker_meshy.blend)
  make_sounds.py             synthesizes all sound effects (numpy + ffmpeg)
  make_textures.py           generates the textures
  sheets.py                  contact sheets from autotest screenshots
```

### Rebuilding the models

The `.spm` models are already in the repo, so you don't need Blender to build the mod. To change them, open
Blender 5.x, set the working directory to `tools/blender`, run `sp_lib.py`, then the batch script you want
(`sp_models_a.py` … `sp_models_d.py`). Models are exported to your temp folder (`sp_spm/`); copy them into
`superpowers/src/client/resources/assets/superpowers/spm/`.

### Entity 666

Entity 666 is the "Hollow Stalker", a model generated with [Meshy](https://www.meshy.ai) and then rigged in
Blender: a 20-bone skeleton with automatic skin weights (`tools/blender/sp_stalker.py`). In game it is skinned on
the CPU every frame (`Creature.java`) with a two-bone IK for its hands, so it walks, swipes, grabs and opens its jaw.

## License

[MIT](LICENSE)
