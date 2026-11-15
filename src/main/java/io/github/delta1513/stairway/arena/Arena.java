package io.github.delta1513.stairway.arena;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The static geometry of a game: the six plots on a circle around spawn, the gameplay area and
 * zone classification (PLAN.md §3). Pure Java, no server needed.
 */
public final class Arena {

    /** Number of plots/colours; plots sit every 360/6 = 60 degrees. */
    public static final int PLOT_COUNT = 6;

    private static final double DEGREES_BETWEEN_PLOTS = 360.0 / PLOT_COUNT;

    private final int spawnX;
    private final int spawnZ;
    private final int areaSize;
    private final int areaHalf;
    private final List<Plot> plots;

    /**
     * @param spawnX   spawn block X
     * @param spawnZ   spawn block Z
     * @param radius   plot circle radius in blocks
     * @param areaSize side length of the square gameplay area (odd)
     * @param colours  exactly six colour names; index = plot index
     */
    public Arena(int spawnX, int spawnZ, int radius, int areaSize, List<String> colours) {
        if (colours.size() != PLOT_COUNT) {
            throw new IllegalArgumentException("Expected " + PLOT_COUNT + " colours, got " + colours.size());
        }
        if (areaSize < 1 || areaSize % 2 == 0) {
            throw new IllegalArgumentException("Area size must be a positive odd number: " + areaSize);
        }
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
        this.areaSize = areaSize;
        this.areaHalf = areaSize / 2;

        List<Plot> built = new ArrayList<>(PLOT_COUNT);
        for (int i = 0; i < PLOT_COUNT; i++) {
            double theta = Math.toRadians(i * DEGREES_BETWEEN_PLOTS);
            int dx = roundHalfUp(radius * Math.cos(theta));
            int dz = roundHalfUp(radius * Math.sin(theta));
            built.add(new Plot(i, colours.get(i), spawnX + dx, spawnZ + dz));
        }
        this.plots = List.copyOf(built);
    }

    /** Round-half-up, {@code floor(v + 0.5)} (PLAN.md §3.1). */
    static int roundHalfUp(double v) {
        return (int) Math.floor(v + 0.5);
    }

    /** Block coordinate containing a world coordinate. */
    public static int blockOf(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    public List<Plot> plots() {
        return plots;
    }

    public Optional<Plot> plotByColour(String colour) {
        return plots.stream().filter(p -> p.colour().equalsIgnoreCase(colour)).findFirst();
    }

    // ---- gameplay area (PLAN.md §3.3) ----

    public int minX() {
        return spawnX - areaHalf;
    }

    public int maxX() {
        return spawnX + areaHalf;
    }

    public int minZ() {
        return spawnZ - areaHalf;
    }

    public int maxZ() {
        return spawnZ + areaHalf;
    }

    /** Whether the block column lies inside the (inclusive) gameplay area. */
    public boolean contains(int bx, int bz) {
        return bx >= minX() && bx <= maxX() && bz >= minZ() && bz <= maxZ();
    }

    /** World border centre X: the centre of the spawn block. */
    public double borderCenterX() {
        return spawnX + 0.5;
    }

    /** World border centre Z: the centre of the spawn block. */
    public double borderCenterZ() {
        return spawnZ + 0.5;
    }

    public double borderSize() {
        return areaSize;
    }

    // ---- zones (PLAN.md §3.2) ----

    /**
     * Classifies a block column against the given active plots only. Plots not in {@code active}
     * are not safe.
     */
    public Zone zoneOf(int bx, int bz, Collection<Plot> active) {
        int d = Integer.MAX_VALUE;
        for (Plot p : active) {
            int dp = Math.max(Math.abs(bx - p.cx()), Math.abs(bz - p.cz())) - Plot.SIZE / 2; // PLAN.md §3.2: Chebyshev distance - 1
            d = Math.min(d, dp);
        }
        if (d <= 0) {
            return Zone.INSIDE;
        }
        return d == 1 ? Zone.RING : Zone.WILD;
    }
}
