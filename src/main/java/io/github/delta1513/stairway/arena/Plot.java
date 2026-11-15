package io.github.delta1513.stairway.arena;

import java.util.ArrayList;
import java.util.List;

/**
 * One 3x3 plot (PLAN.md §2, §3.1), spanning every Y. Pure data and geometry.
 *
 * @param index  position in the colour list (0..5)
 * @param colour lower-case colour name; also the command argument
 * @param cx     centre block X
 * @param cz     centre block Z
 */
public record Plot(int index, String colour, int cx, int cz) {

    /** Plot footprint in blocks along each axis (fixed by the spec: 3x3). */
    public static final int SIZE = 3;

    private static final int HALF = SIZE / 2;

    public int minX() {
        return cx - HALF;
    }

    public int maxX() {
        return cx + HALF;
    }

    public int minZ() {
        return cz - HALF;
    }

    public int maxZ() {
        return cz + HALF;
    }

    public boolean contains(int bx, int bz) {
        return bx >= minX() && bx <= maxX() && bz >= minZ() && bz <= maxZ();
    }

    /** Boundary edge coordinates (block-edge space, PLAN.md §7): west face X. */
    public double edgeMinX() {
        return minX();
    }

    /** East face X: the outer face of the easternmost block ({@code cx + 2}). */
    public double edgeMaxX() {
        return maxX() + 1;
    }

    public double edgeMinZ() {
        return minZ();
    }

    public double edgeMaxZ() {
        return maxZ() + 1;
    }

    /**
     * Points {@code {x, z}} along the four vertical faces of the plot boundary, spaced {@code step}
     * apart, each point listed once (PLAN.md §7).
     */
    public List<double[]> borderPoints(double step) {
        if (step <= 0) {
            throw new IllegalArgumentException("step must be positive");
        }
        List<double[]> points = new ArrayList<>();
        double minX = edgeMinX();
        double maxX = edgeMaxX();
        double minZ = edgeMinZ();
        double maxZ = edgeMaxZ();
        int steps = (int) Math.round(SIZE / step);
        for (int i = 0; i < steps; i++) {
            double t = i * step;
            points.add(new double[] {minX + t, minZ}); // north face, west -> east
            points.add(new double[] {maxX, minZ + t}); // east face, north -> south
            points.add(new double[] {maxX - t, maxZ}); // south face, east -> west
            points.add(new double[] {minX, maxZ - t}); // west face, south -> north
        }
        return points;
    }

    /** Chest column X: the north-west column of the plot (PLAN.md §5). */
    public int chestX() {
        return minX();
    }

    /** Chest column Z: the north-west column of the plot (PLAN.md §5). */
    public int chestZ() {
        return minZ();
    }
}
