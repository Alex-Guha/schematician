# Schematician

A drafting-view post-process for Create: Aeronautics, gated behind a wearable item.

While **Schematician's Goggles** are equipped, the world is overlaid with a Create: Aeronautics drafting / blueprint pipeline (edge detection + palette tone-mapping + dither). Lifts the standalone "drafting view" feature out of an Aeronautics fork and packages it as its own mod with a worn-item trigger instead of a hotkey.

## Status

Alpha. Single item, single pipeline, fixed palette. Tier variants and per-player palette tuning are on the roadmap.

## Requirements

All required — Schematician is built around the Create / Aeronautics / Simulated stack and integrates with each directly.

- Minecraft 1.21.1
- NeoForge 21.1.226+
- [Veil](https://modrinth.com/mod/veil) (FoundryMC) — drives the post-process pipeline
- [Create](https://www.curseforge.com/minecraft/mc-mods/create) — provides the goggles overlay system (stress, fluid, rotation tooltips); also required transitively via Aeronautics
- [Create: Aeronautics](https://github.com/Creators-of-Aeronautics/Simulated-Project) — the drafting-view shader is derived from Aeronautics' `outline_diagram` pipeline and the goggles recipe consumes Aviator's Goggles
- [Simulated](https://github.com/Creators-of-Aeronautics/Simulated-Project) — supplies the Physics Assembler used in the recipe; planned future integrations

## Crafting

`Aviator's Goggles` (Create: Aeronautics) + `Contraption Diagram` (Simulated) → `Schematician's Goggles` (shapeless).

## Roadmap

- [ ] **Unique item icon** — `src/main/resources/assets/schematician/textures/item/schematicians_goggles.png` (16×16). Currently a placeholder derived from Aviator's Goggles.
- [ ] **Unique armor model layers** — `assets/schematician/textures/models/armor/schematicians_goggles_layer_{1,2}.png` (64×32). Currently placeholders.
- [ ] Reuse the icon as `src/main/resources/logo.png` (256×256) for the CurseForge / Modrinth listing.
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

Drafting view shader pipeline derived from work by the Creators-of-Aeronautics team in the [Simulated-Project](https://github.com/Creators-of-Aeronautics/Simulated-Project) repository (`outline_diagram` shader family).

## License

MIT — see [LICENSE](LICENSE).
