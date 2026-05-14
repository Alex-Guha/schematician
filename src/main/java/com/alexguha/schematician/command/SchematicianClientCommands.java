package com.alexguha.schematician.command;

import com.alexguha.schematician.Schematician;
import com.alexguha.schematician.config.SchematicianClientConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

// Client-only commands for the in-world tooltip overlay. Mutate the per-client config (saved
// through to schematician-client.toml) so users can adjust the overlay without alt-tabbing.
//
//   /schematician tooltip toggle           — show/hide the HUD readout
//   /schematician tooltip sigfigs on|off   — switch between rounded and 2-decimal display
//   /schematician tooltip sigfigs <0..12>  — set the sig-fig count (and ensure rounding is on)
//   /schematician tooltip sigfigs          — print the current setting
@EventBusSubscriber(modid = Schematician.MODID, value = Dist.CLIENT)
public final class SchematicianClientCommands {
    private SchematicianClientCommands() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(final RegisterClientCommandsEvent event) {
        final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(Schematician.MODID);

        root.then(Commands.literal("tooltip")
                .then(Commands.literal("toggle").executes(ctx -> toggleEnabled(ctx.getSource())))
                .then(Commands.literal("sigfigs")
                        .executes(ctx -> reportSigFigs(ctx.getSource()))
                        .then(Commands.literal("on").executes(ctx -> setSigFigsEnabled(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setSigFigsEnabled(ctx.getSource(), false)))
                        .then(Commands.argument("n", IntegerArgumentType.integer(0, 12))
                                .executes(ctx -> setSigFigs(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "n"))))));

        event.getDispatcher().register(root);
    }

    private static int toggleEnabled(final CommandSourceStack source) {
        final boolean next = !SchematicianClientConfig.FORCE_TOOLTIP_ENABLED.get();
        SchematicianClientConfig.FORCE_TOOLTIP_ENABLED.set(next);
        source.sendSuccess(() -> Component.translatable(
                "commands." + Schematician.MODID + ".tooltip." + (next ? "enabled" : "disabled")), false);
        return next ? 1 : 0;
    }

    private static int setSigFigsEnabled(final CommandSourceStack source, final boolean value) {
        SchematicianClientConfig.FORCE_TOOLTIP_SIG_FIGS_ENABLED.set(value);
        source.sendSuccess(() -> Component.translatable(
                "commands." + Schematician.MODID + ".tooltip.sigfigs_" + (value ? "on" : "off")), false);
        return value ? 1 : 0;
    }

    // Setting an explicit count implies the user wants rounding active — flip the enable bit on
    // alongside the value so `/schematician tooltip sigfigs 3` doesn't silently do nothing while
    // rounding is off.
    private static int setSigFigs(final CommandSourceStack source, final int value) {
        SchematicianClientConfig.FORCE_TOOLTIP_SIG_FIGS_ENABLED.set(true);
        SchematicianClientConfig.FORCE_TOOLTIP_SIG_FIGS.set(value);
        source.sendSuccess(() -> Component.translatable(
                "commands." + Schematician.MODID + ".tooltip.sigfigs_set", value), false);
        return value;
    }

    private static int reportSigFigs(final CommandSourceStack source) {
        final boolean on = SchematicianClientConfig.FORCE_TOOLTIP_SIG_FIGS_ENABLED.get();
        final int value = SchematicianClientConfig.FORCE_TOOLTIP_SIG_FIGS.get();
        source.sendSuccess(() -> on
                ? Component.translatable("commands." + Schematician.MODID + ".tooltip.sigfigs_current_on", value)
                : Component.translatable("commands." + Schematician.MODID + ".tooltip.sigfigs_current_off"), false);
        return on ? value : 0;
    }
}
