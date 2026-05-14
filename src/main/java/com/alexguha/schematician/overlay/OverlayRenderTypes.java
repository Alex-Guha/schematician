package com.alexguha.schematician.overlay;

import com.alexguha.schematician.Schematician;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

// Custom RenderTypes for the force overlay. We need lines that:
//   1. Render through walls (no depth test) so vectors aren't occluded by sublevel blocks.
//   2. Render onto the main framebuffer AFTER the drafting-view post-process, so the overlay
//      reads as a HUD-like layer rather than being palette-shifted by the shader.
// (Point #2 is achieved by the caller scheduling the render at AFTER_LEVEL; this class just
// owns the no-depth state.)
public final class OverlayRenderTypes extends RenderType {
    private OverlayRenderTypes() {
        super("", null, null, 0, false, false, null, null);
    }

    private static final RenderType FORCE_LINES = create(
            Schematician.MODID + ":force_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.of(3.0)))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    // Used for the CoM marker — small filled cube. Same no-depth behavior so the dot stays
    // visible through occluding blocks.
    private static final RenderType OVERLAY_FILL = create(
            Schematician.MODID + ":overlay_fill",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1024,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    public static RenderType forceLines() {
        return FORCE_LINES;
    }

    public static RenderType overlayFill() {
        return OVERLAY_FILL;
    }
}
