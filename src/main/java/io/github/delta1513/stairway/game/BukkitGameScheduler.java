package io.github.delta1513.stairway.game;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** {@link GameScheduler} backed by the Bukkit scheduler (main thread). */
public final class BukkitGameScheduler implements GameScheduler {

    private final Plugin plugin;

    public BukkitGameScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Handle runLater(long delayTicks, Runnable task) {
        BukkitTask t = plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
        return t::cancel;
    }

    @Override
    public Handle runRepeating(long delayTicks, long periodTicks, Runnable task) {
        BukkitTask t = plugin.getServer().getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return t::cancel;
    }
}
