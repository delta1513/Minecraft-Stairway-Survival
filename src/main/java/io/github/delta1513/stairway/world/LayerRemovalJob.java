package io.github.delta1513.stairway.world;

import org.bukkit.Material;

/**
 * Sets every block of a region to {@link Material#AIR}, bedrock included (PLAN.md §10.1). Every
 * block is written (no read), so a two-layer 101×101 job is 20 402 writes.
 */
public final class LayerRemovalJob implements BlockJob {

    private final Region region;
    private final BlockEditor editor;
    private final long total;
    private long next;

    public LayerRemovalJob(Region region, BlockEditor editor) {
        this.region = region;
        this.editor = editor;
        this.total = region.volume();
    }

    @Override
    public int process(int budget) {
        int done = 0;
        while (done < budget && next < total) {
            int[] p = region.positionAt(next++);
            editor.set(p[0], p[1], p[2], Material.AIR);
            done++;
        }
        return done;
    }

    @Override
    public boolean isDone() {
        return next >= total;
    }
}
