package com.alexguha.schematician.overlay;

import com.alexguha.schematician.Schematician;
import com.alexguha.schematician.component.SchematicianDataComponents;
import com.alexguha.schematician.config.SchematicianClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Render-stage handler for the force overlay.
//
// Runs at AFTER_LEVEL so we land after DraftingViewHandler.applyIfWearingGoggles
// has run the post-process pipeline — the overlay then draws on top of the post-processed image
// without being palette-shifted itself. Uses OverlayRenderTypes.forceLines() to disable depth
// testing, so vectors and the CoM marker remain visible through the sublevel's own blocks.
//
// Matrix handling: the post-process call clobbers RenderSystem's modelview matrix (it sets up
// screen-space matrices for the fullscreen quad and doesn't restore them). We seed it from the
// event's modelViewMatrix, draw, then restore via push/pop on the modelview stack. Without
// this, vertices submitted in world-relative coords render in identity-view space (the overlay
// appears glued to the camera, not to the sublevel).
//
// Coordinate frame: PointForce.point and renderPose.rotationPoint() are both in the sublevel's
// local block frame. We translate the PoseStack to (renderPose.position() - camera), rotate by
// renderPose.orientation(), and the local origin then sits at the rotation point with axes
// aligned to the sublevel — so a force at `point` is drawn at `point - rotationPoint`.
public final class ForceOverlayRenderer {
    private ForceOverlayRenderer() {}

    // CoM cube half-edge (so the cube's edge length is 2 * COM_HALF).
    private static final double COM_HALF = 0.08;

    // Tail-bead sphere radius scales with sublevel size (so big contraptions get correspondingly
    // visible tail beads), capped at 1/3 of the CoM cube's width so the bead can never visually
    // dominate the CoM marker. All other arrow geometry caps key off this radius.
    private static final double TAIL_SPHERE_PER_BBOX = 0.005;   // 0.5% of max bbox extent
    private static final double MAX_TAIL_SPHERE_RADIUS = (COM_HALF * 2.0) / 3.0;

    // Arrow-head shape coefficients. Everything else (shaft thickness, cone tip dimensions)
    // is derived from these so a single change here cascades cleanly.
    private static final double CONE_LEN_PER_LENGTH = 0.10;     // cone is 10% of the arrow length
    private static final double CONE_RADIUS_PER_LEN = 0.40;     // cone base radius = 40% of cone length
    private static final double SHAFT_RADIUS_PER_CONE = 0.35;   // shaft radius = 35% of cone base radius

    // Diameter caps relative to the tail sphere: shaft ≤ sphere diameter, cone base ≤ 1.5×
    // sphere diameter. Cone radius binds first (cone:shaft natural ratio 2.86 > cap ratio 1.5),
    // so the shaft cap is a redundant safety backstop.
    private static final double CONE_RADIUS_PER_TAIL = 1.5;
    private static final double SHAFT_RADIUS_PER_TAIL = 1.0;

