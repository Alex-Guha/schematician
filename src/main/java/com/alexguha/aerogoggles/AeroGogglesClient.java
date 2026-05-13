package com.alexguha.aerogoggles;

import com.alexguha.aerogoggles.drafting.DraftingKeys;
import com.alexguha.aerogoggles.drafting.DraftingViewHandler;
import foundry.veil.api.client.render.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = AeroGoggles.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AeroGoggles.MODID, value = Dist.CLIENT)
public class AeroGogglesClient {

    public AeroGogglesClient() {
        NeoForge.EVENT_BUS.addListener(AeroGogglesClient::onClientTickPost);
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
                DraftingViewHandler.applyIfActive();
            }
        });
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        DraftingKeys.registerTo(event::register);
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        DraftingViewHandler.tickKeybind();
    }
}
