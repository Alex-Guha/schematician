package com.alexguha.schematician.drafting;

import com.alexguha.schematician.Schematician;
import com.alexguha.schematician.component.SchematicianDataComponents;
import com.mojang.blaze3d.platform.Window;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class DraftingViewHandler {

    private static final ResourceLocation POST_PIPELINE_ID = ResourceLocation.fromNamespaceAndPath(Schematician.MODID, "drafting_view");

    // Edge color: dark graphite, blueprint ink.
    private static final float LINE_R = 0x2E / 255.0f;
    private static final float LINE_G = 0x30 / 255.0f;
    private static final float LINE_B = 0x32 / 255.0f;

    // Edge shadow: warm grey, sits between ink and paper so doubled lines read as paper-on-paper.
    private static final float LINE_SHADOW_R = 0x69 / 255.0f;
    private static final float LINE_SHADOW_G = 0x69 / 255.0f;
    private static final float LINE_SHADOW_B = 0x65 / 255.0f;

    // Horizontal offset into the palette texture for tonal mapping; 0..1, tweak to recolor.
    private static final float PALETTE_OFFSET = 0.25f;

    // Toggle the low-res pixelate pass.
    private static final boolean PIXELATE = true;

    public static void applyIfWearingGoggles() {
        if (!shouldRenderDraftingView()) {
            return;
        }

        final PostProcessingManager manager = VeilRenderSystem.renderer().getPostProcessingManager();
        final PostPipeline pipeline = manager.getPipeline(POST_PIPELINE_ID);
        if (pipeline == null) {
            return;
        }

        final Window window = Minecraft.getInstance().getWindow();

        pipeline.getUniformSafe("LineColor").setVector(LINE_R, LINE_G, LINE_B, 1.0f);
        pipeline.getUniformSafe("LineShadowColor").setVector(LINE_SHADOW_R, LINE_SHADOW_G, LINE_SHADOW_B, 1.0f);
        pipeline.getUniformSafe("InSize").setVector((float) window.getWidth(), (float) window.getHeight());
        pipeline.getUniformSafe("PaletteOffset").setFloat(PALETTE_OFFSET);
        pipeline.getUniformSafe("Pixelate").setFloat(PIXELATE ? 1.0f : 0.0f);

        manager.runPipeline(pipeline);
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
