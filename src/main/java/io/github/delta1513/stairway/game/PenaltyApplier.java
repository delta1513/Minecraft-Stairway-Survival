package io.github.delta1513.stairway.game;

import java.util.function.Consumer;
import org.bukkit.entity.Player;

/** Picks one uniformly random death penalty (PLAN.md §9.1). Implemented by the effects package. */
@FunctionalInterface
public interface PenaltyApplier {

    /** Picks a penalty now; the returned action applies it to the player and broadcasts its message. */
    Consumer<Player> pickRandom();

    /** Default that does nothing, until death penalties are wired in. */
    PenaltyApplier NOOP = () -> player -> {};
}
