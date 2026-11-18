package io.github.delta1513.stairway.game;

import io.github.delta1513.stairway.arena.Arena;
import io.github.delta1513.stairway.config.GameConfig;
import io.github.delta1513.stairway.effects.EffectContext;
import io.github.delta1513.stairway.effects.SurvivalEffect;
import io.github.delta1513.stairway.effects.WeightedPicker;
import io.github.delta1513.stairway.world.BlockJobQueue;
import io.github.delta1513.stairway.world.BukkitBlocks;
import io.github.delta1513.stairway.world.LayerRemovalJob;
import io.github.delta1513.stairway.world.Region;
import io.github.delta1513.stairway.messages.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * SURVIVAL phase (PLAN.md §4.4): runs the {@link SurvivalClock}, removes layers through the shared
 * {@link BlockJobQueue}, fires a random {@link SurvivalEffect} each interval and shows the boss bar.
 * The queue is started on {@link #start()} and cleared and stopped on {@link #stop()}.
 */
public final class BukkitSurvivalHandler implements SurvivalPhaseHandler {

    private static final long TICKS_PER_SECOND = 20;

    private final Plugin plugin;
    private final World world;
    private final Arena arena;
    private final GameConfig config;
    private final BlockJobQueue queue;
    private final Supplier<List<Player>> aliveParticipants;
    private final Random random;
    private final WeightedPicker<SurvivalEffect> effectPicker;
    private final Executor scanExecutor;
    private final SurvivalClock clock;
    private final BossBar bossBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED,
            BossBar.Overlay.PROGRESS);
    private boolean barShown;
    private final GameScheduler scheduler;
    private final Messages messages;
    private final List<GameScheduler.Handle> pendingEffects = new ArrayList<>();

    /**
     * @param aliveParticipants alive, online participants at the moment of the call
     * @param scheduler         drives the clock
     */
    public BukkitSurvivalHandler(Plugin plugin, World world, Arena arena, GameConfig config, BlockJobQueue queue,
            Supplier<List<Player>> aliveParticipants, GameScheduler scheduler, Random random, Executor scanExecutor,
            Messages messages) {
        this.scheduler = scheduler;
        this.messages = messages;
        this.plugin = plugin;
        this.world = world;
        this.arena = arena;
        this.config = config;
        this.queue = queue;
        this.aliveParticipants = aliveParticipants;
        this.random = random;
        this.effectPicker = config.survivalEffectPicker();
        this.scanExecutor = scanExecutor;
        // Floor starts at the bottom of the world (-64 in the overworld); the top is the last buildable layer.
        this.clock = new SurvivalClock(scheduler, world.getMinHeight(), world.getMaxHeight() - 1,
                config.layerIntervalSeconds(), config.layersPerInterval(), new ClockListener());
        // Fail fast on a bad other-stones list instead of at the first effect.
        EffectContext.resolveMaterials(config.otherStones());
    }

    @Override
    public void start() {
        queue.clear();
        queue.start(plugin);
        clock.start();
    }

    @Override
    public void stop() {
        clock.stop();
        pendingEffects.forEach(GameScheduler.Handle::cancel);
        pendingEffects.clear();
        queue.clear();
        queue.stop();
        hideBar();
    }

    @Override
    public OptionalInt nextLayerY() {
        return clock.isRunning() ? OptionalInt.of(clock.nextLayerY()) : OptionalInt.empty();
    }

    private void showBar(Component text, float progress) {
        bossBar.name(text);
        bossBar.progress(Math.max(0f, Math.min(1f, progress)));
        Server server = plugin.getServer();
        // Re-adding is idempotent, and picks up players who joined since the last second.
        for (Player p : server.getOnlinePlayers()) {
            p.showBossBar(bossBar);
        }
        barShown = true;
    }

    private void hideBar() {
        if (!barShown) {
            return;
        }
        barShown = false;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.hideBossBar(bossBar);
        }
    }

    private void applyEffect(SurvivalEffect effect, Region region) {
        EffectContext ctx = EffectContext.of(world, region, aliveParticipants.get(), queue, scanExecutor, config,
                random);
        try {
            effect.apply(ctx);
        } catch (RuntimeException e) {
            // Never let a failing effect stop the clock.
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Survival effect failed: " + effect, e);
        }
        messages.broadcast(Messages.effectSection(effect.messageKey()), null);
    }

    private final class ClockListener implements SurvivalClock.Listener {
        @Override
        public void removeLayers(int fromY, int toY) {
            queue.submit(new LayerRemovalJob(Region.gameplayArea(arena, fromY, toY), new BukkitBlocks(world)));
        }

        @Override
        public void fireEffect(int nextLayerY) {
            // Wheel spin (PLAN.md §15.3): pick now, announce the spin, apply the effect later.
            SurvivalEffect effect = effectPicker.pick(random);
            Region region = Region.gameplayArea(arena, nextLayerY, world.getMaxHeight() - 1);
            messages.broadcast("wheel_spin", null);
            GameScheduler.Handle[] self = new GameScheduler.Handle[1];
            self[0] = scheduler.runLater(config.wheelSpinSeconds() * TICKS_PER_SECOND, () -> {
                pendingEffects.remove(self[0]);
                applyEffect(effect, region);
            });
            pendingEffects.add(self[0]);
        }

        @Override
        public void display(int nextLayerY, int secondsLeft) {
            showBar(Component.text("Floor collapsing — Y < " + nextLayerY + " gone — next in " + secondsLeft
                    + "s"), secondsLeft / (float) clock.intervalSeconds());
        }

        @Override
        public void finished(int nextLayerY) {
            showBar(Component.text("Floor collapsing — Y < " + nextLayerY + " gone — the arena is gone"), 0f);
        }
    }
}
