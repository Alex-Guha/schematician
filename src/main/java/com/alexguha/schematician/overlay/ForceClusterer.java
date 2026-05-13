package com.alexguha.schematician.overlay;

import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

// Port of Simulated's ForceClusterFinder simplified to remove the commons-lang3 MutableInt dep.
// Iteratively splits forces into direction clusters; forces with angular variance below the
// threshold collapse into one. See references/Simulated-Project/.../ForceClusterFinder.java for
// the original (license: MIT, same as us).
public final class ForceClusterer {
    private ForceClusterer() {}

    public record Cluster(Vector3d pos, Vector3d force, int groupSize) {}

    private static final class Working {
        Vector3d pos = new Vector3d();
        Vector3d force = new Vector3d();
        int groupSize;
    }

    private static final class Indexed {
        final Vector3dc pos;
        final Vector3dc force;
        int clusterIndex;
        Indexed(final Vector3dc pos, final Vector3dc force) {
            this.pos = pos;
            this.force = force;
        }
    }

    public static List<Cluster> cluster(final List<QueuedForceGroup.PointForce> forces, final double angleThresholdRadians) {
        if (forces.isEmpty()) return List.of();

        final List<Working> clusters = new ArrayList<>();
        final List<Indexed> indexed = new ArrayList<>(forces.size());
        for (final QueuedForceGroup.PointForce f : forces) {
            indexed.add(new Indexed(f.point(), f.force()));
        }

        final double thresholdSq = angleThresholdRadians * angleThresholdRadians;

        while (tryAddCluster(clusters, indexed, thresholdSq)) {
            while (!groupArrows(clusters, indexed)) {
                organizeClusters(clusters, indexed);
            }
        }

        organizeClusters(clusters, indexed);
        finalizePositions(clusters, indexed);

        final List<Cluster> out = new ArrayList<>(clusters.size());
        for (final Working w : clusters) {
            out.add(new Cluster(new Vector3d(w.pos), new Vector3d(w.force), w.groupSize));
        }
        return out;
    }

    private static boolean tryAddCluster(final List<Working> clusters, final List<Indexed> forces, final double thresholdSq) {
        if (clusters.isEmpty()) {
            final Working c = new Working();
            for (final Indexed f : forces) {
                c.force.add(Math.abs(f.force.x()), Math.abs(f.force.y()), Math.abs(f.force.z()));
            }
            if (c.force.lengthSquared() > 0.0) c.force.normalize();
            clusters.add(c);
            return true;
        }

        double maxVar = -1.0;
        Indexed outlier = null;
        for (final Indexed f : forces) {
            final double v = angularVariance(clusters.get(f.clusterIndex).force, f.force);
            if (v > maxVar) {
                maxVar = v;
                outlier = f;
            }
        }
        if (outlier != null && maxVar > thresholdSq) {
            final Working c = new Working();
            c.force.set(outlier.force);
            clusters.add(c);
            return true;
        }
        return false;
    }

    private static boolean groupArrows(final List<Working> clusters, final List<Indexed> forces) {
        boolean stable = true;
        for (final Indexed f : forces) {
            final int prev = f.clusterIndex;
            double minDist = Double.POSITIVE_INFINITY;
            int best = prev;
            for (int i = 0; i < clusters.size(); i++) {
                final double d = angularVariance(f.force, clusters.get(i).force);
                if (d < minDist) {
                    minDist = d;
                    best = i;
                }
            }
            f.clusterIndex = best;
            if (prev != best) stable = false;
        }
        return stable;
    }

    private static void organizeClusters(final List<Working> clusters, final List<Indexed> forces) {
        for (final Working c : clusters) {
            c.force.zero();
            c.groupSize = 0;
        }
        for (final Indexed f : forces) {
            final Working c = clusters.get(f.clusterIndex);
            c.force.add(f.force);
            c.groupSize++;
        }
        for (int k = clusters.size() - 1; k >= 0; k--) {
            if (clusters.get(k).groupSize == 0) {
                clusters.remove(k);
                for (final Indexed f : forces) {
                    if (f.clusterIndex > k) f.clusterIndex--;
                }
            }
        }
    }

    private static void finalizePositions(final List<Working> clusters, final List<Indexed> forces) {
        for (final Indexed f : forces) {
            final Working c = clusters.get(f.clusterIndex);
            final double lenSq = c.force.lengthSquared();
            if (lenSq <= 0.0) continue;
            c.pos.fma(c.force.dot(f.force) / lenSq, f.pos);
        }
    }

    // Squared sine-of-angle-ish variance, identical to Simulated's ForceClusterFinder.getVariance.
    // Returns 0 for parallel vectors, ~4 for anti-parallel.
    private static double angularVariance(final Vector3dc a, final Vector3dc b) {
        final double a2 = a.dot(a);
        final double b2 = b.dot(b);
        if (a2 <= 0.0 || b2 <= 0.0) return 0.0;
        final double ab = a.dot(b);
        return 2.0 * (1.0 - ab / Math.sqrt(a2 * b2));
    }
}
