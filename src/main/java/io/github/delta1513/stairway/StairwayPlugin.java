package io.github.delta1513.stairway;

import io.github.delta1513.stairway.arena.Arena;
import io.github.delta1513.stairway.command.AssignMeCommand;
import io.github.delta1513.stairway.config.GameConfig;
import io.github.delta1513.stairway.game.BukkitGameEnvironment;
import io.github.delta1513.stairway.game.BukkitGameScheduler;
import io.github.delta1513.stairway.game.GameManager;
import io.github.delta1513.stairway.game.PenaltyApplier;
import io.github.delta1513.stairway.game.PlotPreparer;
import io.github.delta1513.stairway.game.SurvivalPhaseHandler;
import io.github.delta1513.stairway.listener.DeathListener;
import io.github.delta1513.stairway.listener.JoinQuitListener;
import io.github.delta1513.stairway.listener.MoveLockListener;
import io.github.delta1513.stairway.task.GameTasks;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class StairwayPlugin extends JavaPlugin {

    private GameConfig gameConfig;
    private Arena arena;
    private GameManager game;
    private GameTasks tasks;
    private PlotPreparer plotPreparer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        gameConfig = GameConfig.from(getConfig());

        World world = getServer().getWorld(gameConfig.gameWorld());
        if (world == null) {
            getLogger().severe("Game world '" + gameConfig.gameWorld() + "' not found; disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Location spawn = world.getSpawnLocation();
        arena = new Arena(spawn.getBlockX(), spawn.getBlockZ(), gameConfig.plotCircleRadius(),
                gameConfig.gameplayAreaSize(), gameConfig.colours());

        plotPreparer = new PlotPreparer(this, world, arena, gameConfig);
        BukkitGameEnvironment env = new BukkitGameEnvironment(getServer(), world, arena, gameConfig, plotPreparer,
                createPenaltyApplier());
        game = new GameManager(gameConfig, arena, env, new BukkitGameScheduler(this), createSurvivalHandler());
        tasks = new GameTasks(this, game, arena, gameConfig, world);
        game.addPhaseListener(tasks);

        env.applyWorldSettings(); // world border (§3.3) and keepInventory=false (§9)

        var pm = getServer().getPluginManager();
        pm.registerEvents(new JoinQuitListener(game), this);
        pm.registerEvents(new MoveLockListener(game), this);
        pm.registerEvents(new DeathListener(game, gameConfig.gameWorld()), this);

        AssignMeCommand assignMe = new AssignMeCommand(game, gameConfig.colours());
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(assignMe.build(), "Join a team"));

        // Players already online (e.g. after /reload) are treated as joining the lobby.
        getServer().getOnlinePlayers().forEach(p -> game.handleJoin(p.getUniqueId()));
    }

    @Override
    public void onDisable() {
        if (tasks != null) {
            tasks.stopAll();
        }
        if (plotPreparer != null) {
            plotPreparer.releaseTickets();
        }
    }

    /** Wiring point for the death penalties (milestone 6). */
    protected PenaltyApplier createPenaltyApplier() {
        return PenaltyApplier.NOOP;
    }

    /** Wiring point for the survival phase (milestone 5). */
    protected SurvivalPhaseHandler createSurvivalHandler() {
        return SurvivalPhaseHandler.NOOP;
    }

    public GameConfig gameConfig() {
        return gameConfig;
    }

    public PlotPreparer plotPreparer() {
        return plotPreparer;
    }

    public GameManager game() {
        return game;
    }

    public Arena arena() {
        return arena;
    }
}
