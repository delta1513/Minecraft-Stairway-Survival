package io.github.delta1513.stairway.command;

import static org.junit.jupiter.api.Assertions.*;

import io.github.delta1513.stairway.StairwayPlugin;
import io.github.delta1513.stairway.game.Phase;
import org.bukkit.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class StairwayCommandTest {

    private ServerMock server;
    private StairwayPlugin plugin;
    private PlayerMock admin;
    private PlayerMock alice;
    private PlayerMock bob;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        plugin = MockBukkit.load(StairwayPlugin.class);
        plugin.plotPreparer().useChunkTickets(new io.github.delta1513.stairway.game.PlotPreparer.ChunkTickets() {
            @Override
            public void add(org.bukkit.Chunk chunk, org.bukkit.plugin.Plugin owner) {}

            @Override
            public void remove(org.bukkit.Chunk chunk, org.bukkit.plugin.Plugin owner) {}
        });
        admin = server.addPlayer("Admin");
        admin.setOp(true);
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void reloadMessagesReReadsTheFileInTheDataFolder() throws Exception {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "messages.json");
        assertTrue(file.isFile(), "default messages.json is copied on first run");
        java.nio.file.Files.writeString(file.toPath(), "{\"countdown\": [\"custom countdown\"]}");
        drain(alice);
        admin.performCommand("stairway reloadmessages");
        assertTrue(drain(admin).contains("reloaded"));
        admin.performCommand("stairway assign Alice red");
        admin.performCommand("stairway assign Bob blue");
        admin.performCommand("stairway start");
        assertTrue(drain(alice).contains("custom countdown"));
    }

    @Test
    void reloadMessagesRequiresAdmin() {
        alice.performCommand("stairway reloadmessages");
        assertFalse(drain(alice).contains("reloaded"));
    }

    private String drain(PlayerMock p) {
        StringBuilder sb = new StringBuilder();
        String m;
        while ((m = p.nextMessage()) != null) {
            sb.append(m).append('\n');
        }
        return sb.toString();
    }

    private void startBuildViaCommands() {
        admin.performCommand("stairway assign Alice red");
        admin.performCommand("stairway assign Bob blue");
        admin.performCommand("stairway start");
        server.getScheduler().performTicks(20L * plugin.gameConfig().countdownSeconds() + 1);
        assertEquals(Phase.BUILD, plugin.game().phase());
    }

    @Test
    void nonAdminCannotUseTheCommand() {
        alice.performCommand("stairway assign Alice red");
        assertTrue(plugin.game().plotOf(alice.getUniqueId()).isEmpty());
    }

    @Test
    void assignAssignsAndOverrides() {
        admin.performCommand("stairway assign Alice red");
        assertEquals("red", plugin.game().plotOf(alice.getUniqueId()).orElseThrow().colour());
        admin.performCommand("stairway assign Alice blue");
        assertEquals("blue", plugin.game().plotOf(alice.getUniqueId()).orElseThrow().colour());
    }

    @Test
    void assignUnknownColourOrPlayerChangesNothingAndReports() {
        drain(admin);
        admin.performCommand("stairway assign Alice pink");
        assertTrue(plugin.game().plotOf(alice.getUniqueId()).isEmpty());
        assertTrue(drain(admin).toLowerCase().contains("colour"));
        admin.performCommand("stairway assign Nobody red");
        assertTrue(plugin.game().assignments().isEmpty());
        assertFalse(drain(admin).isEmpty());
    }

    @Test
    void startMakesUnassignedPlayersSpectatorsAndAssignedLocked() {
        admin.performCommand("stairway assign Alice red");
        admin.performCommand("stairway assign Bob blue");
        // Admin is unassigned, so the lobby start condition does not hold yet
        assertEquals(Phase.LOBBY, plugin.game().phase());
        admin.performCommand("stairway start");
        assertEquals(Phase.COUNTDOWN, plugin.game().phase());
        assertEquals(GameMode.SPECTATOR, admin.getGameMode());
        assertTrue(alice.isInvulnerable());
        assertNotEquals(GameMode.SPECTATOR, alice.getGameMode());
    }

    @Test
    void startWithNobodyAssignedIsRefused() {
        admin.performCommand("stairway start");
        assertEquals(Phase.LOBBY, plugin.game().phase());
    }

    @Test
    void survivalOnlyFromBuild() {
        admin.performCommand("stairway survival");
        assertEquals(Phase.LOBBY, plugin.game().phase());
        startBuildViaCommands();
        admin.performCommand("stairway survival");
        assertEquals(Phase.SURVIVAL, plugin.game().phase());
    }

    @Test
    void disqualifyAndStatus() {
        startBuildViaCommands();
        drain(admin);
        admin.performCommand("stairway status");
        String status = drain(admin);
        assertTrue(status.contains("BUILD"), status);
        assertTrue(status.contains("red"), status);
        assertTrue(status.contains("blue"), status);

        admin.performCommand("stairway disqualify Alice");
        assertEquals(GameMode.SPECTATOR, alice.getGameMode());
        assertEquals(Phase.ENDED, plugin.game().phase());
    }

    @Test
    void disqualifyWorksForOfflineParticipants() {
        startBuildViaCommands();
        alice.disconnect();
        admin.performCommand("stairway disqualify Alice");
        assertEquals(Phase.ENDED, plugin.game().phase());
    }

    @Test
    void resetReturnsToLobbyAndClearsAssignments() {
        admin.performCommand("stairway assign Alice red");
        admin.performCommand("stairway assign Bob blue");
        admin.performCommand("stairway start");
        admin.performCommand("stairway reset");
        assertEquals(Phase.LOBBY, plugin.game().phase());
        assertTrue(plugin.game().assignments().isEmpty());
        assertEquals(GameMode.ADVENTURE, alice.getGameMode());
        assertEquals(GameMode.ADVENTURE, admin.getGameMode());
    }

    @Test
    void statusInLobbyWorks() {
        drain(admin);
        admin.performCommand("stairway status");
        assertTrue(drain(admin).contains("LOBBY"));
    }
}
