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
- [Veil](https://modrinth.com/mod/veil) (FoundryMC) **4.0.0 exactly** — drives the post-process pipeline and custom render types. Pinned because 4.0.1+ has visual glitches under Iris/shaders.
- [Create](https://www.curseforge.com/minecraft/mc-mods/create) — provides the goggles overlay system (stress, fluid, rotation tooltips); also required transitively via Aeronautics
- [Create: Aeronautics](https://github.com/Creators-of-Aeronautics/Simulated-Project) — the drafting-view shader is derived from Aeronautics' `outline_diagram` pipeline and the goggles recipe consumes Aviator's Goggles
- [Simulated](https://github.com/Creators-of-Aeronautics/Simulated-Project) — supplies the Contraption Diagram used in the recipe; the force overlay mirrors its Contraption Diagram data path
- [Sable](https://github.com/ryanhcode/sable) — sublevel / rigid-body framework Aeronautics is built on; the overlay reads force snapshots from its `ServerSubLevel`

## Crafting

`Aviator's Goggles` (Create: Aeronautics) + `Contraption Diagram` (Simulated) → `Schematician's Goggles` (shapeless).

## Force overlay

While the drafting view is on, the goggles raycast from the camera. If the crosshair lands inside a sublevel, that sublevel becomes the inspection target and:

- A small grey cube is drawn at the **center of mass** (white while the first force snapshot is in flight).
- Each **force group** active on the sublevel is drawn as one or more colored arrows. Arrows are placed at the force-application point and aimed along the force. Length is gravity-anchored: gravity itself is drawn at `gravityArrowFraction × bbox_height`, forces ≤ gravity scale linearly from there, and forces > gravity pass through a soft-saturating curve (asymptoting to `arrowSaturation × gravity_arrow_length`) so an over-powered emitter can't run away past the sublevel.
- By default every applied force gets its own arrow (mirroring Simulated's Contraption Diagram with `mergeForces = false`). Force-group IDs in `clusteredForceGroups` opt into Simulated's `ForceClusterFinder` merge for near-parallel forces.
- Clustered groups are temporally smoothed across snapshots so jittery groups (drag in particular) read steady. Pass-through groups skip smoothing since their application points are already stable.
- Past a sublevel-scaled threshold (the cone/shaft caps key off the tail-bead radius, which itself scales with bbox extent), the arrow *head* (cone tip + shaft thickness) freezes; only the shaft extends further. Caps the visual size so long arrows still look like arrows.

The overlay survives opening and closing the Contraption Diagram on the same sublevel — the dispatcher re-asserts Sable's individual-force-tracking flag every physics tick while a subscription is live.

### HUD readout

While the overlay is targeting a sublevel, a small tooltip-style panel anchored to the top-right corner reports the sublevel's mass and the **net force magnitude** for each active force group:

```
Mass: 12,400 kg
Balloon Lift: 2,610 pN
Gravity: 1,010 pN
Drag: 10 pN
```

Per-group values are vector-summed before the magnitude is taken — so balanced pairs read as ~0 (matching what the arrows tell you), and gravity reads as `mass × g` as expected. Force-group names and colors come from Sable's `ForceGroups` registry. The frame uses the same brown translucent tooltip box Simulated's Contraption Diagram uses for its per-arrow tooltips.

The readout follows the same gating as the arrow overlay — goggles equipped, drafting view on, and a sublevel under the crosshair.

### Commands

Client-side; works in single-player and on servers. Changes persist to `schematician-client.toml`.

| Command | Effect |
| --- | --- |
| `/schematician tooltip toggle` | Show or hide the HUD readout. |
| `/schematician tooltip sigfigs on` / `off` | Round to integer/sig-figs (on) or show 2 decimal places (off). |
| `/schematician tooltip sigfigs <0..12>` | Set the sig-fig count and ensure rounding is on. `0` = integer precision. |
| `/schematician tooltip sigfigs` | Print the current setting. |

### Config

`config/schematician-client.toml` is generated on first launch. All values hot-reload on file save (you may need to rejoin the world for some to take effect).

| key | default | effect |
| --- | --- | --- |
| `targetingChunks` | 4 | Max raycast distance in chunks. The crosshair must land within this radius to inspect a sublevel. |
| `gravityArrowFraction` | 0.3 | Gravity arrow length as a fraction of the sublevel's bbox **height** (Y extent). Anchors per-sublevel scaling so arrows fit the contraption. |
| `arrowSaturation` | 2.0 | Asymptotic cap (× gravity arrow length) for forces above gravity. Soft-saturates via `cap·r/(cap+r−1)` — 2× gravity ≈ 1.33×, 5× ≈ 1.67×, 10× ≈ 1.82×; no runaway. |
| `minArrowLength` | 0.3 | Absolute minimum arrow length in blocks. Stops the shaft from collapsing into the cone+tail bead for tiny drag/residual forces. |
| `clusterAngleRadians` | 0.4 | Forces within this angle (radians) merge into one cluster arrow (for groups in `clusteredForceGroups`). |
| `clusteredForceGroups` | `[]` | Force-group IDs that opt into direction clustering + smoothing. Empty = every force renders its own arrow. |
| `smoothingFactor` | 0.25 | EMA factor across snapshots for clustered groups. Lower = smoother but laggier. |
| `paletteOffset` | 0.25 | Horizontal lookup (0..1) into the palette texture. Each row is a different colorway. |
| `pixelate` | true | Toggle the low-res pixelate pass that snaps the screen to a coarser virtual grid. |
| `pixelScale` | 4.0 | Pixelate intensity: each virtual pixel covers this many screen pixels per axis (1 ≈ off). |
| `lineColor` | "2E3032" | Edge ink color (6-digit hex RGB, optional `#`). |
| `lineShadowColor` | "696965" | Edge shadow color (6-digit hex RGB); doubled lines read as paper-on-paper. |
| `forceTooltipEnabled` | true | Show the HUD readout (mass + per-group net force). Also toggleable via `/schematician tooltip toggle`. |
| `forceTooltipSigFigsEnabled` | true | When false, force/mass values render with two decimal places. When true, rounded to integer or sig-figs per `forceTooltipSigFigs`. |
| `forceTooltipSigFigs` | 0 | Sig-fig count while rounding is on. `0` = integer precision. `N > 0` buckets values whose integer length exceeds `N` to that many leading digits (12,345 → 12,300 at `N=3`). Ignored when rounding is off. |

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


## Known limitations

### Shader-pack compatibility (Iris / Oculus)

With a shader pack loaded (e.g. Iris + BSL), the drafting view's edge detection and palette tone-mapping are washed out by the shader pack's final composite. The drafting view itself still renders, but its blueprint look does not survive — you get the shader pack's lighting/bloom with faint drafting traces underneath. There is no automatic workaround in the mod today; **disable your shader pack while wearing the goggles**.

#### What was tried

- **Soft-dep on the Iris API to force-disable the shader pack while goggles are on.** Reflectively binding to `IrisApi.getInstance().getConfig().setShadersEnabledAndApply(false)` (the v0 toggle lives on `IrisApiConfig`, not on `IrisApi`) and restoring on toggle-off. **Functionally correct** — the pack disabled and the drafting view rendered cleanly. **Abandoned** because `setShadersEnabledAndApply` triggers a full Iris pipeline rebuild (shader recompile + pack reload) every toggle, which takes seconds — unacceptable per-equip UX.
- **Move the post-process to `RenderGuiEvent.Pre`** so it runs after Iris's final composite. **Failed** — produced a solid white/paper screen. Root cause: vanilla `GameRenderer.render` clears `GL_DEPTH_BUFFER_BIT` between `renderLevel()` returning and the GUI hook (1.21.1 source line 1044), and the drafting-view shader uses `step(0.9999999, depth)` for sky detection — cleared depth reads as "all sky" and the palette's paper color fills the screen.
- **Mixin into `GameRenderer.render` to run the post-process before that pre-GUI depth clear.** Injecting at the first `RenderSystem.clear(IZ)V` invocation would have kept depth valid while still landing after Iris's composite. **Failed in practice** (visual result still unusable); we did not pin down whether Iris's composite was actually before our injection point or whether some later pass overwrote our output.

#### What hasn't been tried

- **Snapshot the depth + color attachments at Veil's `AFTER_LEVEL` into our own framebuffer**, then sample that snapshot from a post-Iris hook (or from a mixin call site). Sidesteps the depth-clear issue without trying to time-slice between Iris's passes; costs one extra framebuffer copy per drafting-view frame.
- **Draw the drafting view as a manual fullscreen quad** (no `PostProcessingManager`) at the chosen hook point, fully owning the framebuffer binding and samplers. Decouples us from any assumption Veil's pipeline makes about being dispatched inside its own render path.
- **Ship the drafting view as an Iris shader pack** and swap to it via Iris when goggles equip. Likely a dead end — same pipeline-rebuild delay as the disable approach.
- **Mixin into Iris itself** to conditionally skip its final composite. Most invasive; brittle across Iris versions; not attempted.

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

## Potential TODO
- Consider using an item like the wrench to allow user scaling of vectors when wearing schematician goggles

## Credits

Drafting view shader pipeline derived from work by the Creators-of-Aeronautics team in the [Simulated-Project](https://github.com/Creators-of-Aeronautics/Simulated-Project) repository (`outline_diagram` shader family). The in-world force-overlay data path (force-group tracking, gravity synthesis, clustering) mirrors Simulated's Contraption Diagram.

## License

MIT — see [LICENSE](LICENSE).
