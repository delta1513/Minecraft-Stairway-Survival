package io.github.delta1513.stairway.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ScanReplaceJobTest {

    private static final Executor DIRECT = Runnable::run;
    private final FakeWorld world = new FakeWorld();
    private final Region region = new Region(-20, 20, -20, 20, 0, 10); // spans several chunks

    private void drain(BlockJobQueue q) {
        for (int i = 0; i < 1000 && !q.isIdle(); i++) {
            q.tick();
        }
        assertTrue(q.isIdle());
    }

    @Test
    void replacesOnlyMatchingBlocksInsideRegion() {
        world.put(0, 3, 0, Material.OAK_PLANKS);
        world.put(-20, 0, 20, Material.OAK_PLANKS); // corner of region
        world.put(21, 3, 0, Material.OAK_PLANKS); // outside in X
        world.put(0, 11, 0, Material.OAK_PLANKS); // outside in Y
        world.put(1, 3, 1, Material.DIRT);
        var q = new BlockJobQueue(1000, 10);
        var f = q.submit(new ScanReplaceJob(region, world, DIRECT,
                BlockMatcher.ofMaterial(m -> m == Material.OAK_PLANKS), world, Material.AIR, null));
        drain(q);
        assertTrue(f.isDone() && !f.isCompletedExceptionally());
        assertEquals(Material.AIR, world.get(0, 3, 0));
        assertEquals(Material.AIR, world.get(-20, 0, 20));
        assertEquals(Material.OAK_PLANKS, world.get(21, 3, 0));
        assertEquals(Material.OAK_PLANKS, world.get(0, 11, 0));
        assertEquals(Material.DIRT, world.get(1, 3, 1));
        assertEquals(2, world.writes);
    }

    @Test
    void takesOneSnapshotPerChunkCoveredByRegion() {
        var q = new BlockJobQueue(1000, 10);
        q.submit(new ScanReplaceJob(region, world, DIRECT, BlockMatcher.ofMaterial(m -> false), world, Material.AIR, null));
        drain(q);
        // x,z -20..20 => chunks -2..1 on each axis
        assertEquals(16, world.snapshotsTaken);
    }

    @Test
    void writesAreBudgetedPerTick() {
        for (int x = 0; x < 10; x++) {
            world.put(x, 5, 0, Material.WATER);
        }
        var q = new BlockJobQueue(4, 10);
        var f = q.submit(new ScanReplaceJob(region, world, DIRECT,
                BlockMatcher.ofMaterial(m -> m == Material.WATER), world, Material.LAVA, null));
        q.tick(); // takes snapshots, scans, and applies up to 4
        assertEquals(4, world.writes);
        q.tick();
        assertEquals(8, world.writes);
        q.tick();
        assertEquals(10, world.writes);
        assertTrue(f.isDone());
    }

    @Test
    void jobWaitsForAsyncScanWithoutBlocking() {
        world.put(0, 5, 0, Material.WATER);
        var pending = new ArrayList<Runnable>();
        Executor deferred = pending::add;
        var q = new BlockJobQueue(100, 10);
        var f = q.submit(new ScanReplaceJob(region, world, deferred,
                BlockMatcher.ofMaterial(m -> m == Material.WATER), world, Material.LAVA, null));
        q.tick();
        q.tick();
        assertFalse(f.isDone());
        assertEquals(0, world.writes);
        pending.forEach(Runnable::run);
        q.tick();
        assertTrue(f.isDone());
        assertEquals(Material.LAVA, world.get(0, 5, 0));
    }

    @Test
    void reportsEachReplacedPosition() {
        world.put(3, 4, -5, Material.OAK_LEAVES);
        world.put(-7, 2, 9, Material.OAK_LEAVES);
        var seen = new ArrayList<List<Integer>>();
        var q = new BlockJobQueue(100, 10);
        q.submit(new ScanReplaceJob(region, world, DIRECT, BlockMatcher.ofMaterial(m -> m == Material.OAK_LEAVES), world,
                Material.AIR, (x, y, z) -> seen.add(List.of(x, y, z))));
        drain(q);
        assertEquals(2, seen.size());
        assertTrue(seen.contains(List.of(3, 4, -5)));
        assertTrue(seen.contains(List.of(-7, 2, 9)));
    }

    @Test
    void reportFilterLimitsCallbackButNotReplacement() {
        world.put(0, 5, 0, Material.SUNFLOWER);
        world.putUpper(0, 6, 0, Material.SUNFLOWER);
        var q = new BlockJobQueue(100, 10);
        var seen = new ArrayList<List<Integer>>();
        BlockMatcher lowerOnly = (m, snap, x, y, z) -> !snap.isUpperHalf(x, y, z);
        q.submit(new ScanReplaceJob(region, world, DIRECT, BlockMatcher.ofMaterial(m -> m == Material.SUNFLOWER), world,
                Material.AIR, (x, y, z) -> seen.add(List.of(x, y, z)), lowerOnly));
        drain(q);
        assertEquals(List.of(List.of(0, 5, 0)), seen);
        assertEquals(Material.AIR, world.get(0, 5, 0));
        assertEquals(Material.AIR, world.get(0, 6, 0)); // upper half removed too
    }

    @Test
    void negativeCoordinatesSurvivePacking() {
        world.put(-17, 3, -49, Material.OAK_LEAVES);
        var seen = new ArrayList<List<Integer>>();
        var q = new BlockJobQueue(100, 10);
        var big = new Region(-50, 50, -50, 50, -64, 319);
        q.submit(new ScanReplaceJob(big, (cx, cz) -> world.capture(cx, cz), DIRECT,
                (m, s, x, y, z) -> m == Material.OAK_LEAVES && y < 10, world, Material.AIR,
                (x, y, z) -> seen.add(List.of(x, y, z))));
        drain(q);
        assertEquals(List.of(List.of(-17, 3, -49)), seen);
    }

    @Test
    void asyncScanFailureFailsTheJobFuture() {
        Executor failing = r -> {
            throw new java.util.concurrent.RejectedExecutionException("nope");
        };
        var q = new BlockJobQueue(100, 10);
        var f = q.submit(new ScanReplaceJob(region, world, failing, BlockMatcher.ofMaterial(m -> true), world,
                Material.AIR, null));
        q.tick();
        assertTrue(f.isCompletedExceptionally());
        assertTrue(q.isIdle());
    }
}
