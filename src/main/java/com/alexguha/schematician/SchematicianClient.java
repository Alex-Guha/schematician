package com.alexguha.schematician;

import com.alexguha.schematician.drafting.DraftingViewHandler;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = Schematician.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Schematician.MODID, value = Dist.CLIENT)
public class SchematicianClient {

    public SchematicianClient() {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        VeilEventPlatform.INSTANCE.onVeilRenderLevelStage(
                (stage, levelRenderer, bufferSource, matrixStack, frustumMatrix, projectionMatrix,
                 renderTick, deltaTracker, camera, frustum) -> {
                    if (stage == VeilRenderLevelStageEvent.Stage.AFTER_LEVEL) {
                        DraftingViewHandler.applyIfWearingGoggles();
                    }
                });
    }
}
