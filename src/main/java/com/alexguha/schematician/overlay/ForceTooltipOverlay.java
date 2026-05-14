package com.alexguha.schematician.overlay;

import com.alexguha.schematician.Schematician;
import com.alexguha.schematician.component.SchematicianDataComponents;
import com.alexguha.schematician.config.SchematicianClientConfig;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Top-right HUD readout: sublevel mass + net force magnitude per force group. Drawn with the
// same brown tooltip-box look Simulated's Contraption Diagram uses for its per-arrow tooltips
// (background 0x3D322A, border 0x5D483A) so the values feel like a familiar surface. Box is
// rendered manually with the same outer-frame layout as vanilla `Screen.renderTooltipInternal`
// (3px inner margin, 1px outer border) — keeps positioning fully under our control instead of
// fighting Create's cursor-anchored `RemovedGuiUtils.drawHoveringText` auto-flip logic.
public final class ForceTooltipOverlay {
    private ForceTooltipOverlay() {}

    public static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(Schematician.MODID, "force_tooltip");
    public static final LayeredDraw.Layer LAYER = ForceTooltipOverlay::render;

    private static final int LINE_SPACING = 1;
    private static final int OUTER_MARGIN = 4;                 // gap between border and screen edge
    private static final int INNER_MARGIN = 3;                 // gap between border and text
    private static final int BG_COLOR = 0xE03D322A;            // ~88% alpha translucent brown
    private static final int BORDER_COLOR = 0xFF5D483A;        // solid lighter brown
    private static final int VALUE_COLOR = 0xFFFFFFFF;
    private static final int SEPARATOR_COLOR = 0xFFB0A095;     // pale brown for ":" between label and value

    public static void render(final GuiGraphics graphics, final DeltaTracker deltaTracker) {
        if (!SchematicianClientConfig.FORCE_TOOLTIP_ENABLED.get()) return;

        final Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        final LocalPlayer player = mc.player;
        if (player == null) return;
        if (!isWearingActiveGoggles(player)) return;
        if (ForceOverlayClient.currentTarget() == null) return;

        final ForceOverlayClient.ForceSnapshot snapshot = ForceOverlayClient.currentSnapshot();
        if (snapshot == null) return;

        final boolean sigFigsOn = SchematicianClientConfig.FORCE_TOOLTIP_SIG_FIGS_ENABLED.get();
        final int sigFigs = SchematicianClientConfig.FORCE_TOOLTIP_SIG_FIGS.get();
        final List<Component> lines = buildLines(snapshot, sigFigsOn, sigFigs);
        if (lines.isEmpty()) return;

        drawHud(graphics, mc.font, lines);
    }

    private static List<Component> buildLines(final ForceOverlayClient.ForceSnapshot snapshot,
                                              final boolean sigFigsOn, final int sigFigs) {
        final List<Component> lines = new ArrayList<>();

        lines.add(labeledLine(
                Component.translatable("hud." + Schematician.MODID + ".mass"),
                0xFFCCCCCC,
                Component.translatable("hud." + Schematician.MODID + ".mass_value",
                        formatScalar(snapshot.mass(), sigFigsOn, sigFigs))));

        for (final Map.Entry<ResourceLocation, List<QueuedForceGroup.PointForce>> entry : snapshot.forces().entrySet()) {
            final ForceGroup group = ForceGroups.REGISTRY.get(entry.getKey());
            if (group == null) continue;

            final double netMagnitude = netForceMagnitude(entry.getValue());
            if (netMagnitude <= 0.0) continue;

            lines.add(labeledLine(
                    group.name(),
                    0xFF000000 | group.color(),
                    Component.translatable("hud." + Schematician.MODID + ".force_value",
                            formatScalar(netMagnitude, sigFigsOn, sigFigs))));
        }
        return lines;
    }

