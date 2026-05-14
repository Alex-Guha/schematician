package com.alexguha.schematician.net.server;

import com.alexguha.schematician.Schematician;
import com.alexguha.schematician.net.ForceSnapshotPacket;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// Server-side tracker for which players want force snapshots for which sublevels.
//
// Lifecycle mirrors Simulated's DiagramEntity recording flow:
//   1. Client heartbeats arrive via ForceSubscribePacket -> handleSubscribe()
//   2. We bump a per-sublevel tick deadline; enable individualQueuedForcesTracking on first sub
//   3. Each ForgeSablePostPhysicsTickEvent: build a ForceSnapshotPacket and send to subscribers
//   4. Subscriptions that haven't been heartbeated within HEARTBEAT_TIMEOUT_TICKS expire and we
//      disable tracking on a sublevel once it has no more subscribers
//
// The WeakHashMap is keyed by ServerSubLevel; once Sable drops the sublevel (assembly broken,
// chunk unloaded, etc.) our subscription entries are GC'd automatically.
@EventBusSubscriber(modid = Schematician.MODID)
public final class ForceTrackingDispatcher {
    private ForceTrackingDispatcher() {}

    private static final long HEARTBEAT_TIMEOUT_TICKS = 30L;

    private static final Map<ServerSubLevel, Map<UUID, Long>> SUBSCRIPTIONS = new WeakHashMap<>();

    public static void handleSubscribe(final ServerPlayer player, final UUID subLevelId) {
        final ServerLevel level = player.serverLevel();
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        final SubLevel subLevel = container.getSubLevel(subLevelId);
        if (!(subLevel instanceof final ServerSubLevel serverSubLevel)) return;

        final Map<UUID, Long> playerMap = SUBSCRIPTIONS.computeIfAbsent(serverSubLevel, k -> {
            serverSubLevel.enableIndividualQueuedForcesTracking(true);
            return new HashMap<>();
        });
        playerMap.put(player.getUUID(), level.getGameTime());
    }

    @SubscribeEvent
    public static void onPlayerLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        final UUID id = event.getEntity().getUUID();
        final Iterator<Map.Entry<ServerSubLevel, Map<UUID, Long>>> it = SUBSCRIPTIONS.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<ServerSubLevel, Map<UUID, Long>> entry = it.next();
            entry.getValue().remove(id);
            if (entry.getValue().isEmpty()) {
                entry.getKey().enableIndividualQueuedForcesTracking(false);
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onPostPhysicsTick(final ForgeSablePostPhysicsTickEvent event) {
        final SubLevelPhysicsSystem physicsSystem = event.getPhysicsSystem();
        final ServerLevel level = physicsSystem.getLevel();
        final MinecraftServer server = level.getServer();
        if (server == null) return;

        final long now = level.getGameTime();

        final Iterator<Map.Entry<ServerSubLevel, Map<UUID, Long>>> it = SUBSCRIPTIONS.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<ServerSubLevel, Map<UUID, Long>> entry = it.next();
            final ServerSubLevel serverSubLevel = entry.getKey();

            if (serverSubLevel.isRemoved() || serverSubLevel.getLevel() != level) {
                continue;
            }

            final Map<UUID, Long> players = entry.getValue();
            players.values().removeIf(t -> now - t > HEARTBEAT_TIMEOUT_TICKS);
            if (players.isEmpty()) {
                serverSubLevel.enableIndividualQueuedForcesTracking(false);
                it.remove();
                continue;
            }

            // Tracking is a single shared boolean on the sublevel — Simulated's DiagramEntity
            // disables it when its recording ticket ends, which wipes ours too. Re-assert it here
            // every tick while we still have subscribers so closing the Contraption Diagram
            // doesn't strand the overlay with only synthesized gravity.
            if (!serverSubLevel.isTrackingIndividualQueuedForces()) {
                serverSubLevel.enableIndividualQueuedForcesTracking(true);
            }

            final ForceSnapshotPacket packet = buildPacket(serverSubLevel, physicsSystem);
            if (packet == null) continue;

            for (final UUID playerId : players.keySet()) {
                final ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    PacketDistributor.sendToPlayer(player, packet);
                }
            }
        }
    }

    private static @Nullable ForceSnapshotPacket buildPacket(final ServerSubLevel serverSubLevel,
                                                             final SubLevelPhysicsSystem physicsSystem) {
        final MassData massTracker = serverSubLevel.getMassTracker();
        if (massTracker.isInvalid()) {
            return null;
        }
        final double mass = massTracker.getMass();
        final Vector3dc centerOfMass = massTracker.getCenterOfMass();
        if (centerOfMass == null) {
            return null;
        }

        final double timeStep = 1.0 / 20.0 / physicsSystem.getConfig().substepsPerTick;

        final Map<ResourceLocation, List<QueuedForceGroup.PointForce>> outForces = new Object2ObjectOpenHashMap<>();

        final Object2ObjectMap<ForceGroup, QueuedForceGroup> queued = serverSubLevel.getQueuedForceGroups();
        if (queued != null) {
            for (final Map.Entry<ForceGroup, QueuedForceGroup> e : queued.entrySet()) {
                final ResourceLocation key = ForceGroups.REGISTRY.getKey(e.getKey());
                if (key == null) continue;

                final List<QueuedForceGroup.PointForce> recorded = e.getValue().getRecordedPointForces();
                if (recorded.isEmpty()) continue;

                final List<QueuedForceGroup.PointForce> normalized = new java.util.ArrayList<>(recorded.size());
                for (final QueuedForceGroup.PointForce pf : recorded) {
                    final Vector3dc force = new Vector3d(pf.force()).div(timeStep);
                    normalized.add(new QueuedForceGroup.PointForce(pf.point(), force));
                }
                outForces.put(key, normalized);
            }
        }

        // Synthesize a per-tick gravity entry matching the Diagram. Gravity isn't recorded as a
        // queued force on its own — we transform world gravity into the sublevel's local frame
        // and place it at the center of mass, scaled by total mass to get a force in newtons.
        final Pose3d pose = serverSubLevel.logicalPose();
        final Vector3d localGravity = pose.transformNormalInverse(
                DimensionPhysicsData.getGravity(serverSubLevel.getLevel())).mul(mass);
        final ResourceLocation gravityKey = ForceGroups.REGISTRY.getKey(ForceGroups.GRAVITY.get());
        if (gravityKey != null) {
            outForces.put(gravityKey, List.of(new QueuedForceGroup.PointForce(new Vector3d(centerOfMass), localGravity)));
        }

        return new ForceSnapshotPacket(serverSubLevel.getUniqueId(), mass, outForces);
    }
}
