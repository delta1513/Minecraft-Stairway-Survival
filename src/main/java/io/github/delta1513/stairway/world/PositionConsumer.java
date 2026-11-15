package io.github.delta1513.stairway.world;

/** Receives block coordinates, e.g. to queue a mob spawn for each replaced block. */
@FunctionalInterface
public interface PositionConsumer {

    void accept(int x, int y, int z);
}
