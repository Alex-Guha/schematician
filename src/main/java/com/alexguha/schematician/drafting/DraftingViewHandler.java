package com.alexguha.schematician.drafting;

import com.alexguha.schematician.Schematician;
import com.alexguha.schematician.component.SchematicianDataComponents;
import com.alexguha.schematician.config.SchematicianClientConfig;
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
        final float[] line = SchematicianClientConfig.parseHexColor(SchematicianClientConfig.LINE_COLOR.get());
        final float[] lineShadow = SchematicianClientConfig.parseHexColor(SchematicianClientConfig.LINE_SHADOW_COLOR.get());
        final boolean pixelate = SchematicianClientConfig.PIXELATE.get();

        pipeline.getUniformSafe("LineColor").setVector(line[0], line[1], line[2], 1.0f);
        pipeline.getUniformSafe("LineShadowColor").setVector(lineShadow[0], lineShadow[1], lineShadow[2], 1.0f);
        pipeline.getUniformSafe("InSize").setVector((float) window.getWidth(), (float) window.getHeight());
        pipeline.getUniformSafe("PaletteOffset").setFloat(SchematicianClientConfig.PALETTE_OFFSET.get().floatValue());
        pipeline.getUniformSafe("Pixelate").setFloat(pixelate ? 1.0f : 0.0f);
        pipeline.getUniformSafe("PixelScale").setFloat(SchematicianClientConfig.PIXEL_SCALE.get().floatValue());

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
