package io.github.delta1513.stairway.world;

import io.github.delta1513.stairway.arena.Arena;

/**
 * An inclusive axis-aligned block box (PLAN.md §10: "Region"). {@code minY > maxY} denotes an
 * empty region, which is what remains once the floor passes the top of the world.
 */
public record Region(int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {

    private static final int CHUNK_SHIFT = 4;

    public Region {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Inverted region columns");
        }
    }

    /** The gameplay area columns × [minY, maxY]. */
    public static Region gameplayArea(Arena arena, int minY, int maxY) {
        return new Region(arena.minX(), arena.maxX(), arena.minZ(), arena.maxZ(), minY, maxY);
    }

    public boolean isEmpty() {
        return minY > maxY;
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public int sizeY() {
        return isEmpty() ? 0 : maxY - minY + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeZ() * sizeY();
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY;
    }

    /** Coordinates {@code {x, y, z}} of the {@code index}-th block, layer by layer (0 ≤ index &lt; volume). */
    public int[] positionAt(long index) {
        if (index < 0 || index >= volume()) {
            throw new IndexOutOfBoundsException("index " + index);
        }
        int sx = sizeX();
        long layer = (long) sx * sizeZ();
        int x = minX + (int) (index % sx);
        int z = minZ + (int) ((index / sx) % sizeZ());
        int y = minY + (int) (index / layer);
        return new int[] {x, y, z};
    }

    public int minChunkX() {
        return minX >> CHUNK_SHIFT;
    }

    public int maxChunkX() {
        return maxX >> CHUNK_SHIFT;
    }

    public int minChunkZ() {
        return minZ >> CHUNK_SHIFT;
    }

    public int maxChunkZ() {
        return maxZ >> CHUNK_SHIFT;
    }
}
