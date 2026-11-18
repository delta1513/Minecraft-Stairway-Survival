package io.github.delta1513.stairway.task;

import io.github.delta1513.stairway.arena.Arena;
import io.github.delta1513.stairway.arena.Zone;
import io.github.delta1513.stairway.arena.ZoneTracker;
import io.github.delta1513.stairway.game.GameManager;
import io.github.delta1513.stairway.messages.Messages;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Zone enforcement during BUILD (PLAN.md §6). Runs every {@code zone-check-interval-ticks}. */
public final class ZoneTask implements Runnable {

    private final GameManager game;
    private final Arena arena;
    private final World world;
    private final ZoneEffects effects;
    private final Messages messages;
    private final ZoneTracker tracker = new ZoneTracker();

    public ZoneTask(GameManager game, Arena arena, World world, ZoneEffects effects, Messages messages) {
        this.game = game;
        this.arena = arena;
        this.world = world;
        this.effects = effects;
        this.messages = messages;
    }

    @Override
    public void run() {
        for (Player player : world.getPlayers()) {
            if (!game.isAliveParticipant(player.getUniqueId()) || player.getGameMode() != GameMode.SURVIVAL) {
                continue;
            }
            Zone zone = arena.zoneOf(Arena.blockOf(player.getX()), Arena.blockOf(player.getZ()), game.activePlots());
            Optional<Zone> entered = tracker.update(player.getUniqueId(), zone);
            if (entered.isPresent()) {
                if (zone == Zone.RING) {
                    messages.broadcast("ring_entered", player.getName());
                } else if (zone == Zone.WILD) {
                    messages.broadcast("wither_doomed", player.getName());
                    effects.applyWild(player);
                }
            }
            if (zone == Zone.RING) {
                // Refreshed on every check, so they wear off ring-effect-seconds after leaving the ring.
                effects.applyRing(player);
            }
        }
    }
}
