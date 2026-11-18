package io.github.delta1513.stairway.task;

import io.github.delta1513.stairway.arena.Arena;
import io.github.delta1513.stairway.config.GameConfig;
import io.github.delta1513.stairway.game.GameManager;
import io.github.delta1513.stairway.game.Phase;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Per-tick checks on alive participants: the survival trigger (PLAN.md §4.3) and the
 * outside-the-gameplay-area fallback disqualification (§3.3, §8.1).
 */
public final class TriggerTask implements Runnable {

    private final GameManager game;
    private final Arena arena;
    private final GameConfig config;
    private final World world;

    public TriggerTask(GameManager game, Arena arena, GameConfig config, World world) {
        this.game = game;
        this.arena = arena;
        this.config = config;
        this.world = world;
    }

    @Override
    public void run() {
        for (Player player : world.getPlayers()) {
            if (!game.isAliveParticipant(player.getUniqueId())) {
                continue;
            }
            if (!arena.contains(Arena.blockOf(player.getX()), Arena.blockOf(player.getZ()))) {
                game.onLeftGameplayArea(player.getUniqueId());
                continue;
            }
            if (game.phase() == Phase.BUILD && player.getGameMode() == GameMode.SURVIVAL
                    && player.getY() >= config.survivalTriggerY()) {
game.startSurvival(player.getUniqueId());
            }
        }
    }
}
