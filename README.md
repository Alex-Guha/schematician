# AeroGoggles

While the player is wearing a pair of AeroGoggles, the world view is overlaid with a Create: Aeronautics drafting / blueprint post-process pipeline (edge detection + palette + dither shading). Lifts the standalone "drafting view" feature out of an Aeronautics fork and packages it as its own mod with a worn-item trigger instead of a hotkey.

## Status

Pre-alpha. The post-process pipeline and goggles item exist; everything else (recipe, tier variants, palette tuning) is open.

## Toolchain

- Java 21
- NeoForge 21.1.226
- ModDevGradle 2.0.141
- Parchment mappings on top of Mojmap

## Dependencies

- [Veil](https://github.com/FoundryMC/Veil) - for the post-processing pipeline format and `PostProcessingManager`.
- [Create: Aeronautics / Simulated](https://github.com/Creators-of-Aeronautics/Simulated-Project) - source of the original `outline_diagram` shader family this mod's `drafting_view` pipeline derives from.

## Build

```bash
./gradlew build
```

Output jar lands in `build/libs/`.

## License

MIT — see [LICENSE](LICENSE).
