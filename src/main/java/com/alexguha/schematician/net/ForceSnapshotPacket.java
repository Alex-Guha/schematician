package com.alexguha.schematician.net;

import com.alexguha.schematician.Schematician;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Server -> Client snapshot of one sublevel's mass + per-force-group point forces.
// All vectors are in the sublevel's LOCAL frame (matches how Sable stores them); the client
// renderer pushes the sublevel's renderPose before drawing so no projection is needed here.
//
// Forces are keyed by ForceGroup registry ResourceLocation instead of the ForceGroup object so
// the client can resolve groups via its own registry view without needing the registry to be
// network-synced (Sable's force_groups registry isn't tagged @Synced).
public record ForceSnapshotPacket(
        UUID subLevelId,
        double mass,
        Map<ResourceLocation, List<QueuedForceGroup.PointForce>> forces
) implements CustomPacketPayload {

    public static final Type<ForceSnapshotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Schematician.MODID, "force_snapshot"));

    private static final StreamCodec<ByteBuf, Vector3dc> VEC3DC = ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list(3))
            .map(l -> new Vector3d(l.getFirst(), l.get(1), l.get(2)),
                 v -> List.of(v.x(), v.y(), v.z()));

    private static final StreamCodec<ByteBuf, QueuedForceGroup.PointForce> POINT_FORCE =
            VEC3DC.apply(ByteBufCodecs.list(2))
                    .map(l -> new QueuedForceGroup.PointForce(l.getFirst(), l.get(1)),
                         p -> List.of(p.point(), p.force()));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForceSnapshotPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, ForceSnapshotPacket::subLevelId,
                    ByteBufCodecs.DOUBLE, ForceSnapshotPacket::mass,
                    ByteBufCodecs.map(
                            Object2ObjectOpenHashMap::new,
                            ResourceLocation.STREAM_CODEC,
                            POINT_FORCE.apply(ByteBufCodecs.list())),
                    ForceSnapshotPacket::forces,
                    ForceSnapshotPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
