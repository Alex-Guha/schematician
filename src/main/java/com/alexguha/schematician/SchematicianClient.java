package com.alexguha.schematician;

import com.alexguha.schematician.drafting.DraftingViewHandler;
import com.alexguha.schematician.overlay.ForceOverlayRenderer;
import com.alexguha.schematician.overlay.ForceTooltipOverlay;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@Mod(value = Schematician.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Schematician.MODID, value = Dist.CLIENT)
public class SchematicianClient {

    public SchematicianClient(final IEventBus modEventBus) {
        modEventBus.addListener(SchematicianClient::onRegisterGuiLayers);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        VeilEventPlatform.INSTANCE.onVeilRenderLevelStage(
                (stage, levelRenderer, bufferSource, matrixStack, frustumMatrix, projectionMatrix,
                 renderTick, deltaTracker, camera, frustum) -> {
                    if (stage == VeilRenderLevelStageEvent.Stage.AFTER_LEVEL) {
                        DraftingViewHandler.applyIfWearingGoggles();
                    }
                    ForceOverlayRenderer.onRenderStage(stage, bufferSource, camera, frustumMatrix);
                });
    }

    // Mod-bus event — registered manually from the constructor because @EventBusSubscriber.Bus
    // is deprecated in 21.1; the IEventBus injection is the supported path.
    // Layered above HOTBAR so the readout sits with other always-on HUD pieces (XP, food) and
    // not under chat or the pause fade.
    private static void onRegisterGuiLayers(final RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ForceTooltipOverlay.LAYER_ID, ForceTooltipOverlay.LAYER);
    }
}
