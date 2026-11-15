package io.github.delta1513.stairway.world;

import java.util.concurrent.Executor;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Bisected;
import org.bukkit.plugin.Plugin;

/** Thin Bukkit adapter: main-thread block edits plus {@code ChunkSnapshot}-backed scanning. */
public final class BukkitBlocks implements BlockEditor, SnapshotProvider {

    private final World world;

    public BukkitBlocks(World world) {
        this.world = world;
    }

    @Override
    public Material get(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType();
    }

    @Override
    public void set(int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }

    /** Main thread only. Loads the chunk if needed (the game holds plugin tickets on them). */
    @Override
    public BlockSnapshot capture(int chunkX, int chunkZ) {
        ChunkSnapshot snap = world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        return new BlockSnapshot() {
            @Override
            public Material typeAt(int x, int y, int z) {
                if (y < minY || y >= maxY) {
                    return Material.AIR;
                }
                return snap.getBlockType(x & 15, y, z & 15);
            }

            @Override
            public boolean isUpperHalf(int x, int y, int z) {
                return y >= minY && y < maxY
                        && snap.getBlockData(x & 15, y, z & 15) instanceof Bisected b
                        && b.getHalf() == Bisected.Half.TOP;
            }
        };
    }

    /** Runs scans on Bukkit's async scheduler. */
    public static Executor asyncExecutor(Plugin plugin) {
        return runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable);
    }
}
