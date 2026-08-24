# Liminalis resource pack

Build it with `python tools/build_pack.py`. That writes `dist/Liminalis-Pack.zip` and prints
the sha1 to paste into `server.properties`.

```
require-resource-pack=true
resource-pack=<url you host the zip at>
resource-pack-sha1=<printed by the build script>
```

**The hash changes on every rebuild.** If clients suddenly start getting kicked for a failed
pack, that line is why.

## What is in it

**Injury HUD icons** — eight 16×16 glyphs bound to private-use codepoints `U+E001`–`U+E008`,
declared in `assets/liminalis/font/hud.json` and drawn by `InjuryHud.java`. Ordinary injuries
are warm and legible; mortal wounds are desaturated with a dried-blood outline, so the wound
that will not heal on its own is distinguishable at a glance without reading anything.

The icons are generated from hand-authored pixel grids in `tools/build_icons.py` rather than
drawn in an image editor. Edit the ASCII art there and re-run it — the grids are far easier to
tweak than a PNG, and the palette lives in one place.

If you add a new injury and forget its icon, the plugin logs a warning at startup naming the
injury and the two files to change. It will not silently show players an empty box.

**`pack.png`** — the pack icon. A grey gradient with a faint band across it, which is roughly
what the pale garden looks like from a distance.

## What is NOT in it, and why

**Singularity creature textures.** These are genuinely not done, and it is worth being precise
about why rather than leaving a half-built scaffold.

A resource pack cannot retexture *only* the Singularity zombies. Replacing `zombie.png`
replaces the texture on every zombie on the server, which is not what anyone wants. The
standard way to give specific mobs a distinct look without a client mod is to equip them with
a custom-modelled helmet — an item with `custom_model_data` whose model is a 3D head — and
have the plugin put that on them when they spawn.

That needs two things I cannot make: a UV-mapped texture and a model. Procedurally generated
art at that size looks like noise on a rigged model, which would be worse than the vanilla
skins they currently wear.

Everything else about the creatures is already built — their attributes, names, particle
auras, sounds and drops are all theirs. When the art exists, wiring it up is:

1. Put the texture at `assets/minecraft/textures/item/<name>.png`
2. Add the model at `assets/minecraft/models/item/<name>.json`
3. Equip it in `SingularityService.spawnAt`, alongside where the aura is attached

## Adding an icon for a new injury

1. Add an ASCII grid and a palette entry to `tools/build_icons.py`
2. Run `python tools/build_icons.py`
3. Add a provider to `assets/liminalis/font/hud.json` with the next free codepoint
4. Map the injury id to that codepoint in `InjuryHud.GLYPHS`
5. Rebuild the pack, and update the sha1

Steps 3 and 4 have to agree or players see empty boxes. The startup warning catches step 4
being missed; nothing catches step 3, so check the font file.
