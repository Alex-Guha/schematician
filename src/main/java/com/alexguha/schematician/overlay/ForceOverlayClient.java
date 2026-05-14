package com.alexguha.schematician.overlay;

import com.alexguha.schematician.Schematician;
import com.alexguha.schematician.component.SchematicianDataComponents;
import com.alexguha.schematician.config.SchematicianClientConfig;
import com.alexguha.schematician.net.ForceSnapshotPacket;
import com.alexguha.schematician.net.ForceSubscribePacket;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.joml.Vector3d;

// Client-side state for the force overlay:
//   1. Each client tick, raycast from the camera within `targetingChunks * 16` blocks.
//   2. If the hit is inside a sublevel, that's the current target. Otherwise no target.
//   3. Heartbeat ForceSubscribePacket every HEARTBEAT_INTERVAL_TICKS to keep server tracking alive.
//   4. ForceSnapshotPackets are stored as the current snapshot (one at a time — only one target).
//
// The renderer reads `currentTarget()` + `currentSnapshot()` each frame. CoM comes from the
// sublevel pose directly so it doesn't need a snapshot; the snapshot only carries forces.
@EventBusSubscriber(modid = Schematician.MODID, value = Dist.CLIENT)
public final class ForceOverlayClient {
    private ForceOverlayClient() {}

    private static final long HEARTBEAT_INTERVAL_TICKS = 10L;
    private static final long SNAPSHOT_TTL_TICKS = 30L;

    @Nullable private static UUID targetSubLevelId;
    @Nullable private static ForceSnapshot snapshot;
    // Sentinel that means "never sent" without underflowing `localTick - lastHeartbeatTick`.
    // Long.MIN_VALUE would overflow the subtraction and make the cadence check always false —
    // that bug is what hid all the force packets in 0.3.3.
    private static long lastHeartbeatTick = -HEARTBEAT_INTERVAL_TICKS;
    private static long localTick;

    // Smoothed clusters per ForceGroup, persisted across snapshots. Each new snapshot's raw
    // forces are clustered, then matched to the previous smoothed clusters by direction (greedy,
    // best cosine) and EMA-blended. Unmatched new clusters snap in fresh; unmatched old clusters
    // are dropped. The renderer reads these directly so clustering only happens once per tick.
    private static Map<ResourceLocation, List<ForceClusterer.Cluster>> smoothedClusters = new HashMap<>();

    public record ForceSnapshot(
            UUID subLevelId,
            double mass,
            Map<ResourceLocation, List<QueuedForceGroup.PointForce>> forces,
            long receivedAtTick
    ) {}

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        localTick++;

        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        final ClientLevel level = mc.level;
        if (player == null || level == null) {
            clear();
            return;
        }

        if (!isWearingActiveGoggles(player)) {
            clear();
            return;
        }

        final UUID newTarget = raycastTargetSubLevel(player);

        if (newTarget == null) {
            // Lost target — drop cached snapshot so stale arrows don't linger. Server tracking
            // auto-expires after its own timeout once heartbeats stop.
            targetSubLevelId = null;
            snapshot = null;
            smoothedClusters = new HashMap<>();
            return;
        }

        if (!newTarget.equals(targetSubLevelId)) {
            targetSubLevelId = newTarget;
            snapshot = null;
            smoothedClusters = new HashMap<>();
            lastHeartbeatTick = localTick - HEARTBEAT_INTERVAL_TICKS;
        }

        if (localTick - lastHeartbeatTick >= HEARTBEAT_INTERVAL_TICKS) {
            PacketDistributor.sendToServer(new ForceSubscribePacket(newTarget));
            lastHeartbeatTick = localTick;
        }

