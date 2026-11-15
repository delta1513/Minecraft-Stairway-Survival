package io.github.delta1513.stairway.game;

import java.util.ArrayList;
import java.util.List;

/** Deterministic tick scheduler for unit tests. */
final class ManualScheduler implements GameScheduler {

    private static final class Task implements Handle {
        long nextRun;
        final long period; // 0 = one-shot
        final Runnable body;
        boolean cancelled;

        Task(long nextRun, long period, Runnable body) {
            this.nextRun = nextRun;
            this.period = period;
            this.body = body;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private final List<Task> tasks = new ArrayList<>();
    private long now;

    @Override
    public Handle runLater(long delayTicks, Runnable task) {
        Task t = new Task(now + delayTicks, 0, task);
        tasks.add(t);
        return t;
    }

    @Override
    public Handle runRepeating(long delayTicks, long periodTicks, Runnable task) {
        Task t = new Task(now + delayTicks, periodTicks, task);
        tasks.add(t);
        return t;
    }

    void tick(int ticks) {
        for (int i = 0; i < ticks; i++) {
            now++;
            for (Task t : new ArrayList<>(tasks)) {
                if (!t.cancelled && t.nextRun == now) {
                    t.body.run();
                    if (t.period > 0) {
                        t.nextRun += t.period;
                    } else {
                        t.cancelled = true;
                    }
                }
            }
            tasks.removeIf(t -> t.cancelled);
        }
    }

    int pending() {
        return (int) tasks.stream().filter(t -> !t.cancelled).count();
    }
}