    // "<label>: <value>" with the label tinted in the force-group color, the colon in muted
    // beige, and the value in white. Mirrors the contraption-diagram per-arrow tooltip layout
    // (sans the "force of" prefix Simulated prepends — that's redundant in a list of forces).
    private static Component labeledLine(final Component label, final int labelColor, final Component value) {
        final MutableComponent out = Component.empty();
        out.append(label.copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(labelColor & 0x00FFFFFF))));
        out.append(Component.literal(": ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(SEPARATOR_COLOR & 0x00FFFFFF))));
        out.append(value.copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(VALUE_COLOR & 0x00FFFFFF)).withBold(false)));
        return out.withStyle(ChatFormatting.RESET);
    }

    // Net force the group exerts on the contraption: vector-sum the per-emitter forces and take
    // the resulting magnitude. Avoids double-counting balanced pairs (e.g. drag forces cancelling
    // across a symmetric airframe) that a scalar sum-of-magnitudes would inflate.
    private static double netForceMagnitude(final List<QueuedForceGroup.PointForce> forces) {
        double sx = 0.0, sy = 0.0, sz = 0.0;
        for (final QueuedForceGroup.PointForce f : forces) {
            sx += f.force().x();
            sy += f.force().y();
            sz += f.force().z();
        }
        return Math.sqrt(sx * sx + sy * sy + sz * sz);
    }

    private static void drawHud(final GuiGraphics graphics, final Font font, final List<Component> lines) {
        final int lineHeight = font.lineHeight + LINE_SPACING;
        int maxLine = 0;
        for (final Component line : lines) {
            maxLine = Math.max(maxLine, font.width(line));
        }
        final int textWidth = maxLine;
        final int textHeight = lines.size() * lineHeight - LINE_SPACING;

        // Anchor so the outermost pixel column sits OUTER_MARGIN from the screen edge.
        // The outer footprint of the tooltip is (textWidth + INNER_MARGIN*2 + 2) wide.
        final int textX = graphics.guiWidth() - OUTER_MARGIN - INNER_MARGIN - 1 - textWidth;
        final int textY = OUTER_MARGIN + INNER_MARGIN + 1;

        // Inner-background rectangle (3px around the text). The plus-shape extends 1px past
        // each side, leaving the 4 outer corners empty — that's the rounded-corner look.
        final int innerX1 = textX - INNER_MARGIN;
        final int innerY1 = textY - INNER_MARGIN;
        final int innerX2 = textX + textWidth + INNER_MARGIN;
        final int innerY2 = textY + textHeight + INNER_MARGIN;

        // Plus-shape background (5 fills) — matches vanilla `Screen.renderTooltipInternal` and
        // the contraption-diagram tooltip via Create's `RemovedGuiUtils.drawHoveringText`.
        graphics.fill(innerX1, innerY1 - 1, innerX2, innerY1, BG_COLOR);   // top tab (1px above)
        graphics.fill(innerX1, innerY2, innerX2, innerY2 + 1, BG_COLOR);   // bottom tab (1px below)
        graphics.fill(innerX1, innerY1, innerX2, innerY2, BG_COLOR);       // inner fill
        graphics.fill(innerX1 - 1, innerY1, innerX1, innerY2, BG_COLOR);   // left tab (1px outside)
        graphics.fill(innerX2, innerY1, innerX2 + 1, innerY2, BG_COLOR);   // right tab (1px outside)

        // Inner border ring (1px just inside the inner background). Top/bottom span the full
        // inner width; left/right are shortened by 1px at each end so the four ring corners
        // sit at top/bottom-border color rather than crossing. The left/right notches also
        // visually round the inside of the corner.
        graphics.fill(innerX1, innerY1, innerX2, innerY1 + 1, BORDER_COLOR);          // top edge
        graphics.fill(innerX1, innerY2 - 1, innerX2, innerY2, BORDER_COLOR);          // bottom edge
        graphics.fill(innerX1, innerY1 + 1, innerX1 + 1, innerY2 - 1, BORDER_COLOR);  // left edge (notched)
        graphics.fill(innerX2 - 1, innerY1 + 1, innerX2, innerY2 - 1, BORDER_COLOR);  // right edge (notched)

        int y = textY;
        for (final Component line : lines) {
            graphics.drawString(font, line, textX, y, VALUE_COLOR, false);
            y += lineHeight;
        }
    }

    // Mass + force formatter. Three modes:
    //   * sig-figs OFF: 2 decimal places, thousands-separated (e.g. 1,234.56).
    //   * sig-figs ON, count=0 (default): round to nearest integer, thousands-separated.
    //   * sig-figs ON, count=N>0: round to integer, then bucket to the top N leading digits
    //     when the integer length exceeds N (e.g. N=3 → 12345 → 12,300).
    static String formatScalar(final double value, final boolean sigFigsOn, final int sigFigs) {
        if (!Double.isFinite(value)) return "—";

        if (!sigFigsOn) {
            return String.format(Locale.ROOT, "%,.2f", value);
        }

        final long rounded = Math.round(value);
        if (sigFigs <= 0) return String.format(Locale.ROOT, "%,d", rounded);

        final long mag = Math.abs(rounded);
        if (mag == 0) return "0";
        final int digits = (int) Math.floor(Math.log10(mag)) + 1;
        if (digits <= sigFigs) return String.format(Locale.ROOT, "%,d", rounded);

        final long divisor = (long) Math.pow(10, digits - sigFigs);
        final long bucketed = Math.round((double) rounded / (double) divisor) * divisor;
        return String.format(Locale.ROOT, "%,d", bucketed);
    }

    private static boolean isWearingActiveGoggles(final LocalPlayer player) {
        final ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.is(Schematician.SCHEMATICIANS_GOGGLES.asItem())) return false;
        return head.getOrDefault(SchematicianDataComponents.DRAFTING_VIEW_ENABLED.get(), Boolean.TRUE);
    }
}
