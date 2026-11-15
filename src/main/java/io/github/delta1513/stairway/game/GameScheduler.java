package io.github.delta1513.stairway.game;

/** Minimal tick scheduler so the state machine can be tested without a server. */
public interface GameScheduler {

    /** Handle to cancel a scheduled task. */
    interface Handle {
        void cancel();
    }

    Handle runLater(long delayTicks, Runnable task);

    Handle runRepeating(long delayTicks, long periodTicks, Runnable task);
}
