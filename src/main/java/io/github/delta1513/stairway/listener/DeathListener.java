package io.github.delta1513.stairway.listener;

import io.github.delta1513.stairway.game.GameManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Death, respawn and world-change triggers (PLAN.md §8.1, §9). */
public final class DeathListener implements Listener {

    private final GameManager game;
    private final String gameWorld;

    public DeathListener(GameManager game, String gameWorld) {
        this.game = game;
        this.gameWorld = gameWorld;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        game.onDeath(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        game.onRespawn(event.getPlayer().getUniqueId(), event.isBedSpawn());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        game.onWorldChange(event.getPlayer().getUniqueId(), event.getPlayer().getWorld().getName().equals(gameWorld));
    }
}
