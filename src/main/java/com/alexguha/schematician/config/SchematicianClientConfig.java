package com.alexguha.schematician.config;

import com.alexguha.schematician.Schematician;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.regex.Pattern;

public final class SchematicianClientConfig {
    private SchematicianClientConfig() {}

    public static final ModConfigSpec.IntValue TARGETING_CHUNKS;
    public static final ModConfigSpec.DoubleValue GRAVITY_ARROW_FRACTION;
    public static final ModConfigSpec.DoubleValue ARROW_SATURATION;
    public static final ModConfigSpec.DoubleValue MIN_ARROW_LENGTH;
    public static final ModConfigSpec.DoubleValue CLUSTER_ANGLE_RADIANS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CLUSTERED_FORCE_GROUPS;
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

        GRAVITY_ARROW_FRACTION = builder
                .comment("Length of the gravity arrow as a fraction of the sublevel's bbox height (Y extent).",
                         "Forces ≤ gravity scale linearly from there: length = magnitude * (gravityLength /",
                         "gravityMagnitude). Forces > gravity soft-saturate (see arrowSaturation). Anchoring",
                         "to gravity + bbox-height keeps a stable visual ruler per sublevel — small",
                         "contraptions get small arrows, large ones get large arrows — without snap-to-max.")
                .defineInRange("gravityArrowFraction", 0.3, 0.05, 4.0);

        ARROW_SATURATION = builder
                .comment("Soft cap (in units of the gravity arrow length) that arrows above gravity",
                         "asymptote toward. Formula: visualRatio = cap * r / (cap + r - 1) for r > 1, where",
                         "r = magnitude / gravityMagnitude. Order is preserved (bigger forces still draw",
                         "longer arrows) but runaway is killed. With the default cap = 2: r=2 → ~1.33x,",
                         "r=5 → ~1.67x, r=10 → ~1.82x; asymptotes to 2x regardless of how huge r gets.")
                .defineInRange("arrowSaturation", 2.0, 1.1, 100.0);

        MIN_ARROW_LENGTH = builder
                .comment("Absolute minimum arrow length in blocks so the shaft stays visible past the cone",
                         "tip + tail bead. Tiny forces (drag, residuals) won't collapse to a sphere-with-cone.")
                .defineInRange("minArrowLength", 0.3, 0.0, 64.0);

        CLUSTER_ANGLE_RADIANS = builder
                .comment("Forces within this angular threshold (radians) are merged into one cluster arrow",
                         "for groups listed in clusteredForceGroups. Mirrors Simulated's Contraption Diagram",
                         "default (~0.4 rad).")
                .defineInRange("clusterAngleRadians", 0.4, 0.0, Math.PI);

        CLUSTERED_FORCE_GROUPS = builder
                .comment("Force-group IDs whose forces are merged into direction clusters. Groups not listed",
                         "here render one arrow per applied force, mirroring the Contraption Diagram's default",
                         "(mergeForces = false). Add e.g. \"sable:drag\" to dampen jittery groups.")
                .defineListAllowEmpty("clusteredForceGroups",
                        List.of(),
                        () -> "sable:drag",
                        o -> o instanceof String s && ResourceLocation.tryParse(s) != null);

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
