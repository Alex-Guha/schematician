package com.alexguha.aerogoggles;

import com.alexguha.aerogoggles.drafting.DraftingViewHandler;
import foundry.veil.api.client.render.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = AeroGoggles.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AeroGoggles.MODID, value = Dist.CLIENT)
public class AeroGogglesClient {

    public AeroGogglesClient() {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        VeilEventPlatform.INSTANCE.onVeilRenderLevelStage((stage,
                                                          levelRenderer,
                                                          bufferSource,
                                                          frustumMatrix,
                                                          projectionMatrix,
                                                          renderTick,
                                                          deltaTracker,
                                                          camera,
                                                          frustum) -> {
            if (stage == VeilRenderLevelStageEvent.Stage.AFTER_LEVEL) {
                DraftingViewHandler.applyIfWearingGoggles();
            }
        });
    }
}
