package io.github.delta1513.stairway.world;

import org.bukkit.Material;

/** Main-thread block access. The Bukkit implementation is {@link BukkitBlocks}. */
public interface BlockEditor {

    Material get(int x, int y, int z);

    /** Sets the block type without physics updates (PLAN.md §10). */
    void set(int x, int y, int z, Material material);
}
