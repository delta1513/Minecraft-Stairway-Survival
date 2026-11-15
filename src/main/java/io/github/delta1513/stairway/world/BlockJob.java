package io.github.delta1513.stairway.world;

/**
 * One bulk block job (PLAN.md §10). Driven on the main thread by {@link BlockJobQueue}, which
 * never runs two jobs at once.
 */
public interface BlockJob {

    /**
     * Does at most {@code budget} block operations and returns how many it did. A job that is
     * waiting on something (e.g. an async scan) returns 0 without being done; the queue then
     * ends the tick rather than letting later jobs overtake it.
     */
    int process(int budget);

    boolean isDone();

    /** Called when the queue is cleared before the job finished. */
    default void cancel() {}
}
