package io.github.delta1513.stairway.world;

import org.bukkit.Material;

/**
 * Immutable copy of one chunk's blocks, in world coordinates, safe to read from any thread
 * (a {@code ChunkSnapshot} in production).
 */
public interface BlockSnapshot {

    Material typeAt(int x, int y, int z);

    /** Whether the block is the upper half of a bisected block (two-tall plants). */
    boolean isUpperHalf(int x, int y, int z);
}
