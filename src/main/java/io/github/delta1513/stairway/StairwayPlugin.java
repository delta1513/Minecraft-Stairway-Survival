package io.github.delta1513.stairway;

import io.github.delta1513.stairway.arena.Arena;
import io.github.delta1513.stairway.command.AssignMeCommand;
import io.github.delta1513.stairway.command.StairwayCommand;
import io.github.delta1513.stairway.config.GameConfig;
import io.github.delta1513.stairway.effects.RandomPenaltyApplier;
import io.github.delta1513.stairway.game.BukkitSurvivalHandler;
import io.github.delta1513.stairway.world.BlockJobQueue;
import io.github.delta1513.stairway.world.BukkitBlocks;
import java.util.List;
import java.util.Random;
import org.bukkit.entity.Player;
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
    private World world;
    private BlockJobQueue jobQueue;
    private SurvivalPhaseHandler survival;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        gameConfig = GameConfig.from(getConfig());

        world = getServer().getWorld(gameConfig.gameWorld());
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
        survival = createSurvivalHandler();
        game = new GameManager(gameConfig, arena, env, new BukkitGameScheduler(this), survival);
        tasks = new GameTasks(this, game, arena, gameConfig, world);
        game.addPhaseListener(tasks);

        env.applyWorldSettings(); // world border (§3.3) and keepInventory=false (§9)

        var pm = getServer().getPluginManager();
        pm.registerEvents(new JoinQuitListener(game), this);
        pm.registerEvents(new MoveLockListener(game), this);
        pm.registerEvents(new DeathListener(game, gameConfig.gameWorld()), this);

        AssignMeCommand assignMe = new AssignMeCommand(game, gameConfig.colours());
        StairwayCommand stairway = new StairwayCommand(getServer(), game, survival, gameConfig.colours());
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(assignMe.build(), "Join a team");
            event.registrar().register(stairway.build(), "Administer the Stairway Survival game");
        });

        // Players already online (e.g. after /reload) are treated as joining the lobby.
        getServer().getOnlinePlayers().forEach(p -> game.handleJoin(p.getUniqueId()));
    }

    @Override
    public void onDisable() {
        if (tasks != null) {
            tasks.stopAll();
        }
        if (survival != null) {
            // Cancels the layer clock, drops queued block jobs (an in-flight async scan only fills a
            // future nobody reads any more) and hides the boss bar.
            survival.stop();
        }
        if (plotPreparer != null) {
            plotPreparer.releaseTickets();
        }
    }

    /** Randomness for penalties and survival effects; overridable so tests can make it deterministic. */
    protected Random createRandom() {
        return new Random();
    }

    /** Wiring point for the death penalties (PLAN.md §9.1). */
    protected PenaltyApplier createPenaltyApplier() {
        return new RandomPenaltyApplier(createRandom(), gameConfig.thunderDurationSeconds());
    }

    /** Wiring point for the survival phase (PLAN.md §4.4). */
    protected SurvivalPhaseHandler createSurvivalHandler() {
        jobQueue = new BlockJobQueue(gameConfig.blockOpsPerTick(), gameConfig.entitySpawnsPerTick());
        return new BukkitSurvivalHandler(this, world, arena, gameConfig, jobQueue, this::aliveOnlineParticipants,
                new BukkitGameScheduler(this), createRandom(), BukkitBlocks.asyncExecutor(this));
    }

    private List<Player> aliveOnlineParticipants() {
        return getServer().getOnlinePlayers().stream()
                .filter(p -> game.isAliveParticipant(p.getUniqueId()))
                .map(p -> (Player) p)
                .toList();
    }

    public BlockJobQueue jobQueue() {
        return jobQueue;
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
