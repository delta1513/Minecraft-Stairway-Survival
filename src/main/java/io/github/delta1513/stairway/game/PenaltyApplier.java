package io.github.delta1513.stairway.game;

import org.bukkit.entity.Player;

/** Applies one uniformly random death penalty (PLAN.md §9.1). Implemented by the effects package. */
@FunctionalInterface
public interface PenaltyApplier {

    void applyRandom(Player player);

    /** Default that does nothing, until death penalties are wired in. */
    PenaltyApplier NOOP = player -> {};
}
