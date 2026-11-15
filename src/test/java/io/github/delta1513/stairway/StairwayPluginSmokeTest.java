package io.github.delta1513.stairway;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class StairwayPluginSmokeTest {

    private ServerMock server;
    private StairwayPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(StairwayPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginLoadsAndEnables() {
        assertTrue(plugin.isEnabled());
        assertSame(plugin, server.getPluginManager().getPlugin("StairwaySurvival"));
    }

    @Test
    void pluginExposesParsedDefaultConfig() {
        assertEquals(101, plugin.gameConfig().gameplayAreaSize());
        assertEquals("world", plugin.gameConfig().gameWorld());
    }
}
