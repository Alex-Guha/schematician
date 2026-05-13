package com.alexguha.schematician.net;

import com.alexguha.schematician.Schematician;
import com.alexguha.schematician.net.server.ForceTrackingDispatcher;
import com.alexguha.schematician.overlay.ForceOverlayClient;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Schematician.MODID)
public final class SchematicianNetwork {
    private SchematicianNetwork() {}

    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void onRegister(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(
                ForceSubscribePacket.TYPE,
                ForceSubscribePacket.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        context.enqueueWork(() -> ForceTrackingDispatcher.handleSubscribe(serverPlayer, payload.subLevelId()));
                    }
                });

        registrar.playToClient(
                ForceSnapshotPacket.TYPE,
                ForceSnapshotPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ForceOverlayClient.handleSnapshot(payload)));
    }
}
