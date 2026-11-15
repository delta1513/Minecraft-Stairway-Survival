package io.github.delta1513.stairway.world;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;

/** In-memory block store implementing the world abstractions, for server-free tests. */
public final class FakeWorld implements BlockEditor, SnapshotProvider {

    private record Pos(int x, int y, int z) {}

    private final Map<Pos, Material> blocks = new HashMap<>();
    private final Map<Pos, Boolean> upperHalves = new HashMap<>();
    public int writes;
    public int snapshotsTaken;

    public void put(int x, int y, int z, Material m) {
        blocks.put(new Pos(x, y, z), m);
    }

    public void putUpper(int x, int y, int z, Material m) {
        put(x, y, z, m);
        upperHalves.put(new Pos(x, y, z), true);
    }

    @Override
    public Material get(int x, int y, int z) {
        return blocks.getOrDefault(new Pos(x, y, z), Material.STONE);
    }

    @Override
    public void set(int x, int y, int z, Material m) {
        writes++;
        blocks.put(new Pos(x, y, z), Objects.requireNonNull(m));
    }

    @Override
    public BlockSnapshot capture(int chunkX, int chunkZ) {
        snapshotsTaken++;
        Map<Pos, Material> copy = new HashMap<>(blocks);
        Map<Pos, Boolean> halves = new HashMap<>(upperHalves);
        return new BlockSnapshot() {
            @Override
            public Material typeAt(int x, int y, int z) {
                return copy.getOrDefault(new Pos(x, y, z), Material.STONE);
            }

            @Override
            public boolean isUpperHalf(int x, int y, int z) {
                return halves.getOrDefault(new Pos(x, y, z), false);
            }
        };
    }
}
