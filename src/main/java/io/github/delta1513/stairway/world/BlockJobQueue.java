package io.github.delta1513.stairway.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The single shared FIFO queue of bulk block jobs (PLAN.md §10), plus a throttled queue of
 * entity spawns. {@link #tick()} must be called once per server tick on the main thread
 * ({@link #start(Plugin)} schedules that). Each tick:
 * <ul>
 *   <li>at most {@code blockOpsPerTick} block operations are done, always on the head job only,
 *       so jobs never overlap; a job that is waiting (returns 0 and is not done) ends the tick;</li>
 *   <li>at most {@code entitySpawnsPerTick} queued spawns run, in FIFO order.</li>
 * </ul>
 * Pure Java apart from {@link #start}; the budgeting is unit-tested without a server.
 */
public final class BlockJobQueue {

    private static final Logger LOG = Logger.getLogger(BlockJobQueue.class.getName());

    private record Entry(BlockJob job, CompletableFuture<Void> future) {}

    private final int blockOpsPerTick;
    private final int entitySpawnsPerTick;
    private final Deque<Entry> jobs = new ArrayDeque<>();
    private final Deque<Runnable> spawns = new ArrayDeque<>();
    private BukkitTask task;

    public BlockJobQueue(int blockOpsPerTick, int entitySpawnsPerTick) {
        if (blockOpsPerTick < 1 || entitySpawnsPerTick < 1) {
            throw new IllegalArgumentException("Budgets must be positive");
        }
        this.blockOpsPerTick = blockOpsPerTick;
        this.entitySpawnsPerTick = entitySpawnsPerTick;
    }

    /** Appends a job; the future completes when it finishes (exceptionally if it threw). */
    public CompletableFuture<Void> submit(BlockJob job) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        jobs.addLast(new Entry(job, future));
        return future;
    }

    /** Queues a spawn to run within the per-tick entity spawn budget. */
    public void queueSpawn(Runnable spawn) {
        spawns.addLast(spawn);
    }

    public void tick() {
        runJobs();
        runSpawns();
    }

    private void runJobs() {
        int budget = blockOpsPerTick;
        while (budget > 0 && !jobs.isEmpty()) {
            Entry head = jobs.peekFirst();
            int used;
            try {
                used = head.job.process(budget);
                if (head.job.isDone()) {
                    jobs.pollFirst();
                    head.future.complete(null);
                }
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Block job failed", e);
                jobs.pollFirst();
                head.future.completeExceptionally(e);
                continue;
            }
            budget -= used;
            if (used == 0 && !head.future.isDone()) {
                return; // waiting on async work; keep FIFO order and do not spin
            }
        }
    }

    private void runSpawns() {
        for (int i = 0; i < entitySpawnsPerTick && !spawns.isEmpty(); i++) {
            Runnable spawn = spawns.pollFirst();
            try {
                spawn.run();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Queued spawn failed", e);
            }
        }
    }

    public boolean isIdle() {
        return jobs.isEmpty() && spawns.isEmpty();
    }

    public int pendingJobs() {
        return jobs.size();
    }

    public int pendingSpawns() {
        return spawns.size();
    }

    /** Drops all pending work (e.g. on reset). Pending job futures are cancelled. */
    public void clear() {
        for (Entry e : jobs) {
            e.job.cancel();
            e.future.cancel(false);
        }
        jobs.clear();
        spawns.clear();
    }

    /** Runs {@link #tick()} every server tick until {@link #stop()}. */
    public void start(Plugin plugin) {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
