package io.github.delta1513.stairway.game;

/**
 * Hook for the SURVIVAL phase world changes (layer clock, boss bar, random effects; PLAN.md §4.4).
 * {@link GameManager} calls {@link #start()} on entering SURVIVAL and {@link #stop()} when the game
 * ends or is reset while SURVIVAL was running.
 */
public interface SurvivalPhaseHandler {

    void start();

    void stop();

    /** The floor of the remaining arena while the layer clock runs; empty otherwise. */
    default java.util.OptionalInt nextLayerY() {
        return java.util.OptionalInt.empty();
    }

    /** Default that does nothing, until the survival milestone is wired in. */
    SurvivalPhaseHandler NOOP = new SurvivalPhaseHandler() {
        @Override
        public void start() {}

        @Override
        public void stop() {}
    };
}
