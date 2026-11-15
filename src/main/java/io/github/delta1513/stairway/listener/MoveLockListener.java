package io.github.delta1513.stairway.listener;

import io.github.delta1513.stairway.game.GameManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/** Cancels moves that change the block position while a player is locked (PLAN.md §4.2). */
public final class MoveLockListener implements Listener {

    private final GameManager game;

    public MoveLockListener(GameManager game) {
        this.game = game;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!game.isMoveLocked(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            event.setCancelled(true);
        }
    }
}