        if (snapshot != null && localTick - snapshot.receivedAtTick > SNAPSHOT_TTL_TICKS) {
            snapshot = null;
            smoothedClusters = new HashMap<>();
        }
    }

    public static void handleSnapshot(final ForceSnapshotPacket packet) {
        if (targetSubLevelId == null || !targetSubLevelId.equals(packet.subLevelId())) {
            return;
        }
        snapshot = new ForceSnapshot(packet.subLevelId(), packet.mass(), packet.forces(), localTick);
        recomputeSmoothedClusters(packet);
    }

    private static void recomputeSmoothedClusters(final ForceSnapshotPacket packet) {
        final double angleThreshold = SchematicianClientConfig.CLUSTER_ANGLE_RADIANS.get();
        final double alpha = SchematicianClientConfig.SMOOTHING_FACTOR.get();
        final Set<ResourceLocation> clustered = new HashSet<>();
        for (final String id : SchematicianClientConfig.CLUSTERED_FORCE_GROUPS.get()) {
            final ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed != null) clustered.add(parsed);
        }

        final Map<ResourceLocation, List<ForceClusterer.Cluster>> next = new HashMap<>();
        for (final Map.Entry<ResourceLocation, List<QueuedForceGroup.PointForce>> e : packet.forces().entrySet()) {
            final boolean shouldCluster = clustered.contains(e.getKey());
            final List<ForceClusterer.Cluster> rawClusters = shouldCluster
                    ? ForceClusterer.cluster(e.getValue(), angleThreshold)
                    : asIndividualClusters(e.getValue());
            if (rawClusters.isEmpty()) continue;

            // Skip EMA on unclustered groups: direction-based matching is unreliable when all
            // arrows in the group point the same way (multiple balloons or wings), and the
            // application points are already stable since they come from fixed emitter blocks.
            if (!shouldCluster) {
                next.put(e.getKey(), rawClusters);
                continue;
            }
            final List<ForceClusterer.Cluster> prev = smoothedClusters.getOrDefault(e.getKey(), List.of());
            next.put(e.getKey(), blendClusters(rawClusters, prev, alpha));
        }
        smoothedClusters = next;
    }

    // Default render path: each PointForce becomes its own one-element Cluster, preserving the
    // application point and exact force vector. Mirrors Simulated's Contraption Diagram, which
    // defaults to mergeForces=false and uses ForceClusterFinder.passThrough.
    private static List<ForceClusterer.Cluster> asIndividualClusters(final List<QueuedForceGroup.PointForce> forces) {
        final List<ForceClusterer.Cluster> out = new ArrayList<>(forces.size());
        for (final QueuedForceGroup.PointForce f : forces) {
            out.add(new ForceClusterer.Cluster(new Vector3d(f.point()), new Vector3d(f.force()), 1));
        }
        return out;
    }

    // Greedy direction matching: for each new cluster, pair with the unused previous cluster of
    // closest direction (cosine similarity above 0.5 — anti-parallel pairs aren't blended). EMA
    // matched ones; snap unmatched new ones in fresh. Unmatched previous clusters are dropped
    // (a force disappeared or merged elsewhere).
    private static List<ForceClusterer.Cluster> blendClusters(
            final List<ForceClusterer.Cluster> raw,
            final List<ForceClusterer.Cluster> prev,
            final double alpha) {
        final boolean[] used = new boolean[prev.size()];
        final List<ForceClusterer.Cluster> out = new ArrayList<>(raw.size());

        for (final ForceClusterer.Cluster n : raw) {
            int bestIdx = -1;
            double bestScore = 0.5;
            for (int i = 0; i < prev.size(); i++) {
                if (used[i]) continue;
                final ForceClusterer.Cluster p = prev.get(i);
                final double na2 = n.force().lengthSquared();
                final double pa2 = p.force().lengthSquared();
                if (na2 <= 0.0 || pa2 <= 0.0) continue;
                final double cos = n.force().dot(p.force()) / Math.sqrt(na2 * pa2);
                if (cos > bestScore) {
                    bestScore = cos;
                    bestIdx = i;
                }
            }

            if (bestIdx >= 0) {
                used[bestIdx] = true;
                final ForceClusterer.Cluster p = prev.get(bestIdx);
                final Vector3d pos = new Vector3d(p.pos()).lerp(n.pos(), alpha);
                final Vector3d force = new Vector3d(p.force()).lerp(n.force(), alpha);
                out.add(new ForceClusterer.Cluster(pos, force, n.groupSize()));
            } else {
                out.add(new ForceClusterer.Cluster(new Vector3d(n.pos()), new Vector3d(n.force()), n.groupSize()));
            }
        }
        return out;
    }

    public static @Nullable UUID currentTarget() {
        return targetSubLevelId;
    }

    public static @Nullable ForceSnapshot currentSnapshot() {
        return snapshot;
    }

    public static Map<ResourceLocation, List<ForceClusterer.Cluster>> smoothedClusters() {
        return smoothedClusters;
    }

    private static void clear() {
        targetSubLevelId = null;
        snapshot = null;
        smoothedClusters = new HashMap<>();
        lastHeartbeatTick = -HEARTBEAT_INTERVAL_TICKS;
    }

    private static boolean isWearingActiveGoggles(final LocalPlayer player) {
        final ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.is(Schematician.SCHEMATICIANS_GOGGLES.asItem())) {
            return false;
        }
        return head.getOrDefault(SchematicianDataComponents.DRAFTING_VIEW_ENABLED.get(), Boolean.TRUE);
    }

    private static @Nullable UUID raycastTargetSubLevel(final LocalPlayer player) {
        final int chunks = SchematicianClientConfig.TARGETING_CHUNKS.get();
        final double maxDist = chunks * 16.0;

        // player.pick uses sublevel-aware clip via Sable's BlockGetterMixin, so the hit location
        // can be passed straight to Sable.HELPER.getContainingClient.
        // We run on tick boundary, so partialTick = 1.0 (mirror PhysicsStaffClientHandler).
        final HitResult hit = player.pick(maxDist, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        final BlockHitResult blockHit = (BlockHitResult) hit;
        final Vec3 hitPos = blockHit.getLocation();

        final ClientSubLevel containing = Sable.HELPER.getContainingClient(hitPos);
        if (containing == null) {
            return null;
        }
        return ((SubLevel) containing).getUniqueId();
    }
}
