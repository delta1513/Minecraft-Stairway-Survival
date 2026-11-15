package io.github.delta1513.stairway.world;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

class BukkitBlocksTest {

    private WorldMock world;
    private BukkitBlocks blocks;

    @BeforeEach
    void setUp() {
        world = MockBukkit.mock().addSimpleWorld("world");
        blocks = new BukkitBlocks(world);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void setAndGetGoThroughTheWorld() {
        blocks.set(-3, 70, 5, Material.LAVA);
        assertEquals(Material.LAVA, blocks.get(-3, 70, 5));
        assertEquals(Material.LAVA, world.getBlockAt(-3, 70, 5).getType());
    }

    @Test
    void snapshotOutOfWorldHeightReadsAsAir() {
        BlockSnapshot snap = blocks.capture(0, 0);
        assertEquals(Material.AIR, snap.typeAt(0, world.getMaxHeight() + 5, 0));
        assertEquals(Material.AIR, snap.typeAt(0, world.getMinHeight() - 5, 0));
        assertFalse(snap.isUpperHalf(0, world.getMaxHeight() + 5, 0));
    }

    @Test
    void layerRemovalThroughBukkitAdapterRemovesBedrock() {
        blocks.set(0, 0, 0, Material.BEDROCK);
        blocks.set(1, 1, 1, Material.STONE);
        var q = new BlockJobQueue(100, 10);
        q.submit(new LayerRemovalJob(new Region(0, 1, 0, 1, 0, 1), blocks));
        q.tick();
        assertEquals(Material.AIR, blocks.get(0, 0, 0));
        assertEquals(Material.AIR, blocks.get(1, 1, 1));
    }
}
