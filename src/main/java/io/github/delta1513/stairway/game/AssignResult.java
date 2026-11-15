package io.github.delta1513.stairway.game;

/** Outcome of {@link GameManager#assign}. */
public enum AssignResult {
    ASSIGNED,
    ALREADY_ASSIGNED,
    NOT_IN_LOBBY,
    UNKNOWN_COLOUR
}
