package io.github.delta1513.stairway.game;

/**
 * The SURVIVAL layer clock (PLAN.md §4.4), free of Bukkit so it is unit-testable with a manual
 * {@link GameScheduler}. Ticks once a second. Every {@code intervalSeconds}, starting one interval
 * after {@link #start()}, it asks its {@link Listener} to remove the next layers, advances
 * {@code nextLayerY} and fires an effect; once {@code nextLayerY} passes {@code topY} it stops.
 */
public final class SurvivalClock {

    private static final long TICKS_PER_SECOND = 20;

    /** Side effects of the clock. All calls happen on the scheduler's thread. */
    public interface Listener {
        /** Remove layers {@code fromY..toY} (inclusive). Called before {@link #fireEffect}. */
        void removeLayers(int fromY, int toY);

        /** Fire one random effect; {@code nextLayerY} is already advanced (the effect region starts there). */
        void fireEffect(int nextLayerY);

        /** Once per second (and once at start): current floor and seconds until the next removal. */
        void display(int nextLayerY, int secondsLeft);

        /** The clock has run out of layers and stopped itself. */
        void finished(int nextLayerY);
    }

    private final GameScheduler scheduler;
    private final int bottomY;
    private final int topY;
    private final int intervalSeconds;
    private final int layersPerInterval;
    private final Listener listener;

    private GameScheduler.Handle task;
    private int nextLayerY;
    private int secondsLeft;
    private boolean finished;

    public SurvivalClock(GameScheduler scheduler, int bottomY, int topY, int intervalSeconds, int layersPerInterval,
            Listener listener) {
        if (intervalSeconds < 1 || layersPerInterval < 1) {
            throw new IllegalArgumentException("interval and layers per interval must be positive");
        }
        this.scheduler = scheduler;
        this.bottomY = bottomY;
        this.topY = topY;
        this.intervalSeconds = intervalSeconds;
        this.layersPerInterval = layersPerInterval;
        this.listener = listener;
    }

    public void start() {
        stop();
        nextLayerY = bottomY;
        secondsLeft = intervalSeconds;
        finished = false;
        listener.display(nextLayerY, secondsLeft);
        task = scheduler.runRepeating(TICKS_PER_SECOND, TICKS_PER_SECOND, this::second);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public int nextLayerY() {
        return nextLayerY;
    }

    public int secondsLeft() {
        return secondsLeft;
    }

    public int intervalSeconds() {
        return intervalSeconds;
    }

    public boolean isRunning() {
        return task != null;
    }

    public boolean isFinished() {
        return finished;
    }

    private void second() {
        secondsLeft--;
        if (secondsLeft > 0) {
            listener.display(nextLayerY, secondsLeft);
            return;
        }
        int from = nextLayerY;
        int to = Math.min(topY, from + layersPerInterval - 1);
        listener.removeLayers(from, to);
        nextLayerY = to + 1;
        listener.fireEffect(nextLayerY);
        if (nextLayerY > topY) {
            finished = true;
            stop();
            listener.finished(nextLayerY);
        } else {
            secondsLeft = intervalSeconds;
            listener.display(nextLayerY, secondsLeft);
        }
    }
}
