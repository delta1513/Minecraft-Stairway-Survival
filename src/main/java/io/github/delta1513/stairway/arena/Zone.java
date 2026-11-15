package io.github.delta1513.stairway.arena;

/** Classification of a block column relative to the active plots (PLAN.md §3.2). */
public enum Zone {
    /** Inside some active plot. */
    INSIDE,
    /** The first block outside an active plot. */
    RING,
    /** Anywhere further out. */
    WILD
}
