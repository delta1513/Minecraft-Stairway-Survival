package io.github.delta1513.stairway.game;

final class CountingSurvivalHandler implements SurvivalPhaseHandler {
    int starts;
    int stops;

    @Override
    public void start() {
        starts++;
    }

    @Override
    public void stop() {
        stops++;
    }
}
