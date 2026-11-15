package io.github.delta1513.stairway.game;

import io.github.delta1513.stairway.arena.Arena;
import io.github.delta1513.stairway.arena.Plot;
import io.github.delta1513.stairway.config.GameConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Plot preparation (PLAN.md §5): plugin chunk tickets, starter chests and plot spawn points. */
public final class PlotPreparer {

    private final Plugin plugin;
    private final World world;
    private final Arena arena;
    private final GameConfig config;
    private final List<Chunk> ticketed = new ArrayList<>();
    private ChunkTickets tickets = new ChunkTickets() {
        @Override
        public void add(Chunk chunk, Plugin owner) {
            chunk.addPluginChunkTicket(owner);
        }

        @Override
        public void remove(Chunk chunk, Plugin owner) {
            chunk.removePluginChunkTicket(owner);
        }
    };

    /** Chunk ticket operations; replaceable because MockBukkit does not implement them. */
    public interface ChunkTickets {
        void add(Chunk chunk, Plugin owner);

        void remove(Chunk chunk, Plugin owner);
    }

    public void useChunkTickets(ChunkTickets tickets) {
        this.tickets = tickets;
    }

    public PlotPreparer(Plugin plugin, World world, Arena arena, GameConfig config) {
        this.plugin = plugin;
        this.world = world;
        this.arena = arena;
        this.config = config;
    }

    /** Surface Y of a column: the highest non-leaf motion-blocking block. */
    public int surfaceY(int x, int z) {
        return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }

    /** Plot spawn point: plot centre, one block above the surface. */
    public Location spawnPoint(Plot plot) {
        return new Location(world, plot.cx() + 0.5, surfaceY(plot.cx(), plot.cz()) + 1, plot.cz() + 0.5);
    }

    /** Spawn surface of the world, one block above the highest block. */
    public Location worldSpawnSurface() {
        Location spawn = world.getSpawnLocation();
        int x = spawn.getBlockX();
        int z = spawn.getBlockZ();
        return new Location(world, x + 0.5, surfaceY(x, z) + 1, z + 0.5);
    }

    public void prepare(Collection<Plot> activePlots) {
        addChunkTickets();
        for (Plot plot : activePlots) {
            placeChest(plot);
        }
    }

    private void addChunkTickets() {
        releaseTickets();
        for (int cx = arena.minX() >> 4; cx <= arena.maxX() >> 4; cx++) {
            for (int cz = arena.minZ() >> 4; cz <= arena.maxZ() >> 4; cz++) {
                Chunk chunk = world.getChunkAt(cx, cz);
                tickets.add(chunk, plugin);
                ticketed.add(chunk);
            }
        }
    }

    public void releaseTickets() {
        for (Chunk chunk : ticketed) {
            tickets.remove(chunk, plugin);
        }
        ticketed.clear();
    }

    private void placeChest(Plot plot) {
        int x = plot.chestX();
        int z = plot.chestZ();
        Block block = world.getBlockAt(x, surfaceY(x, z) + 1, z);
        block.setType(Material.CHEST);
        if (block.getState() instanceof Chest chest) {
            Inventory inv = chest.getBlockInventory();
            for (ItemStack item : StarterKit.resolve(config.starterKit(), TeamColours.bed(plot.colour()),
                    plugin.getLogger())) {
                inv.addItem(item);
            }
        }
    }
}
