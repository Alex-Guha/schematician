package com.alexguha.schematician.drafting;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;

// Secondary render target that the drafting-view shader writes into. Sized to track the main
// framebuffer (lazy-allocated on first use, resized whenever the main RT's dimensions diverge).
// Owned exclusively by DraftingViewHandler.
public final class DraftingFramebuffer {

    final TextureTarget target;

    DraftingFramebuffer(int width, int height) {
        this.target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        this.target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    }

    void resizeIfNeeded(int width, int height) {
        if (this.target.width != width || this.target.height != height) {
            this.target.resize(width, height, Minecraft.ON_OSX);
        }
    }
}
