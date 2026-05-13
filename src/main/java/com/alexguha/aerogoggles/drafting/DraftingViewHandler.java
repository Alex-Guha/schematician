package com.alexguha.aerogoggles.drafting;

import com.alexguha.aerogoggles.AeroGoggles;
import com.mojang.blaze3d.platform.Window;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

public class DraftingViewHandler {

    private static final ResourceLocation POST_PIPELINE_ID = ResourceLocation.fromNamespaceAndPath(AeroGoggles.MODID, "drafting_view");
    private static final boolean PIXELATE = true;

    public static void applyIfWearingGoggles() {
        if (!isWearingAeroGoggles()) {
            return;
        }

        final PostProcessingManager manager = VeilRenderSystem.renderer().getPostProcessingManager();
        final PostPipeline pipeline = manager.getPipeline(POST_PIPELINE_ID);
        if (pipeline == null) {
            return;
        }

        final Window window = Minecraft.getInstance().getWindow();

        pipeline.getUniformSafe("LineColor").setVector(0x2E / 255.0f, 0x30 / 255.0f, 0x32 / 255.0f, 1.0f);
        pipeline.getUniformSafe("LineShadowColor").setVector(0x69 / 255.0f, 0x69 / 255.0f, 0x65 / 255.0f, 1.0f);
        pipeline.getUniformSafe("InSize").setVector((float) window.getWidth(), (float) window.getHeight());
        pipeline.getUniformSafe("PaletteOffset").setFloat(0.25f);
        pipeline.getUniformSafe("Pixelate").setFloat(PIXELATE ? 1.0f : 0.0f);

        manager.runPipeline(pipeline);
    }

    private static boolean isWearingAeroGoggles() {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        return player.getItemBySlot(EquipmentSlot.HEAD).is(AeroGoggles.AERO_GOGGLES.asItem());
    }
}
