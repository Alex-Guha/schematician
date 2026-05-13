package com.alexguha.schematician.overlay;

import com.alexguha.schematician.Schematician;
import com.alexguha.schematician.component.SchematicianDataComponents;
import com.alexguha.schematician.config.SchematicianClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Veil render-stage handler for the force overlay. For the targeted sublevel:
//   - Draws a small filled cube at the rotation point (== CoM in local frame).
//   - For each ForceGroup in the latest snapshot, clusters near-parallel forces and renders
//     one colored line + arrowhead per cluster, scaled by config.
//
// Coordinate frames:
//   - PointForce.point and renderPose.rotationPoint() are both in the sublevel's local block
//     frame (same frame used by Sable's vanilla sublevel block renderer).
//   - We translate the PoseStack to (renderPose.position() - camera), then rotate by
//     renderPose.orientation(). This puts our local origin at the rotation point with axes
//     aligned to the sublevel — so a force at `point` is drawn at `point - rotationPoint`.
public final class ForceOverlayRenderer {
    private ForceOverlayRenderer() {}

    public static void onRenderStage(final VeilRenderLevelStageEvent.Stage stage,
                                     final MultiBufferSource.BufferSource bufferSource,
                                     final Camera camera) {
        if (stage != VeilRenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        final ClientLevel level = mc.level;
        if (player == null || level == null) return;
        if (!isWearingActiveGoggles(player)) return;

        final UUID targetId = ForceOverlayClient.currentTarget();
        if (targetId == null) return;

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        final SubLevel raw = container.getSubLevel(targetId);
        if (!(raw instanceof final ClientSubLevel clientSubLevel)) return;

        final Pose3dc renderPose = clientSubLevel.renderPose();
        final Vector3dc renderPos = renderPose.position();
        final Vector3dc rotationPoint = renderPose.rotationPoint();
        final Vec3 camPos = camera.getPosition();

        final PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        poseStack.translate(renderPos.x() - camPos.x, renderPos.y() - camPos.y, renderPos.z() - camPos.z);
        poseStack.mulPose(new Quaternionf(renderPose.orientation()));

        renderCenterOfMass(poseStack, bufferSource);

        final ForceOverlayClient.ForceSnapshot snapshot = ForceOverlayClient.currentSnapshot();
        if (snapshot != null) {
            renderForces(poseStack, bufferSource, snapshot, rotationPoint);
        }

        poseStack.popPose();
        bufferSource.endLastBatch();
    }

    private static void renderCenterOfMass(final PoseStack poseStack, final MultiBufferSource bufferSource) {
        // Origin of the current matrix == CoM. Small filled box, white-ish, semi-transparent.
        final double half = 0.12;
        DebugRenderer.renderFilledBox(
                poseStack, bufferSource,
                new AABB(-half, -half, -half, half, half, half),
                0.95f, 0.95f, 1.0f, 0.85f);
    }

    private static void renderForces(final PoseStack poseStack,
                                     final MultiBufferSource bufferSource,
                                     final ForceOverlayClient.ForceSnapshot snapshot,
                                     final Vector3dc rotationPoint) {
        final double scale = SchematicianClientConfig.METERS_PER_NEWTON.get();
        final double minLen = SchematicianClientConfig.MIN_ARROW_LENGTH.get();
        final double maxLen = SchematicianClientConfig.MAX_ARROW_LENGTH.get();
        final double angleThreshold = SchematicianClientConfig.CLUSTER_ANGLE_RADIANS.get();

        final VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        for (final Map.Entry<ResourceLocation, List<QueuedForceGroup.PointForce>> entry : snapshot.forces().entrySet()) {
            final ForceGroup group = ForceGroups.REGISTRY.get(entry.getKey());
            if (group == null) continue;

            final int packedColor = 0xFF000000 | group.color();
            final float r = ((packedColor >> 16) & 0xFF) / 255.0f;
            final float g = ((packedColor >> 8) & 0xFF) / 255.0f;
            final float b = (packedColor & 0xFF) / 255.0f;

            final List<ForceClusterer.Cluster> clusters = ForceClusterer.cluster(entry.getValue(), angleThreshold);
            for (final ForceClusterer.Cluster c : clusters) {
                drawArrow(poseStack, consumer, c.pos(), c.force(), rotationPoint, scale, minLen, maxLen, r, g, b);
            }
        }
    }

    private static void drawArrow(final PoseStack poseStack,
                                  final VertexConsumer consumer,
                                  final Vector3dc forcePoint,
                                  final Vector3dc forceVec,
                                  final Vector3dc rotationPoint,
                                  final double scale,
                                  final double minLen,
                                  final double maxLen,
                                  final float r, final float g, final float b) {
        final double magnitude = forceVec.length();
        if (magnitude < 1.0e-6) return;

        double length = magnitude * scale;
        if (length < minLen) length = minLen;
        if (length > maxLen) length = maxLen;

        final Vector3d dir = new Vector3d(forceVec).div(magnitude);

        // Origin of poseStack is at rotationPoint (in block-frame). Force point is in block-frame
        // too, so we subtract rotationPoint to get the offset within the current matrix.
        final double bx = forcePoint.x() - rotationPoint.x();
        final double by = forcePoint.y() - rotationPoint.y();
        final double bz = forcePoint.z() - rotationPoint.z();

        final double tx = bx + dir.x * length;
        final double ty = by + dir.y * length;
        final double tz = bz + dir.z * length;

        // Shaft.
        line(poseStack, consumer, bx, by, bz, tx, ty, tz, r, g, b, dir);

        // Arrowhead: two short backwards-angled lines, length = 20% of shaft, splayed off-axis.
        // Build any vector not parallel to dir to derive a perpendicular basis.
        final Vector3d ref = Math.abs(dir.y) < 0.9 ? new Vector3d(0, 1, 0) : new Vector3d(1, 0, 0);
        final Vector3d perp1 = new Vector3d(dir).cross(ref).normalize();
        final Vector3d perp2 = new Vector3d(dir).cross(perp1).normalize();

        final double headLen = Math.max(0.12, length * 0.18);
        final double headSplay = headLen * 0.55;

        final double baseX = tx - dir.x * headLen;
        final double baseY = ty - dir.y * headLen;
        final double baseZ = tz - dir.z * headLen;

        for (int i = 0; i < 4; i++) {
            final Vector3d axis = (i % 2 == 0) ? perp1 : perp2;
            final double sign = (i < 2) ? 1.0 : -1.0;
            line(poseStack, consumer,
                    baseX + axis.x * headSplay * sign, baseY + axis.y * headSplay * sign, baseZ + axis.z * headSplay * sign,
                    tx, ty, tz,
                    r, g, b, dir);
        }
    }

    private static void line(final PoseStack poseStack, final VertexConsumer consumer,
                             final double x1, final double y1, final double z1,
                             final double x2, final double y2, final double z2,
                             final float r, final float g, final float b,
                             final Vector3d normal) {
        final var pose = poseStack.last();
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(r, g, b, 1.0f)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(r, g, b, 1.0f)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static boolean isWearingActiveGoggles(final LocalPlayer player) {
        final ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.is(Schematician.SCHEMATICIANS_GOGGLES.asItem())) {
            return false;
        }
        return head.getOrDefault(SchematicianDataComponents.DRAFTING_VIEW_ENABLED.get(), Boolean.TRUE);
    }
}
