package io.github.delta1513.stairway.task;

import io.github.delta1513.stairway.arena.Arena;
import io.github.delta1513.stairway.config.GameConfig;
import io.github.delta1513.stairway.game.GameManager;
import io.github.delta1513.stairway.game.Phase;
import io.github.delta1513.stairway.game.PhaseListener;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Starts and stops the repeating tasks as the game changes phase: zone and particle tasks run only
 * in BUILD; the trigger task runs in BUILD and SURVIVAL.
 */
public final class GameTasks implements PhaseListener {

    private final Plugin plugin;
    private final GameManager game;
    private final Arena arena;
    private final GameConfig config;
    private final World world;
    private final List<BukkitTask> buildTasks = new ArrayList<>();
    private BukkitTask triggerTask;

    public GameTasks(Plugin plugin, GameManager game, Arena arena, GameConfig config, World world) {
        this.plugin = plugin;
        this.game = game;
        this.arena = arena;
        this.config = config;
        this.world = world;
    }

    @Override
    public void phaseChanged(Phase from, Phase to) {
        if (to == Phase.BUILD) {
            var scheduler = plugin.getServer().getScheduler();
            buildTasks.add(scheduler.runTaskTimer(plugin, new ZoneTask(game, arena, world), 0L,
                    config.zoneCheckIntervalTicks()));
            buildTasks.add(scheduler.runTaskTimer(plugin, new ParticleTask(game, config, world), 0L,
                    config.particleIntervalTicks()));
            triggerTask = scheduler.runTaskTimer(plugin, new TriggerTask(game, arena, config, world), 0L, 1L);
        } else if (to == Phase.SURVIVAL) {
            stopBuildTasks();
        } else {
            stopAll();
        }
    }

    private void stopBuildTasks() {
        buildTasks.forEach(BukkitTask::cancel);
        buildTasks.clear();
    }

    public void stopAll() {
        stopBuildTasks();
        if (triggerTask != null) {
            triggerTask.cancel();
            triggerTask = null;
        }
    }
}
