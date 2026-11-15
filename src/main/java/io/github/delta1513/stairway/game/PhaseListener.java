package io.github.delta1513.stairway.game;

/** Notified by {@link GameManager} after every phase change. */
@FunctionalInterface
public interface PhaseListener {
    void phaseChanged(Phase from, Phase to);
}
