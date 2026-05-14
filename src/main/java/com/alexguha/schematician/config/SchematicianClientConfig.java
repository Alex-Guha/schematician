package com.alexguha.schematician.config;

import com.alexguha.schematician.Schematician;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class SchematicianClientConfig {
    private SchematicianClientConfig() {}

    public static final ModConfigSpec.IntValue TARGETING_CHUNKS;
    public static final ModConfigSpec.DoubleValue METERS_PER_NEWTON;
    public static final ModConfigSpec.DoubleValue MIN_ARROW_LENGTH;
    public static final ModConfigSpec.DoubleValue MAX_ARROW_LENGTH;
    public static final ModConfigSpec.DoubleValue CLUSTER_ANGLE_RADIANS;
    public static final ModConfigSpec.DoubleValue SMOOTHING_FACTOR;

    public static final ModConfigSpec SPEC;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("force_overlay");

        TARGETING_CHUNKS = builder
                .comment("Max distance, in chunks, the goggles raycast will search for a sublevel to inspect.")
                .defineInRange("targetingChunks", 4, 1, 32);

        METERS_PER_NEWTON = builder
                .comment("Arrow length scale: rendered length (blocks) per newton of force magnitude.",
                         "Default 0.00002 = 1 block per 50 kN. Tune once real airships are tested.")
                .defineInRange("metersPerNewton", 0.00002, 1.0e-9, 1.0);

        MIN_ARROW_LENGTH = builder
                .comment("Minimum arrow length in blocks; forces below this are floored to keep small contributions visible.")
                .defineInRange("minArrowLength", 0.3, 0.0, 64.0);

        MAX_ARROW_LENGTH = builder
                .comment("Maximum arrow length in blocks; caps massive thrust vectors so they don't stretch to infinity.")
                .defineInRange("maxArrowLength", 64.0, 0.5, 256.0);

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

        SPEC = builder.build();
    }

    public static void register(final ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC, Schematician.MODID + "-client.toml");
    }
}