    public static void onRenderStage(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;

        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        final ClientLevel level = mc.level;
        if (player == null || level == null) return;
        if (!isWearingActiveGoggles(player)) return;

        final Camera camera = event.getCamera();
        final Matrix4fc modelViewMatrix = event.getModelViewMatrix();
        final MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

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

        // Restore the world-render modelview matrix that the drafting-view post-process clobbered
        // when it bound its fullscreen quad pipeline.
        final Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.set(modelViewMatrix);
        RenderSystem.applyModelViewMatrix();

        try {
            final PoseStack poseStack = new PoseStack();
            poseStack.translate(renderPos.x() - camPos.x, renderPos.y() - camPos.y, renderPos.z() - camPos.z);
            poseStack.mulPose(new Quaternionf(renderPose.orientation()));

            final ForceOverlayClient.ForceSnapshot snapshot = ForceOverlayClient.currentSnapshot();

            // CoM cube tints by snapshot state: white = snapshot received, magenta = waiting for
            // first packet. Lets us tell at a glance whether the server-tracking pipeline is
            // delivering data versus the client-only marker rendering correctly.
            final RenderType fillType = OverlayRenderTypes.overlayFill();
            renderCenterOfMass(poseStack, bufferSource.getBuffer(fillType), snapshot != null);
            bufferSource.endBatch(fillType);

            if (snapshot != null) {
                renderForces(poseStack, bufferSource, rotationPoint, clientSubLevel);
            }
        } finally {
            mvStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void renderCenterOfMass(final PoseStack poseStack, final VertexConsumer consumer, final boolean haveSnapshot) {
        // Filled cube at the rotation point (== CoM). Light-gray once we have a force snapshot,
        // plain white while we're still waiting — gives a subtle but visible loading affordance
        // without the colored tint of earlier iterations.
        final float r, g, b;
        if (haveSnapshot) {
            r = 0.75f; g = 0.75f; b = 0.75f;
        } else {
            r = 1.0f; g = 1.0f; b = 1.0f;
        }
        quadCube(poseStack, consumer, COM_HALF, r, g, b, 1.0f);
    }

    // 6 quads forming an axis-aligned cube centered at the origin, edge length 2*half.
    // QUADS render type, POSITION_COLOR.
    private static void quadCube(final PoseStack poseStack, final VertexConsumer consumer,
                                 final double half,
                                 final float r, final float g, final float b, final float a) {
        final var pose = poseStack.last();
        final float n = (float) -half;
        final float p = (float) half;
        // -X face
        quad(pose, consumer, n, n, n,  n, p, n,  n, p, p,  n, n, p, r, g, b, a);
        // +X face
        quad(pose, consumer, p, n, p,  p, p, p,  p, p, n,  p, n, n, r, g, b, a);
        // -Y face
        quad(pose, consumer, n, n, p,  p, n, p,  p, n, n,  n, n, n, r, g, b, a);
        // +Y face
        quad(pose, consumer, n, p, n,  p, p, n,  p, p, p,  n, p, p, r, g, b, a);
        // -Z face
        quad(pose, consumer, p, n, n,  p, p, n,  n, p, n,  n, n, n, r, g, b, a);
        // +Z face
        quad(pose, consumer, n, n, p,  n, p, p,  p, p, p,  p, n, p, r, g, b, a);
    }

    private static void quad(final PoseStack.Pose pose, final VertexConsumer consumer,
                             final float x1, final float y1, final float z1,
                             final float x2, final float y2, final float z2,
                             final float x3, final float y3, final float z3,
                             final float x4, final float y4, final float z4,
                             final float r, final float g, final float b, final float a) {
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        consumer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
        consumer.addVertex(pose, x4, y4, z4).setColor(r, g, b, a);
    }

    // Precomputed per-cluster geometry. Shared between the two render passes (lines + triangles)
    // so we cluster the snapshot once. Coordinates are still in block-frame; the actual draw
    // subtracts rotationPoint per-pass.
    private record ArrowDraw(
            double bx, double by, double bz,
            double tx, double ty, double tz,
            double dirX, double dirY, double dirZ,
            Vector3d perp1, Vector3d perp2,
            double length,
            float r, float g, float b) {}

    private static final ResourceLocation GRAVITY_KEY =
            ResourceLocation.fromNamespaceAndPath("sable", "gravity");

    private static void renderForces(final PoseStack poseStack,
                                     final MultiBufferSource.BufferSource bufferSource,
                                     final Vector3dc rotationPoint,
                                     final ClientSubLevel clientSubLevel) {
        final double gravityFraction = SchematicianClientConfig.GRAVITY_ARROW_FRACTION.get();
        final double saturation = SchematicianClientConfig.ARROW_SATURATION.get();
        final double minLen = SchematicianClientConfig.MIN_ARROW_LENGTH.get();

        // Gravity-anchored scaling: gravity is sized to a fraction of the sublevel's bbox height
        // (Y extent — keeps the ruler vertical-aligned and unaffected by wide-flat hulls).
        // Forces ≤ gravity scale linearly. Forces > gravity pass through a soft saturation curve
        // so a balloon with several × gravity worth of lift doesn't overrun the sublevel.
        final List<ForceClusterer.Cluster> gravityClusters =
                ForceOverlayClient.smoothedClusters().get(GRAVITY_KEY);
        if (gravityClusters == null || gravityClusters.isEmpty()) return;
        final double gravityMagnitude = gravityClusters.get(0).force().length();
        if (gravityMagnitude < 1.0e-6) return;

        final var bbox = clientSubLevel.boundingBox();
        final double bboxHeight = bbox.maxY() - bbox.minY();
        if (bboxHeight <= 0.0) return;

        final double gravityArrowLen = bboxHeight * gravityFraction;

        // Tail sphere (and the cone/shaft caps that derive from it) scales with the sublevel's
        // max bbox extent so big contraptions get correspondingly visible markers, capped at
        // 1/3 the CoM cube's width so the bead never visually dominates the CoM marker.
        final double bboxMaxExtent = Math.max(bbox.maxX() - bbox.minX(),
                Math.max(bboxHeight, bbox.maxZ() - bbox.minZ()));
        final double tailSphereRadius = Math.min(MAX_TAIL_SPHERE_RADIUS,
                bboxMaxExtent * TAIL_SPHERE_PER_BBOX);
        final double maxConeRadius = tailSphereRadius * CONE_RADIUS_PER_TAIL;
        final double maxShaftRadius = tailSphereRadius * SHAFT_RADIUS_PER_TAIL;
        final double maxShapeLength = maxConeRadius / (CONE_LEN_PER_LENGTH * CONE_RADIUS_PER_LEN);

        final List<ArrowDraw> arrows = new ArrayList<>();

        // Clustering + temporal smoothing live in ForceOverlayClient — see smoothedClusters().
        // The renderer just reads the pre-smoothed result and applies colors per ForceGroup.
        for (final Map.Entry<ResourceLocation, List<ForceClusterer.Cluster>> entry : ForceOverlayClient.smoothedClusters().entrySet()) {
            final ForceGroup group = ForceGroups.REGISTRY.get(entry.getKey());
            if (group == null) continue;

            final int color = group.color();
            final float r = ((color >> 16) & 0xFF) / 255.0f;
            final float g = ((color >> 8) & 0xFF) / 255.0f;
            final float b = (color & 0xFF) / 255.0f;

            for (final ForceClusterer.Cluster c : entry.getValue()) {
                final ArrowDraw d = buildArrow(c.pos(), c.force(), rotationPoint,
                        gravityMagnitude, gravityArrowLen, saturation, minLen, r, g, b);
                if (d != null) arrows.add(d);
            }
        }

        if (arrows.isEmpty()) return;

        // Single pass — shaft cylinder, cone tip, and tail bead are all triangles, so we only
        // need one RenderType. Replaces the previous LINES shaft, which kept a constant pixel
        // width at any distance and visually engulfed the cone tip when the arrow was small on
        // screen. A 3D cylinder scales with view distance like the cone does.
        final RenderType triType = OverlayRenderTypes.overlayTriangles();
        final VertexConsumer triConsumer = bufferSource.getBuffer(triType);
        final var pose = poseStack.last();
        for (final ArrowDraw a : arrows) {
            // Shape dimensions saturate at maxShapeLength — the cone tip and shaft thickness
            // freeze in lock-step. The arrow's total span still uses `a.length`, so longer
            // arrows just get a longer shaft, not a bigger head.
            final double shapeLen = Math.min(a.length, maxShapeLength);
            final double coneLen = Math.max(0.09, shapeLen * CONE_LEN_PER_LENGTH);
            final double coneRadius = Math.min(maxConeRadius, coneLen * CONE_RADIUS_PER_LEN);
            final double shaftRadius = Math.min(maxShaftRadius, coneRadius * SHAFT_RADIUS_PER_CONE);
            final double shaftEndX = a.tx - a.dirX * coneLen;
            final double shaftEndY = a.ty - a.dirY * coneLen;
            final double shaftEndZ = a.tz - a.dirZ * coneLen;

            cylinder(pose, triConsumer,
                    a.bx, a.by, a.bz,
                    shaftEndX, shaftEndY, shaftEndZ,
                    a.perp1, a.perp2,
                    shaftRadius, 6,
                    a.r, a.g, a.b);

            cone(poseStack, triConsumer,
                    a.tx, a.ty, a.tz,
                    a.dirX, a.dirY, a.dirZ,
                    a.perp1, a.perp2,
                    coneLen, coneRadius,
                    a.r, a.g, a.b);

            sphere(pose, triConsumer, a.bx, a.by, a.bz, tailSphereRadius, 8, 4, a.r, a.g, a.b);
        }
        bufferSource.endBatch(triType);
    }

    // Capped-on-the-cone-side, open-on-the-tail-side cylinder running from (x0..) to (x1..)
    // along the axis spanned by perp1/perp2. The tail bead sphere closes the base end; the cone
    // closes the tip end. `segments` controls the polygon count around the circumference.
    private static void cylinder(final PoseStack.Pose pose, final VertexConsumer consumer,
                                 final double x0, final double y0, final double z0,
                                 final double x1, final double y1, final double z1,
                                 final Vector3d perp1, final Vector3d perp2,
                                 final double radius,
                                 final int segments,
                                 final float r, final float g, final float b) {
        for (int i = 0; i < segments; i++) {
            final double a0 = 2.0 * Math.PI * i / segments;
            final double a1 = 2.0 * Math.PI * (i + 1) / segments;
            final double c0 = Math.cos(a0) * radius, s0 = Math.sin(a0) * radius;
            final double c1 = Math.cos(a1) * radius, s1 = Math.sin(a1) * radius;

            final double offX0 = perp1.x * c0 + perp2.x * s0;
            final double offY0 = perp1.y * c0 + perp2.y * s0;
            final double offZ0 = perp1.z * c0 + perp2.z * s0;
            final double offX1 = perp1.x * c1 + perp2.x * s1;
            final double offY1 = perp1.y * c1 + perp2.y * s1;
            final double offZ1 = perp1.z * c1 + perp2.z * s1;

            triangle(pose, consumer,
                    x0 + offX0, y0 + offY0, z0 + offZ0,
                    x1 + offX0, y1 + offY0, z1 + offZ0,
                    x1 + offX1, y1 + offY1, z1 + offZ1,
                    r, g, b);
            triangle(pose, consumer,
                    x0 + offX0, y0 + offY0, z0 + offZ0,
                    x1 + offX1, y1 + offY1, z1 + offZ1,
                    x0 + offX1, y0 + offY1, z0 + offZ1,
                    r, g, b);
        }
    }

    // Low-poly UV sphere centered at (cx, cy, cz). lonSegs * latSegs * 2 triangles total.
    // Reused for the per-arrow tail bead.
    private static void sphere(final PoseStack.Pose pose, final VertexConsumer consumer,
                               final double cx, final double cy, final double cz, final double radius,
                               final int lonSegs, final int latSegs,
                               final float r, final float g, final float b) {
        final double[] sinPhi = new double[latSegs + 1];
        final double[] cosPhi = new double[latSegs + 1];
        for (int j = 0; j <= latSegs; j++) {
            final double phi = -Math.PI / 2.0 + Math.PI * j / latSegs;
            sinPhi[j] = Math.sin(phi);
            cosPhi[j] = Math.cos(phi);
        }
        final double[] sinTheta = new double[lonSegs + 1];
        final double[] cosTheta = new double[lonSegs + 1];
        for (int i = 0; i <= lonSegs; i++) {
            final double theta = 2.0 * Math.PI * i / lonSegs;
            sinTheta[i] = Math.sin(theta);
            cosTheta[i] = Math.cos(theta);
        }

        for (int j = 0; j < latSegs; j++) {
            final double y0 = sinPhi[j] * radius;
            final double y1 = sinPhi[j + 1] * radius;
            final double r0 = cosPhi[j] * radius;
            final double r1 = cosPhi[j + 1] * radius;
            for (int i = 0; i < lonSegs; i++) {
                final double x00 = cosTheta[i] * r0,     z00 = sinTheta[i] * r0;
                final double x01 = cosTheta[i + 1] * r0, z01 = sinTheta[i + 1] * r0;
                final double x10 = cosTheta[i] * r1,     z10 = sinTheta[i] * r1;
                final double x11 = cosTheta[i + 1] * r1, z11 = sinTheta[i + 1] * r1;

                triangle(pose, consumer,
                        cx + x00, cy + y0, cz + z00,
                        cx + x10, cy + y1, cz + z10,
                        cx + x11, cy + y1, cz + z11,
                        r, g, b);
                triangle(pose, consumer,
                        cx + x00, cy + y0, cz + z00,
                        cx + x11, cy + y1, cz + z11,
                        cx + x01, cy + y0, cz + z01,
                        r, g, b);
            }
        }
    }

    private static ArrowDraw buildArrow(final Vector3dc forcePoint,
                                        final Vector3dc forceVec,
                                        final Vector3dc rotationPoint,
                                        final double gravityMagnitude,
                                        final double gravityArrowLen,
                                        final double saturation,
                                        final double minLen,
                                        final float r, final float g, final float b) {
        final double magnitude = forceVec.length();
        if (magnitude < 1.0e-6) return null;

        // Linear up to gravity, then soft-saturating above. The saturation curve
        // `cap * r / (cap + r - 1)` passes through (1,1), is monotonic, and asymptotes to `cap`
        // — keeps relative magnitudes honest near gravity, prevents runaway above it. Floor at
        // `minLen` so tiny forces still render with a visible shaft past the cone.
        final double ratio = magnitude / gravityMagnitude;
        final double visualRatio = ratio <= 1.0
                ? ratio
                : saturation * ratio / (saturation + ratio - 1.0);
        final double length = Math.max(minLen, visualRatio * gravityArrowLen);

        final Vector3d dir = new Vector3d(forceVec).div(magnitude);

        final double bx = forcePoint.x() - rotationPoint.x();
        final double by = forcePoint.y() - rotationPoint.y();
        final double bz = forcePoint.z() - rotationPoint.z();

        final double tx = bx + dir.x * length;
        final double ty = by + dir.y * length;
        final double tz = bz + dir.z * length;

        final Vector3d ref = Math.abs(dir.y) < 0.9 ? new Vector3d(0, 1, 0) : new Vector3d(1, 0, 0);
        final Vector3d perp1 = new Vector3d(dir).cross(ref).normalize();
        final Vector3d perp2 = new Vector3d(dir).cross(perp1).normalize();

        return new ArrowDraw(bx, by, bz, tx, ty, tz, dir.x, dir.y, dir.z, perp1, perp2, length, r, g, b);
    }

    // Cone aimed forward along `dir`. Tip at (tipX, tipY, tipZ), base disc centered at
    // (tip - dir*length). Drawn as `segments` side triangles + a closing base fan.
    private static void cone(final PoseStack poseStack, final VertexConsumer consumer,
                             final double tipX, final double tipY, final double tipZ,
                             final double dx, final double dy, final double dz,
                             final Vector3d perp1, final Vector3d perp2,
                             final double length, final double radius,
                             final float r, final float g, final float b) {
        final int segments = 10;
        final double baseX = tipX - dx * length;
        final double baseY = tipY - dy * length;
        final double baseZ = tipZ - dz * length;

        final double[] cx = new double[segments + 1];
        final double[] cy = new double[segments + 1];
        final double[] cz = new double[segments + 1];
        for (int i = 0; i <= segments; i++) {
            final double angle = 2.0 * Math.PI * i / segments;
            final double c = Math.cos(angle);
            final double s = Math.sin(angle);
            cx[i] = baseX + (perp1.x * c + perp2.x * s) * radius;
            cy[i] = baseY + (perp1.y * c + perp2.y * s) * radius;
            cz[i] = baseZ + (perp1.z * c + perp2.z * s) * radius;
        }

        final var pose = poseStack.last();
        for (int i = 0; i < segments; i++) {
            // Side wall: tip → ring[i] → ring[i+1]
            triangle(pose, consumer,
                    tipX, tipY, tipZ,
                    cx[i], cy[i], cz[i],
                    cx[i + 1], cy[i + 1], cz[i + 1],
                    r, g, b);
            // Base cap: center → ring[i+1] → ring[i] (reversed winding so it's outward-facing)
            triangle(pose, consumer,
                    baseX, baseY, baseZ,
                    cx[i + 1], cy[i + 1], cz[i + 1],
                    cx[i], cy[i], cz[i],
                    r, g, b);
        }
    }

    private static void triangle(final PoseStack.Pose pose, final VertexConsumer consumer,
                                 final double x1, final double y1, final double z1,
                                 final double x2, final double y2, final double z2,
                                 final double x3, final double y3, final double z3,
                                 final float r, final float g, final float b) {
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, 1.0f);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(r, g, b, 1.0f);
        consumer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(r, g, b, 1.0f);
    }

    private static boolean isWearingActiveGoggles(final LocalPlayer player) {
        final ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.is(Schematician.SCHEMATICIANS_GOGGLES.asItem())) {
            return false;
        }
        return head.getOrDefault(SchematicianDataComponents.DRAFTING_VIEW_ENABLED.get(), Boolean.TRUE);
    }
}
