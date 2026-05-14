# Schematician

A drafting-view post-process **and** in-world physics overlay for Create: Aeronautics, gated behind a wearable item.

While **Schematician's Goggles** are equipped:

- The world is overlaid with a Create: Aeronautics drafting / blueprint pipeline (edge detection + palette tone-mapping + dither).
- Looking at any Aeronautics sublevel surfaces its **center of mass** and live **force vectors** (lift, drag, thrust, gravity, …) drawn on top of the contraption — like the Contraption Diagram, but in-world.
- Create's own goggle HUD (stress, fluid content, etc.) is preserved.

The goggles can be toggled in-hand (right-click) without unequipping.

## Requirements

All required — Schematician is built around the Create / Aeronautics / Simulated stack and integrates with each directly.

- Minecraft 1.21.1
- NeoForge 21.1.226+
- [Veil](https://modrinth.com/mod/veil) (FoundryMC) — drives the post-process pipeline and custom render types
- [Create](https://www.curseforge.com/minecraft/mc-mods/create) — provides the goggles overlay system (stress, fluid, rotation tooltips); also required transitively via Aeronautics
- [Create: Aeronautics](https://github.com/Creators-of-Aeronautics/Simulated-Project) — the drafting-view shader is derived from Aeronautics' `outline_diagram` pipeline and the goggles recipe consumes Aviator's Goggles
- [Simulated](https://github.com/Creators-of-Aeronautics/Simulated-Project) — supplies the Physics Assembler used in the recipe; the force overlay mirrors its Contraption Diagram data path
- [Sable](https://github.com/ryanhcode/sable) — sublevel / rigid-body framework Aeronautics is built on; the overlay reads force snapshots from its `ServerSubLevel`

## Crafting

`Aviator's Goggles` (Create: Aeronautics) + `Contraption Diagram` (Simulated) → `Schematician's Goggles` (shapeless).

## Force overlay

While the drafting view is on, the goggles raycast from the camera. If the crosshair lands inside a sublevel, that sublevel becomes the inspection target and:

- A small grey cube is drawn at the **center of mass** (white while the first force snapshot is in flight).
- Each **force group** active on the sublevel is drawn as one or more colored arrows. Arrows are placed at the force-application point and aimed along the force; length is proportional to magnitude.
- Near-parallel forces inside a group are merged into one arrow (mirroring Simulated's `ForceClusterFinder`).
- Each cluster is temporally smoothed across snapshots so jittery groups (drag in particular) read steady.
- Past `~5.7` blocks of length the arrow *head* (cone tip + shaft thickness) freezes proportionally; only the shaft extends further. Caps the visual size so long arrows still look like arrows.

The overlay survives opening and closing the Contraption Diagram on the same sublevel — the dispatcher re-asserts Sable's individual-force-tracking flag every physics tick while a subscription is live.

### Config

`config/schematician-client.toml` is generated on first launch. All values hot-reload on file save (you may need to rejoin the world for some to take effect).

| key | default | effect |
| --- | --- | --- |
| `targetingChunks` | 4 | Max raycast distance in chunks. The crosshair must land within this radius to inspect a sublevel. |
| `metersPerNewton` | 0.0002 | Arrow length scale. 1 block per 5 kN; calibrated so a chunk-sized sublevel's gravity reads ~5 blocks. |
| `minArrowLength` | 0.3 | Floor for tiny forces so they're still visible. |
| `maxArrowLength` | 8.0 | Cap on total arrow length. Past ~5.7 blocks the head freezes; only the shaft extends. |
| `clusterAngleRadians` | 0.4 | Forces within this angle (radians) merge into one cluster arrow. |
| `smoothingFactor` | 0.25 | EMA factor across snapshots. Lower = smoother but laggier. |

## Tooltip text

The in-game tooltip on the goggles uses Create's shift-expand format (same system as Aviator's Goggles). All copy lives in [`src/main/resources/assets/schematician/lang/en_us.json`](src/main/resources/assets/schematician/lang/en_us.json) under these keys:

| Lang key (suffix on `item.schematician.schematicians_goggles.tooltip.`) | Where it shows | Notes |
| --- | --- | --- |
| `summary` | Inside the Shift expansion (top) | Wrap keywords in `_underscores_` for the yellow highlight. |
| `condition1` | Inside the Shift expansion, gray header | e.g. "When looking at a sublevel". Add `condition2`, `condition3`, … to chain more sections. |
| `behaviour1` | Inside the Shift expansion, indented under `condition1` | Same `_underscore_` highlighting. Pairs 1:1 with the matching `conditionN`. |
| `state.on` / `state.off` | Always visible | Driven by the `drafting_view_enabled` ItemStack component. |
| `toggle_hint` | Beside the state line, only while Shift is held | Discoverability for the right-click toggle. |

The Shift-expand wiring is registered in [`compat/CreateCompat.java`](src/main/java/com/alexguha/schematician/compat/CreateCompat.java) (`registerGogglesTooltip`), and the always-visible state line + Shift-only toggle hint are emitted from [`item/SchematicianGogglesItem.java`](src/main/java/com/alexguha/schematician/item/SchematicianGogglesItem.java) (`appendHoverText`).

## Roadmap

- [ ] **Unique item icon** — `src/main/resources/assets/schematician/textures/item/schematicians_goggles.png` (16×16). Currently a placeholder derived from Aviator's Goggles.
- [ ] **Unique armor model layers** — `assets/schematician/textures/models/armor/schematicians_goggles_layer_{1,2}.png` (64×32). Currently placeholders.
- [ ] Reuse the icon as `src/main/resources/logo.png` (256×256) for the CurseForge / Modrinth listing.
- [ ] Magnitude labels on force arrows (toggleable).
- [ ] Tier variants (palette / pixelate intensity).
- [ ] Per-player palette tuning (config or in-game UI).

## Toolchain

- Java 21
- NeoForge 21.1.226
- ModDevGradle 2.0.141
- Parchment mappings on top of Mojmap

## Build

```bash
./gradlew build
```

Output jar lands in `build/libs/`.

## Credits

Drafting view shader pipeline derived from work by the Creators-of-Aeronautics team in the [Simulated-Project](https://github.com/Creators-of-Aeronautics/Simulated-Project) repository (`outline_diagram` shader family). The in-world force-overlay data path (force-group tracking, gravity synthesis, clustering) mirrors Simulated's Contraption Diagram.

## License

MIT — see [LICENSE](LICENSE).
