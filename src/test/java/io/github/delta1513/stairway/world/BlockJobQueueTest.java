package io.github.delta1513.stairway.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class BlockJobQueueTest {

    /** Job that consumes one op per unit of work and records the order of events. */
    private static final class CountingJob implements BlockJob {
        final String name;
        final List<String> log;
        int remaining;

        CountingJob(String name, int work, List<String> log) {
            this.name = name;
            this.remaining = work;
            this.log = log;
        }

        @Override
        public int process(int budget) {
            int n = Math.min(budget, remaining);
            remaining -= n;
            if (n > 0) {
                log.add(name + ":" + n);
            }
            return n;
        }

        @Override
        public boolean isDone() {
            return remaining == 0;
        }
    }

    @Test
    void jobIsSpreadOverTicksByBudget() {
        var q = new BlockJobQueue(100, 10);
        var log = new ArrayList<String>();
        CompletableFuture<Void> f = q.submit(new CountingJob("a", 250, log));
        q.tick();
        assertFalse(f.isDone());
        q.tick();
        assertFalse(f.isDone());
        q.tick();
        assertTrue(f.isDone());
        assertEquals(List.of("a:100", "a:100", "a:50"), log);
        assertTrue(q.isIdle());
    }

    @Test
    void jobsRunInFifoOrderAndNeverInterleave() {
        var q = new BlockJobQueue(100, 10);
        var log = new ArrayList<String>();
        q.submit(new CountingJob("a", 150, log));
        q.submit(new CountingJob("b", 150, log));
        q.tick();
        q.tick();
        q.tick();
        assertEquals(List.of("a:100", "a:50", "b:50", "b:100"), log);
    }

    @Test
    void totalOpsPerTickNeverExceedBudgetAcrossJobs() {
        var q = new BlockJobQueue(100, 10);
        var log = new ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            q.submit(new CountingJob("j" + i, 30, log));
        }
        q.tick();
        int total = log.stream().mapToInt(s -> Integer.parseInt(s.substring(s.indexOf(':') + 1))).sum();
        assertEquals(100, total);
    }

    @Test
    void waitingJobThatUsesNoBudgetDoesNotLetLaterJobsOvertake() {
        var q = new BlockJobQueue(100, 10);
        var log = new ArrayList<String>();
        var waiting = new BlockJob() {
            boolean ready;
            boolean done;

            @Override
            public int process(int budget) {
                if (!ready) {
                    return 0;
                }
                done = true;
                log.add("waiting-done");
                return 1;
            }

            @Override
            public boolean isDone() {
                return done;
            }
        };
        q.submit(waiting);
        q.submit(new CountingJob("b", 5, log));
        q.tick();
        q.tick();
        assertTrue(log.isEmpty(), "later job must not overtake a job waiting on async work");
        waiting.ready = true;
        q.tick();
        assertEquals(List.of("waiting-done", "b:5"), log);
    }

    @Test
    void failingJobCompletesExceptionallyAndQueueContinues() {
        var q = new BlockJobQueue(100, 10);
        var log = new ArrayList<String>();
        BlockJob bad = new BlockJob() {
            @Override
            public int process(int budget) {
                throw new IllegalStateException("boom");
            }

            @Override
            public boolean isDone() {
                return false;
            }
        };
        CompletableFuture<Void> f = q.submit(bad);
        q.submit(new CountingJob("b", 5, log));
        q.tick();
        assertTrue(f.isCompletedExceptionally());
        assertEquals(List.of("b:5"), log);
    }

    @Test
    void spawnsAreThrottledPerTickAndKeepOrder() {
        var q = new BlockJobQueue(100, 3);
        var spawned = new ArrayList<Integer>();
        for (int i = 0; i < 8; i++) {
            int id = i;
            q.queueSpawn(() -> spawned.add(id));
        }
        assertEquals(8, q.pendingSpawns());
        q.tick();
        assertEquals(List.of(0, 1, 2), spawned);
        q.tick();
        q.tick();
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), spawned);
        assertTrue(q.isIdle());
    }

    @Test
    void spawnBudgetIsIndependentOfBlockBudget() {
        var q = new BlockJobQueue(10, 2);
        var log = new ArrayList<String>();
        q.submit(new CountingJob("a", 100, log));
        var spawned = new ArrayList<Integer>();
        q.queueSpawn(() -> spawned.add(1));
        q.queueSpawn(() -> spawned.add(2));
        q.queueSpawn(() -> spawned.add(3));
        q.tick();
        assertEquals(List.of("a:10"), log);
        assertEquals(List.of(1, 2), spawned);
    }

    @Test
    void failingSpawnDoesNotBlockOthers() {
        var q = new BlockJobQueue(10, 5);
        var spawned = new ArrayList<Integer>();
        q.queueSpawn(() -> {
            throw new IllegalStateException("x");
        });
        q.queueSpawn(() -> spawned.add(1));
        q.tick();
        assertEquals(List.of(1), spawned);
    }

    @Test
    void clearCancelsPendingWork() {
        var q = new BlockJobQueue(10, 1);
        CompletableFuture<Void> f = q.submit(new CountingJob("a", 100, new ArrayList<>()));
        q.queueSpawn(() -> fail("must not run"));
        q.clear();
        assertTrue(f.isCancelled());
        assertTrue(q.isIdle());
        q.tick();
    }

    @Test
    void rejectsNonPositiveBudgets() {
        assertThrows(IllegalArgumentException.class, () -> new BlockJobQueue(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new BlockJobQueue(1, 0));
    }

    @Test
    void layerRemovalSetsEveryBlockInTwoLayersToAirOverSeveralTicks() {
        var world = new FakeWorld();
        var region = new Region(0, 4, 0, 4, -64, -63); // 50 blocks
        var q = new BlockJobQueue(20, 10);
        CompletableFuture<Void> f = q.submit(new LayerRemovalJob(region, world));
        q.tick();
        assertEquals(20, world.writes);
        assertFalse(f.isDone());
        q.tick();
        q.tick();
        assertTrue(f.isDone());
        assertEquals(50, world.writes);
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                for (int y = -64; y <= -63; y++) {
                    assertEquals(Material.AIR, world.get(x, y, z));
                }
                assertEquals(Material.STONE, world.get(x, -62, z));
            }
        }
    }

    @Test
    void layerRemovalOnEmptyRegionCompletesImmediately() {
        var q = new BlockJobQueue(20, 10);
        var f = q.submit(new LayerRemovalJob(new Region(0, 4, 0, 4, 320, 319), new FakeWorld()));
        q.tick();
        assertTrue(f.isDone());
    }
}
