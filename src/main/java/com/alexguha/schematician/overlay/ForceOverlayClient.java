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

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
            return;
        }

        if (!newTarget.equals(targetSubLevelId)) {
            targetSubLevelId = newTarget;
            snapshot = null;
            lastHeartbeatTick = localTick - HEARTBEAT_INTERVAL_TICKS;
        }

        if (localTick - lastHeartbeatTick >= HEARTBEAT_INTERVAL_TICKS) {
            PacketDistributor.sendToServer(new ForceSubscribePacket(newTarget));
            lastHeartbeatTick = localTick;
        }

        if (snapshot != null && localTick - snapshot.receivedAtTick > SNAPSHOT_TTL_TICKS) {
            snapshot = null;
        }
    }

    public static void handleSnapshot(final ForceSnapshotPacket packet) {
        if (targetSubLevelId == null || !targetSubLevelId.equals(packet.subLevelId())) {
            return;
        }
        snapshot = new ForceSnapshot(packet.subLevelId(), packet.mass(), packet.forces(), localTick);
    }

    public static @Nullable UUID currentTarget() {
        return targetSubLevelId;
    }

    public static @Nullable ForceSnapshot currentSnapshot() {
        return snapshot;
    }

    private static void clear() {
        targetSubLevelId = null;
        snapshot = null;
        lastHeartbeatTick = Long.MIN_VALUE;
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
