package io.github.delta1513.stairway.game;

/**
 * Hook for the SURVIVAL phase world changes (layer clock, boss bar, random effects; PLAN.md §4.4).
 * {@link GameManager} calls {@link #start()} on entering SURVIVAL and {@link #stop()} when the game
 * ends or is reset while SURVIVAL was running.
 */
public interface SurvivalPhaseHandler {

    void start();

    void stop();

    /** Default that does nothing, until the survival milestone is wired in. */
    SurvivalPhaseHandler NOOP = new SurvivalPhaseHandler() {
        @Override
        public void start() {}

        @Override
        public void stop() {}
    };
}
