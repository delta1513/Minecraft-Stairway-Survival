package io.github.delta1513.stairway.world;

/** Takes chunk snapshots; called on the main thread only. */
public interface SnapshotProvider {

    BlockSnapshot capture(int chunkX, int chunkZ);
}
