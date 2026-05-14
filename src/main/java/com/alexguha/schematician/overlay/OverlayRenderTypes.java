package com.alexguha.schematician.overlay;

import com.alexguha.schematician.Schematician;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

// Custom RenderTypes for the force overlay. Both are no-depth so the overlay stays visible
// through occluding sublevel blocks. They render onto the main framebuffer AFTER the
// drafting-view post-process (caller schedules the draw at AFTER_LEVEL), so the overlay reads
// as a HUD-like layer rather than being palette-shifted by the shader.
public final class OverlayRenderTypes extends RenderType {
    private OverlayRenderTypes() {
        super("", null, null, 0, false, false, null, null);
    }

    // Used for the CoM marker — small filled cube.
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

    // Used for the force arrows — shaft cylinder + cone tip + tail bead all share this type so
    // the entire arrow is one batched draw.
    private static final RenderType OVERLAY_TRIANGLES = create(
            Schematician.MODID + ":overlay_triangles",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
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

    public static RenderType overlayFill() {
        return OVERLAY_FILL;
    }

    public static RenderType overlayTriangles() {
        return OVERLAY_TRIANGLES;
    }
}
