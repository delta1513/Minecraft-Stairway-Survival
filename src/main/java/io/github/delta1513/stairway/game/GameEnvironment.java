package io.github.delta1513.stairway.game;

import io.github.delta1513.stairway.arena.Plot;
import java.util.Collection;
import java.util.UUID;

/**
 * Everything the {@link GameManager} needs from the server. Keeping it behind an interface keeps the
 * state machine unit-testable.
 */
public interface GameEnvironment {

    Collection<UUID> onlinePlayers();

    /** Display name for a player, also for offline players. */
    String nameOf(UUID player);

    void broadcast(String message);

    /** Adventure mode at the spawn surface (PLAN.md §4.1). */
    void sendToLobby(UUID player);

    /** Chunk tickets and the starter chest for every active plot (PLAN.md §5). */
    void preparePlots(Collection<Plot> activePlots);

    /** Drops the chunk tickets again. Safe to call repeatedly. */
    void releasePlots();

    /** Teleport to the plot spawn and make invulnerable (PLAN.md §4.2). */
    void enterCountdown(UUID player, Plot plot);

    void showCountdown(int secondsLeft);

    /** BUILD treatment (PLAN.md §4.3); teleports to the plot first when {@code teleport} is set. */
    void enterBuild(UUID player, Plot plot, boolean teleport);

    /** Spectator mode. For a player outside the game world, also teleports to the spawn surface. */
    void setSpectator(UUID player);

    /**
     * Broadcasts a flavour message from {@code messages.json} (PLAN.md §15).
     *
     * @param section the message section, e.g. {@code countdown} or {@code effects.DEATH_FEATHERS}
     * @param target  the player the message is about, or {@code null} for none
     */
    void message(String section, UUID target);

    /**
     * Picks one random death penalty for the player (PLAN.md §9.1) and returns the action that applies it and
     * broadcasts its effect message; run it when the wheel spin (§15.3) is over. The action does nothing if
     * the player is offline by then.
     */
    Runnable pickPenalty(UUID player);

    /** Winner announcement and title; {@code winner} is null when nobody is left. */
    void announceWinner(Plot winner);

    /** (Re)applies the world border and gamerules. */
    void applyWorldSettings();
}
