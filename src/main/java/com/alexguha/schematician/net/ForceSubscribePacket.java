package com.alexguha.schematician.net;

import com.alexguha.schematician.Schematician;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

// Client -> Server heartbeat. While drafting view is on, the client sends this every few ticks
// with the UUID of the sublevel it's currently targeting. The dispatcher auto-expires any
// subscription that hasn't been heartbeated, so there's no explicit unsubscribe payload —
// just stop sending.
public record ForceSubscribePacket(UUID subLevelId) implements CustomPacketPayload {

    public static final Type<ForceSubscribePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Schematician.MODID, "force_subscribe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForceSubscribePacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, ForceSubscribePacket::subLevelId,
                    ForceSubscribePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
