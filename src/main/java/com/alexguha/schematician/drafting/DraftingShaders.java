package com.alexguha.schematician.drafting;

import com.alexguha.schematician.Schematician;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

// Holds the post-process ShaderInstance references for the drafting-view pipeline. Loaded via
// the mod-bus RegisterShadersEvent — the listener is registered manually from SchematicianClient
// because @EventBusSubscriber.Bus is deprecated on 21.1.
//
// Shader assets live at assets/schematician/shaders/core/{drafting_view,drafting_upscale}.
public final class DraftingShaders {

    private DraftingShaders() {}

    @Nullable private static ShaderInstance draftingView;
    @Nullable private static ShaderInstance draftingUpscale;

    @Nullable
    public static ShaderInstance draftingView() {
        return draftingView;
    }

    @Nullable
    public static ShaderInstance draftingUpscale() {
        return draftingUpscale;
    }

    public static void onRegisterShaders(final RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(Schematician.MODID, "drafting_view"),
                            DefaultVertexFormat.POSITION),
                    loaded -> draftingView = loaded);
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(Schematician.MODID, "drafting_upscale"),
                            DefaultVertexFormat.POSITION),
                    loaded -> draftingUpscale = loaded);
        } catch (IOException e) {
            throw new RuntimeException("Failed to register Schematician drafting-view shaders", e);
        }
    }
}
