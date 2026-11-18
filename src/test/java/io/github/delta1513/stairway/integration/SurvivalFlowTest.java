package io.github.delta1513.stairway.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.github.delta1513.stairway.StairwayPlugin;
import io.github.delta1513.stairway.arena.Arena;
import io.github.delta1513.stairway.effects.SurvivalEffect;
import io.github.delta1513.stairway.game.Phase;
import io.github.delta1513.stairway.game.PlotPreparer;
import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * End to end: two teams, countdown, build, forced survival, one layer interval, win by disqualification.
 *
 * <p>The floor starts at {@code world.getMinHeight()} (-64 in a real overworld; MockBukkit's simple world
 * may differ), so the assertions use that instead of a literal -64. The random effect is pinned to
 * "Hunger + nausea", which needs no mob spawning that MockBukkit may not support.
 */
class SurvivalFlowTest {

    /** The production plugin with a deterministic random: every pick is the same effect index. */
    public static class FixedRandomPlugin extends StairwayPlugin {
        @Override
        protected Random createRandom() {
            return new Random() {
                @Override
                public int nextInt(int bound) {
                    return SurvivalEffect.HUNGER_NAUSEA.ordinal() % bound;
                }
            };
        }
    }

    private ServerMock server;
    private World world;
    private StairwayPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.load(FixedRandomPlugin.class);
        plugin.plotPreparer().useChunkTickets(new PlotPreparer.ChunkTickets() {
            @Override
            public void add(Chunk chunk, Plugin owner) {}

            @Override
            public void remove(Chunk chunk, Plugin owner) {}
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void fillLayer(int y, Material m) {
        Arena a = plugin.arena();
        for (int x = a.minX(); x <= a.maxX(); x++) {
            for (int z = a.minZ(); z <= a.maxZ(); z++) {
                world.getBlockAt(x, y, z).setType(m, false);
            }
        }
    }

    private boolean layerIs(int y, Material m) {
        Arena a = plugin.arena();
        for (int x = a.minX(); x <= a.maxX(); x++) {
            for (int z = a.minZ(); z <= a.maxZ(); z++) {
                if (world.getBlockAt(x, y, z).getType() != m) {
                    return false;
                }
            }
        }
        return true;
    }

    private void console(String cmd) {
        server.dispatchCommand(server.getConsoleSender(), cmd);
    }

    private String allMessages(PlayerMock p) {
        StringBuilder sb = new StringBuilder();
        String m;
        while ((m = p.nextMessage()) != null) {
            sb.append(m).append('\n');
        }
        return sb.toString();
    }

    @Test
    void fullGameFromLobbyToWinner() {
        int bottom = world.getMinHeight();
        fillLayer(bottom, Material.BEDROCK);
        fillLayer(bottom + 1, Material.STONE);
        fillLayer(bottom + 2, Material.STONE);

        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        // The subclass has no plugin.yml of its own in MockBukkit, so /assignme's default permission is not
        // registered; assign through the manager (the command itself is covered in GameFlowTest).
        plugin.game().assign(alice.getUniqueId(), "red");
        plugin.game().assign(bob.getUniqueId(), "blue");
        assertEquals(Phase.COUNTDOWN, plugin.game().phase());
        server.getScheduler().performTicks(20L * plugin.gameConfig().countdownSeconds() + 1);
        assertEquals(Phase.BUILD, plugin.game().phase());

        console("stairway survival");
        assertEquals(Phase.SURVIVAL, plugin.game().phase());
        assertEquals(bottom, plugin.survivalHandler().nextLayerY().orElseThrow());

        // Nothing is removed before one full interval has passed.
        int interval = plugin.gameConfig().layerIntervalSeconds();
        server.getScheduler().performTicks(20L * interval - 5);
        assertTrue(layerIs(bottom, Material.BEDROCK));
        assertTrue(layerIs(bottom + 1, Material.STONE));

        // Past the interval: two layers gone (the removal is spread over a couple of ticks), effect fired.
        allMessages(alice);
        server.getScheduler().performTicks(10);
        String spin = allMessages(alice);
        assertTrue(spin.contains("Wheel of Misfortune"), spin);
        assertTrue(layerIs(bottom, Material.AIR), "bottom layer should be air");
        assertTrue(layerIs(bottom + 1, Material.AIR), "second layer should be air");
        assertTrue(layerIs(bottom + 2, Material.STONE), "third layer must be untouched");
        assertEquals(bottom + 2, plugin.survivalHandler().nextLayerY().orElseThrow());
        // The effect itself waits for the wheel spin.
        assertNull(alice.getPotionEffect(PotionEffectType.NAUSEA));
        server.getScheduler().performTicks(20L * plugin.gameConfig().wheelSpinSeconds());
        String msgs = allMessages(alice);
        assertTrue(msgs.contains("tummy is growling"), msgs);
        assertFalse(msgs.contains("Survival effect:"), msgs);
        assertNotNull(alice.getPotionEffect(PotionEffectType.NAUSEA));
        assertNotNull(bob.getPotionEffect(PotionEffectType.NAUSEA));
        assertTrue(plugin.jobQueue().isIdle());

        // Disqualify one team -> ENDED with a winner broadcast; the clock and queue stop.
        console("stairway disqualify Bob");
        assertEquals(Phase.ENDED, plugin.game().phase());
        assertEquals(GameMode.SPECTATOR, bob.getGameMode());
        assertTrue(allMessages(alice).contains("red wins!"));
        assertTrue(plugin.survivalHandler().nextLayerY().isEmpty());
        server.getScheduler().performTicks(20L * interval * 2);
        assertTrue(layerIs(bottom + 2, Material.STONE), "no more layers after the game ended");
    }

    @Test
    void pendingEffectIsCancelledWhenTheGameEnds() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        plugin.game().assign(alice.getUniqueId(), "red");
        plugin.game().assign(bob.getUniqueId(), "blue");
        server.getScheduler().performTicks(20L * plugin.gameConfig().countdownSeconds() + 1);
        console("stairway survival");
        server.getScheduler().performTicks(20L * plugin.gameConfig().layerIntervalSeconds() + 5);
        console("stairway disqualify Bob");
        assertEquals(Phase.ENDED, plugin.game().phase());
        server.getScheduler().performTicks(20L * plugin.gameConfig().wheelSpinSeconds() + 5);
        assertNull(alice.getPotionEffect(PotionEffectType.NAUSEA));
    }

    @Test
    void resetDuringSurvivalStopsClockAndDropsPendingJobs() {
        int bottom = world.getMinHeight();
        fillLayer(bottom, Material.STONE);
        fillLayer(bottom + 1, Material.STONE);
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        plugin.game().assign(alice.getUniqueId(), "red");
        plugin.game().assign(bob.getUniqueId(), "blue");
        server.getScheduler().performTicks(20L * plugin.gameConfig().countdownSeconds() + 1);
        console("stairway survival");
        // Fill the queue with work and reset before it is processed.
        plugin.jobQueue().submit(new io.github.delta1513.stairway.world.LayerRemovalJob(
                io.github.delta1513.stairway.world.Region.gameplayArea(plugin.arena(), bottom, bottom + 1),
                new io.github.delta1513.stairway.world.BukkitBlocks(world)));
        assertFalse(plugin.jobQueue().isIdle());

        console("stairway reset");
        assertEquals(Phase.LOBBY, plugin.game().phase());
        assertTrue(plugin.jobQueue().isIdle());
        assertTrue(plugin.survivalHandler().nextLayerY().isEmpty());
        server.getScheduler().performTicks(20L * plugin.gameConfig().layerIntervalSeconds() * 2);
        assertTrue(layerIs(bottom, Material.STONE), "nothing may be removed after reset");
    }

    @Test
    void survivalWithClockLeftRunningIsStoppedOnPluginDisable() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        plugin.game().assign(alice.getUniqueId(), "red");
        plugin.game().assign(bob.getUniqueId(), "blue");
        server.getScheduler().performTicks(20L * plugin.gameConfig().countdownSeconds() + 1);
        console("stairway survival");
        server.getPluginManager().disablePlugin(plugin);
        assertTrue(plugin.survivalHandler().nextLayerY().isEmpty());
        // must not throw
        server.getScheduler().performTicks(20L * plugin.gameConfig().layerIntervalSeconds() + 5);
    }
}
