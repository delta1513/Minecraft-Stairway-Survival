package io.github.delta1513.stairway.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.github.delta1513.stairway.StairwayPlugin;
import io.github.delta1513.stairway.arena.Plot;
import io.github.delta1513.stairway.game.Phase;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class GameFlowTest {

    private ServerMock server;
    private World world;
    private StairwayPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.load(StairwayPlugin.class);
        // MockBukkit does not implement chunk tickets; record them instead.
        plugin.plotPreparer().useChunkTickets(new io.github.delta1513.stairway.game.PlotPreparer.ChunkTickets() {
            @Override
            public void add(org.bukkit.Chunk chunk, org.bukkit.plugin.Plugin owner) {
                tickets.add(chunk);
            }

            @Override
            public void remove(org.bukkit.Chunk chunk, org.bukkit.plugin.Plugin owner) {
                tickets.remove(chunk);
            }
        });
    }

    private final java.util.Set<org.bukkit.Chunk> tickets = new java.util.HashSet<>();

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Plot plot(String colour) {
        return plugin.arena().plotByColour(colour).orElseThrow();
    }

    /** Two players on red and blue, through the countdown, in BUILD. */
    private PlayerMock[] startBuild() {
        PlayerMock a = server.addPlayer("Alice");
        PlayerMock b = server.addPlayer("Bob");
        plugin.game().assign(a.getUniqueId(), "red");
        plugin.game().assign(b.getUniqueId(), "blue");
        assertEquals(Phase.COUNTDOWN, plugin.game().phase());
        server.getScheduler().performTicks(20L * plugin.gameConfig().countdownSeconds() + 1);
        assertEquals(Phase.BUILD, plugin.game().phase());
        return new PlayerMock[] {a, b};
    }

    @Test
    void enableSetsWorldBorderAndKeepInventory() {
        assertEquals(101, world.getWorldBorder().getSize());
        assertEquals(world.getSpawnLocation().getBlockX() + 0.5, world.getWorldBorder().getCenter().getX());
        assertFalse(world.getGameRuleValue(GameRules.KEEP_INVENTORY));
    }

    @Test
    void lobbyJoinIsAdventureAtSpawn() {
        PlayerMock p = server.addPlayer();
        assertEquals(GameMode.ADVENTURE, p.getGameMode());
        assertEquals(world.getSpawnLocation().getBlockX(), p.getLocation().getBlockX());
        assertEquals(world.getSpawnLocation().getBlockZ(), p.getLocation().getBlockZ());
    }

    @Test
    void assignmeCommandAssignsAndRejectsSecondUse() {
        PlayerMock a = server.addPlayer("Alice");
        a.performCommand("assignme red");
        assertEquals("red", plugin.game().plotOf(a.getUniqueId()).orElseThrow().colour());
        a.performCommand("assignme blue");
        assertEquals("red", plugin.game().plotOf(a.getUniqueId()).orElseThrow().colour());
    }

    @Test
    void countdownTeleportsLocksAndBuildUnlocks() {
        PlayerMock a = server.addPlayer("Alice");
        PlayerMock b = server.addPlayer("Bob");
        plugin.game().assign(a.getUniqueId(), "red");
        plugin.game().assign(b.getUniqueId(), "blue");
        Plot red = plot("red");
        assertEquals(red.cx(), a.getLocation().getBlockX());
        assertEquals(red.cz(), a.getLocation().getBlockZ());
        assertTrue(a.isInvulnerable());

        Location from = a.getLocation();
        Location to = from.clone().add(2, 0, 0);
        PlayerMoveEvent move = new PlayerMoveEvent(a, from, to);
        server.getPluginManager().callEvent(move);
        assertTrue(move.isCancelled());

        PlayerMoveEvent look = new PlayerMoveEvent(a, from, from.clone().add(0.1, 0, 0));
        server.getPluginManager().callEvent(look);
        assertFalse(look.isCancelled());

        server.getScheduler().performTicks(20L * plugin.gameConfig().countdownSeconds() + 1);
        assertEquals(Phase.BUILD, plugin.game().phase());
        assertEquals(GameMode.SURVIVAL, a.getGameMode());
        assertFalse(a.isInvulnerable());
        PlayerMoveEvent free = new PlayerMoveEvent(a, from, to);
        server.getPluginManager().callEvent(free);
        assertFalse(free.isCancelled());
    }

    @Test
    void chunkTicketsCoverGameplayAreaAndAreReleasedAtEnd() {
        PlayerMock[] ps = startBuild();
        int minCx = plugin.arena().minX() >> 4, maxCx = plugin.arena().maxX() >> 4;
        int minCz = plugin.arena().minZ() >> 4, maxCz = plugin.arena().maxZ() >> 4;
        assertEquals((maxCx - minCx + 1) * (maxCz - minCz + 1), tickets.size());
        plugin.game().disqualify(ps[0].getUniqueId(), "x");
        assertEquals(Phase.ENDED, plugin.game().phase());
        assertTrue(tickets.isEmpty());
    }

    @Test
    void countdownPlacesChestWithKitOnActivePlotsOnly() {
        startBuild();
        Plot red = plot("red");
        int y = world.getHighestBlockYAt(red.chestX(), red.chestZ(), org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
        // chest sits on top of the old surface: surface is now the chest itself
        var block = world.getBlockAt(red.chestX(), y, red.chestZ());
        assertEquals(org.bukkit.Material.CHEST, block.getType());
        var inv = ((org.bukkit.block.Chest) block.getState()).getBlockInventory();
        assertTrue(inv.contains(org.bukkit.Material.RED_BED, 4));
        assertTrue(inv.contains(org.bukkit.Material.WATER_BUCKET, 1));
        Plot lime = plot("lime");
        int ly = world.getHighestBlockYAt(lime.chestX(), lime.chestZ(), org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
        assertNotEquals(org.bukkit.Material.CHEST, world.getBlockAt(lime.chestX(), ly, lime.chestZ()).getType());
    }

    @Test
    void ringEntryAppliesEffectsAndWildAppliesWither() {
        PlayerMock[] ps = startBuild();
        PlayerMock a = ps[0];
        Plot red = plot("red");
        server.getScheduler().performTicks(2);
        assertNull(a.getPotionEffect(PotionEffectType.POISON));

        a.teleport(new Location(world, red.cx() + 0.5 + 2, 70, red.cz() + 0.5)); // x offset 2 -> d = 1 -> ring
        server.getScheduler().performTicks(1);
        assertNotNull(a.getPotionEffect(PotionEffectType.POISON));
        assertEquals(1, a.getPotionEffect(PotionEffectType.POISON).getAmplifier());
        assertEquals(0, a.getPotionEffect(PotionEffectType.BLINDNESS).getAmplifier());
        assertEquals(0, a.getPotionEffect(PotionEffectType.NAUSEA).getAmplifier());
        assertEquals(9, a.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier());
        assertEquals(9, a.getPotionEffect(PotionEffectType.MINING_FATIGUE).getAmplifier());
        assertEquals(0, a.getPotionEffect(PotionEffectType.HUNGER).getAmplifier());
        assertEquals(4, a.getPotionEffect(PotionEffectType.WEAKNESS).getAmplifier());
        assertEquals(60, a.getPotionEffect(PotionEffectType.POISON).getDuration());
        assertNull(a.getPotionEffect(PotionEffectType.WITHER));

        a.teleport(new Location(world, red.cx() + 0.5 + 3, 70, red.cz() + 0.5)); // wild
        server.getScheduler().performTicks(1);
        var wither = a.getPotionEffect(PotionEffectType.WITHER);
        assertNotNull(wither);
        assertEquals(9, wither.getAmplifier());
        assertTrue(wither.isInfinite());
    }

    @Test
    void ringEffectsAreRefreshedAtFullDurationWhileStayingInRing() {
        PlayerMock[] ps = startBuild();
        PlayerMock a = ps[0];
        Plot red = plot("red");
        a.teleport(new Location(world, red.cx() + 0.5 + 2, 70, red.cz() + 0.5));
        server.getScheduler().performTicks(1);
        a.removePotionEffect(PotionEffectType.POISON);
        a.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SLOWNESS, 5, 0));
        server.getScheduler().performTicks(2);
        assertNotNull(a.getPotionEffect(PotionEffectType.POISON));
        assertEquals(60, a.getPotionEffect(PotionEffectType.POISON).getDuration());
        assertEquals(9, a.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier());
    }

    @Test
    void ringEffectsAreNotRefreshedOnceTheRingIsLeft() {
        PlayerMock[] ps = startBuild();
        PlayerMock a = ps[0];
        Plot red = plot("red");
        a.teleport(new Location(world, red.cx() + 0.5 + 2, 70, red.cz() + 0.5));
        server.getScheduler().performTicks(1);
        a.teleport(new Location(world, red.cx() + 0.5, 70, red.cz() + 0.5)); // inside the plot
        a.removePotionEffect(PotionEffectType.POISON);
        server.getScheduler().performTicks(5);
        assertNull(a.getPotionEffect(PotionEffectType.POISON));
    }

    @Test
    void ringAndWildBroadcastTheirMessagesOnTransitionsOnly() {
        PlayerMock[] ps = startBuild();
        PlayerMock a = ps[0];
        Plot red = plot("red");
        drain(a);
        a.teleport(new Location(world, red.cx() + 0.5 + 2, 70, red.cz() + 0.5));
        server.getScheduler().performTicks(5);
        String ring = drain(a);
        assertEquals(1, count(ring, "wandered into the ring"), ring);
        assertTrue(ring.contains("Alice"), ring);

        a.teleport(new Location(world, red.cx() + 0.5 + 3, 70, red.cz() + 0.5));
        server.getScheduler().performTicks(5);
        String wild = drain(a);
        assertEquals(1, count(wild, "stepped into the wild"), wild);
    }

    private static int count(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static String drain(PlayerMock p) {
        StringBuilder sb = new StringBuilder();
        String m;
        while ((m = p.nextMessage()) != null) {
            sb.append(m).append('\n');
        }
        return sb.toString();
    }

    @Test
    void reachingTriggerYStartsSurvivalAndStopsZoneTask() {
        PlayerMock[] ps = startBuild();
        PlayerMock a = ps[0];
        Plot red = plot("red");
        a.teleport(new Location(world, red.cx() + 0.5, 319.5, red.cz() + 0.5));
        server.getScheduler().performTicks(1);
        assertEquals(Phase.SURVIVAL, plugin.game().phase());
        assertTrue(server.getOnlinePlayers().stream().count() == 2);
        // zone effects no longer applied
        a.teleport(new Location(world, red.cx() + 0.5 + 2, 319.5, red.cz() + 0.5));
        server.getScheduler().performTicks(2);
        assertNull(a.getPotionEffect(PotionEffectType.POISON));
    }

    @Test
    void respawnWithoutBedDisqualifiesAndEndsGame() {
        PlayerMock[] ps = startBuild();
        PlayerMock a = ps[0];
        server.getPluginManager().callEvent(new PlayerRespawnEvent(a, a.getLocation(), false, false, false,
                PlayerRespawnEvent.RespawnReason.DEATH));
        server.getScheduler().performTicks(2);
        assertEquals(GameMode.SPECTATOR, a.getGameMode());
        assertEquals(Phase.ENDED, plugin.game().phase());
    }

    @Test
    void respawnAtBedKeepsPlayerInBuild() {
        PlayerMock[] ps = startBuild();
        PlayerMock a = ps[0];
        server.getPluginManager().callEvent(new PlayerRespawnEvent(a, a.getLocation(), true, false, false,
                PlayerRespawnEvent.RespawnReason.DEATH));
        server.getScheduler().performTicks(2);
        assertEquals(GameMode.SURVIVAL, a.getGameMode());
        assertEquals(Phase.BUILD, plugin.game().phase());
    }

    @Test
    void leavingTheOverworldDisqualifies() {
        PlayerMock[] ps = startBuild();
        PlayerMock a = ps[0];
        World nether = server.addSimpleWorld("world_nether");
        World from = a.getWorld();
        a.teleport(new Location(nether, 0, 70, 0));
        server.getPluginManager().callEvent(new PlayerChangedWorldEvent(a, from));
        assertEquals(GameMode.SPECTATOR, a.getGameMode());
        assertEquals(Phase.ENDED, plugin.game().phase());
        assertEquals(world, a.getWorld());
    }

    @Test
    void unassignedJoinerDuringBuildIsSpectator() {
        startBuild();
        PlayerMock c = server.addPlayer("Carol");
        assertEquals(GameMode.SPECTATOR, c.getGameMode());
    }

    @Test
    void escapingTheGameplayAreaDisqualifies() {
        PlayerMock[] ps = startBuild();
        PlayerMock a = ps[0];
        a.teleport(new Location(world, world.getSpawnLocation().getX() + 80, 70, world.getSpawnLocation().getZ()));
        server.getScheduler().performTicks(1);
        assertEquals(GameMode.SPECTATOR, a.getGameMode());
    }
}
