package com.alexguha.schematician.drafting;

import com.alexguha.schematician.Schematician;
import com.alexguha.schematician.component.SchematicianDataComponents;
import com.alexguha.schematician.config.SchematicianClientConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

// Drafting-view post-process. Two-pass:
//   1. Sample main FB (color + depth) + palette/dither textures via the drafting_view shader,
//      writing into a private secondary framebuffer.
//   2. Copy secondary back to main via the drafting_upscale shader.
//
// Owned entirely by this mod — Veil is no longer involved in the runtime path (Phase 2 of the
// decoupling plan). The framebuffer is lazy-allocated and resize-tracked against the main RT.
public final class DraftingViewHandler {

    private static final ResourceLocation PALETTE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Schematician.MODID, "textures/effects/diagram_palette.png");
    private static final ResourceLocation DITHER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Schematician.MODID, "textures/effects/dither.png");

    private static DraftingFramebuffer framebuffer;

    private DraftingViewHandler() {}

    public static void applyIfWearingGoggles() {
        if (!shouldRenderDraftingView()) {
            return;
        }

        final ShaderInstance viewShader = DraftingShaders.draftingView();
        final ShaderInstance upscaleShader = DraftingShaders.draftingUpscale();
        if (viewShader == null || upscaleShader == null) {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        final RenderTarget main = mc.getMainRenderTarget();
        final Window window = mc.getWindow();

        if (framebuffer == null) {
            framebuffer = new DraftingFramebuffer(main.width, main.height);
        } else {
            framebuffer.resizeIfNeeded(main.width, main.height);
        }

        final float[] line = SchematicianClientConfig.parseHexColor(SchematicianClientConfig.LINE_COLOR.get());
        final float[] lineShadow = SchematicianClientConfig.parseHexColor(SchematicianClientConfig.LINE_SHADOW_COLOR.get());
        final boolean pixelate = SchematicianClientConfig.PIXELATE.get();
        final float paletteOffset = SchematicianClientConfig.PALETTE_OFFSET.get().floatValue();
        final float pixelScale = SchematicianClientConfig.PIXEL_SCALE.get().floatValue();

        final AbstractTexture palette = mc.getTextureManager().getTexture(PALETTE_TEXTURE);
        final AbstractTexture dither = mc.getTextureManager().getTexture(DITHER_TEXTURE);

        // ShaderInstance.apply() drives the bound program + samplers + uniforms; we own
        // blend/depth state explicitly. A fullscreen post-process: write everything, no blend,
        // no depth test, no depth write.
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        // Pass 1: drafting_view → secondary target. Reads main's color + depth textures (still
        // alive even though main isn't bound for write right now).
        framebuffer.target.bindWrite(true);
        viewShader.setSampler("DiffuseSampler0", main.getColorTextureId());
        viewShader.setSampler("DiffuseDepthSampler", main.getDepthTextureId());
        viewShader.setSampler("Palette", palette);
        viewShader.setSampler("Dither", dither);
        viewShader.safeGetUniform("LineColor").set(line[0], line[1], line[2], 1.0f);
        viewShader.safeGetUniform("LineShadowColor").set(lineShadow[0], lineShadow[1], lineShadow[2], 1.0f);
        viewShader.safeGetUniform("InSize").set((float) window.getWidth(), (float) window.getHeight());
        viewShader.safeGetUniform("PaletteOffset").set(paletteOffset);
        viewShader.safeGetUniform("Pixelate").set(pixelate ? 1.0f : 0.0f);
        viewShader.safeGetUniform("PixelScale").set(pixelScale);
        RenderSystem.setShader(() -> viewShader);
        drawFullscreenTriangle();
        viewShader.clear();

        // Pass 2: drafting_upscale → main. Replaces main's color with the secondary's content.
        main.bindWrite(true);
        upscaleShader.setSampler("DiffuseSampler0", framebuffer.target.getColorTextureId());
        RenderSystem.setShader(() -> upscaleShader);
        drawFullscreenTriangle();
        upscaleShader.clear();

        // Restore depth state to match what Veil's PostProcessingManager.clear() left behind:
        // depth test DISABLED, func LEQUAL, mask ON. Crucially we leave depth test off — the
        // force overlay's NO_DEPTH_TEST RenderType uses a no-op setup callback (GL_ALWAYS), so
        // it inherits whatever state is current. Re-enabling depth test here would cause the
        // overlay's cube/arrows to get occluded by blocks in front of them.
        RenderSystem.depthFunc(515);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(true);
    }

    // 3-vertex fullscreen triangle in NDC. The two off-screen corners (3,-1) and (-1,3) extend
    // past the (-1..1) viewport so the triangle covers it entirely after clipping — cheaper than
    // a quad and avoids the diagonal-seam artefact a 2-triangle quad can introduce. The vsh
    // passes Position straight to gl_Position and derives texCoord from it.
    private static void drawFullscreenTriangle() {
        final Tesselator tess = Tesselator.getInstance();
        final BufferBuilder bb = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        bb.addVertex(-1.0f, -1.0f, 0.0f);
        bb.addVertex(3.0f, -1.0f, 0.0f);
        bb.addVertex(-1.0f, 3.0f, 0.0f);
        final MeshData mesh = bb.buildOrThrow();
        BufferUploader.drawWithShader(mesh);
    }

    private static boolean shouldRenderDraftingView() {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        final ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.is(Schematician.SCHEMATICIANS_GOGGLES.asItem())) {
            return false;
        }
        return head.getOrDefault(SchematicianDataComponents.DRAFTING_VIEW_ENABLED.get(), Boolean.TRUE);
    }
}
