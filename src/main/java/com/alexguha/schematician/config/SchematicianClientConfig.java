package com.alexguha.schematician.config;

import com.alexguha.schematician.Schematician;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.regex.Pattern;

public final class SchematicianClientConfig {
    private SchematicianClientConfig() {}

    public static final ModConfigSpec.IntValue TARGETING_CHUNKS;
    public static final ModConfigSpec.DoubleValue METERS_PER_NEWTON;
    public static final ModConfigSpec.DoubleValue MIN_ARROW_LENGTH;
    public static final ModConfigSpec.DoubleValue MAX_ARROW_LENGTH;
    public static final ModConfigSpec.DoubleValue CLUSTER_ANGLE_RADIANS;
    public static final ModConfigSpec.DoubleValue SMOOTHING_FACTOR;

    public static final ModConfigSpec.DoubleValue PALETTE_OFFSET;
    public static final ModConfigSpec.BooleanValue PIXELATE;
    public static final ModConfigSpec.DoubleValue PIXEL_SCALE;
    public static final ModConfigSpec.ConfigValue<String> LINE_COLOR;
    public static final ModConfigSpec.ConfigValue<String> LINE_SHADOW_COLOR;

    public static final ModConfigSpec SPEC;

    private static final Pattern HEX_RGB = Pattern.compile("^#?[0-9a-fA-F]{6}$");

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("force_overlay");

        TARGETING_CHUNKS = builder
                .comment("Max distance, in chunks, the goggles raycast will search for a sublevel to inspect.")
                .defineInRange("targetingChunks", 4, 1, 32);

        METERS_PER_NEWTON = builder
                .comment("Arrow length scale: rendered length (blocks) per newton of force magnitude.",
                         "Default 0.0002 = 1 block per 5 kN, calibrated so a chunk-sized sublevel's",
                         "gravity vector reads at ~5 blocks (matching the Contraption Diagram).")
                .defineInRange("metersPerNewton", 0.0002, 1.0e-9, 1.0);

        MIN_ARROW_LENGTH = builder
                .comment("Minimum arrow length in blocks; forces below this are floored to keep small contributions visible.")
                .defineInRange("minArrowLength", 0.3, 0.0, 64.0);

        MAX_ARROW_LENGTH = builder
                .comment("Maximum arrow length in blocks; caps massive thrust vectors so they don't stretch to infinity.")
                .defineInRange("maxArrowLength", 8.0, 0.5, 256.0);

        CLUSTER_ANGLE_RADIANS = builder
                .comment("Forces within this angular threshold (radians) are merged into one cluster arrow.",
                         "Mirrors Simulated's Contraption Diagram default (~0.4 rad).")
                .defineInRange("clusterAngleRadians", 0.4, 0.0, Math.PI);

        SMOOTHING_FACTOR = builder
                .comment("Exponential-moving-average factor applied to clustered arrows across snapshots.",
                         "1.0 = no smoothing (snap to each tick); lower = more smoothing.",
                         "0.25 ≈ 70% catch-up over 5 ticks; drag in particular jitters less.")
                .defineInRange("smoothingFactor", 0.25, 0.01, 1.0);

        builder.pop();

        builder.push("drafting_view");

        PALETTE_OFFSET = builder
                .comment("Horizontal lookup offset (0..1) into the palette texture used for tonal mapping.",
                         "Each row in the palette PNG is a different colorway; tweak to recolor the view.")
                .defineInRange("paletteOffset", 0.25, 0.0, 1.0);

        PIXELATE = builder
                .comment("Toggle the low-res pixelate pass that snaps the screen to a coarser virtual grid.")
                .define("pixelate", true);

        PIXEL_SCALE = builder
                .comment("Pixelate intensity: each virtual pixel covers this many screen pixels per axis.",
                         "1.0 ≈ off, 4.0 = default, higher = chunkier blueprint look.")
                .defineInRange("pixelScale", 4.0, 1.0, 16.0);

        LINE_COLOR = builder
                .comment("Edge ink color as a 6-digit hex RGB (with or without leading '#').",
                         "Default 2E3032 is a dark graphite for blueprint outlines.")
                .define("lineColor", "2E3032", o -> o instanceof String s && HEX_RGB.matcher(s).matches());

        LINE_SHADOW_COLOR = builder
                .comment("Edge shadow color as a 6-digit hex RGB; sits between ink and paper so doubled lines",
                         "read as paper-on-paper. Default 696965 is a warm grey.")
                .define("lineShadowColor", "696965", o -> o instanceof String s && HEX_RGB.matcher(s).matches());

        builder.pop();

        SPEC = builder.build();
    }

    public static float[] parseHexColor(final String hex) {
        final String body = hex.startsWith("#") ? hex.substring(1) : hex;
        final int rgb = Integer.parseInt(body, 16);
        return new float[] {
                ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f,
                (rgb & 0xFF) / 255.0f,
        };
    }

    public static void register(final ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC, Schematician.MODID + "-client.toml");
    }
}
