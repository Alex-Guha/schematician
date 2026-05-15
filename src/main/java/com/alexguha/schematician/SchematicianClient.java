package com.alexguha.schematician;

import com.alexguha.schematician.drafting.DraftingViewHandler;
import com.alexguha.schematician.overlay.ForceOverlayRenderer;
import com.alexguha.schematician.overlay.ForceTooltipOverlay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@Mod(value = Schematician.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Schematician.MODID, value = Dist.CLIENT)
public class SchematicianClient {

    public SchematicianClient(final IEventBus modEventBus) {
        modEventBus.addListener(SchematicianClient::onRegisterGuiLayers);
    }

    // NeoForge's AFTER_LEVEL is the 1:1 analogue of Veil's AFTER_LEVEL (per Veil's STAGE_MAPPING
    // in NeoForgeVeilEventPlatform). It fires near the end of LevelRenderer.renderLevel(), after
    // all transparency targets have been composited back to main — main FB is bound and the
    // scene is fully assembled. Earlier stages fire within the fabulous-graphics chain, so a
    // transparency target can still be the bound framebuffer at that point.
    //
    // Order matches the original Veil-driven flow: post-process runs first, then the overlay is
    // drawn on top of the post-processed image (overlay reads as a HUD-like layer, not palette-
    // shifted by the drafting-view shader).
    @SubscribeEvent
    static void onRenderLevelStage(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        DraftingViewHandler.applyIfWearingGoggles();
        ForceOverlayRenderer.onRenderStage(event);
    }

    // Mod-bus event — registered manually from the constructor because @EventBusSubscriber.Bus
    // is deprecated in 21.1; the IEventBus injection is the supported path.
    // Layered above HOTBAR so the readout sits with other always-on HUD pieces (XP, food) and
    // not under chat or the pause fade.
    private static void onRegisterGuiLayers(final RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ForceTooltipOverlay.LAYER_ID, ForceTooltipOverlay.LAYER);
    }
}
