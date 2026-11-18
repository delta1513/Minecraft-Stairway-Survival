package io.github.delta1513.stairway.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import io.github.delta1513.stairway.effects.DeathPenalty;
import io.github.delta1513.stairway.effects.SurvivalEffect;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GameConfigTest {

    private static YamlConfiguration bundledDefaults() throws IOException, InvalidConfigurationException {
        try (InputStream in = GameConfigTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in, "config.yml resource must be shipped");
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    /** Mimics JavaPlugin#getConfig(): user file layered over the bundled defaults. */
    private static GameConfig withOverrides(String overridesYaml) throws Exception {
        YamlConfiguration user = new YamlConfiguration();
        user.load(new StringReader(overridesYaml));
        user.setDefaults(bundledDefaults());
        return GameConfig.from(user);
    }

    @Test
    void bundledDefaultsMatchPlanSection11_2() throws Exception {
        GameConfig c = GameConfig.from(bundledDefaults());

        assertEquals("world", c.gameWorld());
        assertEquals(2, c.minTeams());
        assertEquals(10, c.countdownSeconds());
        assertEquals(319, c.survivalTriggerY());
        assertEquals(60, c.layerIntervalSeconds());
        assertEquals(2, c.layersPerInterval());
        assertEquals(101, c.gameplayAreaSize());
        assertEquals(20, c.plotCircleRadius());
        assertEquals(1, c.zoneCheckIntervalTicks());
        assertEquals(10, c.particleIntervalTicks());
        assertEquals(64, c.particleViewDistance());
        assertEquals(300, c.thunderDurationSeconds());
        assertEquals(3, c.ringEffectSeconds());
        assertEquals(10, c.witherLevel());
        assertEquals(5, c.wheelSpinSeconds());
        assertEquals(30, c.lobbyReminderMinSeconds());
        assertEquals(60, c.lobbyReminderMaxSeconds());
        assertEquals(20_000, c.blockOpsPerTick());
        assertEquals(200, c.entitySpawnsPerTick());
        assertEquals(List.of("GRANITE", "DIORITE", "ANDESITE", "TUFF"), c.otherStones());
        assertEquals(List.of("red", "orange", "yellow", "lime", "blue", "purple"), c.colours());
    }

    @Test
    void ringEffectDefaultsKeepOrderAndLevels() throws Exception {
        GameConfig c = GameConfig.from(bundledDefaults());
        assertEquals(List.of("POISON", "BLINDNESS", "NAUSEA", "SLOWNESS", "MINING_FATIGUE", "HUNGER", "WEAKNESS"),
                List.copyOf(c.ringEffects().keySet()));
        assertEquals(Map.of("POISON", 2, "BLINDNESS", 1, "NAUSEA", 1, "SLOWNESS", 10, "MINING_FATIGUE", 10,
                "HUNGER", 1, "WEAKNESS", 5), c.ringEffects());
    }

    @Test
    void newKeysCanBeOverridden() throws Exception {
        GameConfig c = withOverrides("""
                ring-effect-seconds: 7
                ring-effects: {POISON: 1}
                wither-level: 3
                wheel-spin-seconds: 0
                lobby-reminder-min-seconds: 5
                lobby-reminder-max-seconds: 6
                """);
        assertEquals(7, c.ringEffectSeconds());
        assertEquals(Map.of("POISON", 1), c.ringEffects());
        assertEquals(3, c.witherLevel());
        assertEquals(0, c.wheelSpinSeconds());
        assertEquals(5, c.lobbyReminderMinSeconds());
        assertEquals(6, c.lobbyReminderMaxSeconds());
    }

    @Test
    void rejectsLobbyReminderMaxBelowMin() {
        assertThrows(IllegalArgumentException.class, () -> withOverrides(
                "lobby-reminder-min-seconds: 50\nlobby-reminder-max-seconds: 40"));
    }

    @Test
    void starterKitDefaultsKeepOrderAndAmounts() throws Exception {
        GameConfig c = GameConfig.from(bundledDefaults());
        Map<String, Integer> kit = c.starterKit();

        assertEquals(
                List.of("OAK_SAPLING", "BONE_MEAL", "WHEAT_SEEDS", "TEAM_BED", "BREAD", "WATER_BUCKET"),
                List.copyOf(kit.keySet()));
        assertEquals(4, kit.get("OAK_SAPLING"));
        assertEquals(4, kit.get("BONE_MEAL"));
        assertEquals(4, kit.get("WHEAT_SEEDS"));
        assertEquals(4, kit.get("TEAM_BED"));
        assertEquals(4, kit.get("BREAD"));
        assertEquals(1, kit.get("WATER_BUCKET"));
    }

    @Test
    void emptyUserFileFallsBackToDefaults() throws Exception {
        GameConfig c = withOverrides("{}");
        assertEquals(GameConfig.from(bundledDefaults()), c);
    }

    @Test
    void scalarOverridesAreApplied() throws Exception {
        GameConfig c = withOverrides("""
                game-world: arena
                min-teams: 3
                countdown-seconds: 5
                survival-trigger-y: 300
                layer-interval-seconds: 30
                plot-circle-radius: 25
                block-ops-per-tick: 1000
                """);

        assertEquals("arena", c.gameWorld());
        assertEquals(3, c.minTeams());
        assertEquals(5, c.countdownSeconds());
        assertEquals(300, c.survivalTriggerY());
        assertEquals(30, c.layerIntervalSeconds());
        assertEquals(25, c.plotCircleRadius());
        assertEquals(1000, c.blockOpsPerTick());
        // untouched keys keep their default
        assertEquals(101, c.gameplayAreaSize());
        assertEquals(64, c.particleViewDistance());
    }

    @Test
    void listAndKitOverridesReplaceDefaults() throws Exception {
        GameConfig c = withOverrides("""
                other-stones: [DEEPSLATE]
                colours: [a, b, c, d, e, f]
                starter-kit:
                  BREAD: 9
                  TEAM_BED: 1
                """);

        assertEquals(List.of("DEEPSLATE"), c.otherStones());
        assertEquals(List.of("a", "b", "c", "d", "e", "f"), c.colours());
        assertEquals(Map.of("BREAD", 9, "TEAM_BED", 1), c.starterKit());
    }

    @Test
    void rejectsEvenGameplayAreaSize() {
        assertThrows(IllegalArgumentException.class, () -> withOverrides("gameplay-area-size: 100"));
    }

    @Test
    void rejectsWrongNumberOfColours() {
        assertThrows(IllegalArgumentException.class, () -> withOverrides("colours: [red, blue]"));
    }

    @Test
    void starterKitIsImmutable() throws Exception {
        GameConfig c = GameConfig.from(bundledDefaults());
        assertThrows(UnsupportedOperationException.class, () -> c.starterKit().put("DIRT", 1));
        assertThrows(UnsupportedOperationException.class, () -> c.colours().add("pink"));
    }

    @Test
    void weightDefaultsAreOneForEveryConstant() throws Exception {
        GameConfig c = GameConfig.from(bundledDefaults());
        for (DeathPenalty d : DeathPenalty.values()) {
            assertEquals(1, c.deathPenaltyWeights().get(d), d.name());
        }
        for (SurvivalEffect e : SurvivalEffect.values()) {
            assertEquals(1, c.survivalEffectWeights().get(e), e.name());
        }
        assertEquals(DeathPenalty.values().length, c.deathPenaltyWeights().size());
        assertEquals(SurvivalEffect.values().length, c.survivalEffectWeights().size());
    }

    @Test
    void partialWeightOverrideKeepsOthersAtOne() throws Exception {
        GameConfig c = withOverrides("""
                death-penalty-weights:
                  NAUSEA: 5
                  FEATHERS: 0
                survival-effect-weights:
                  silverfish: 3
                """);
        assertEquals(5, c.deathPenaltyWeights().get(DeathPenalty.NAUSEA));
        assertEquals(0, c.deathPenaltyWeights().get(DeathPenalty.FEATHERS));
        assertEquals(1, c.deathPenaltyWeights().get(DeathPenalty.BLINDNESS));
        assertEquals(3, c.survivalEffectWeights().get(SurvivalEffect.SILVERFISH)); // case-insensitive key
        assertEquals(1, c.survivalEffectWeights().get(SurvivalEffect.REMOVE_ORES));
        assertNotNull(c.deathPenaltyPicker());
        assertNotNull(c.survivalEffectPicker());
    }

    @Test
    void rejectsBadWeights() {
        var unknown = assertThrows(IllegalArgumentException.class,
                () -> withOverrides("death-penalty-weights: {BOGUS: 2}"));
        assertTrue(unknown.getMessage().contains("BOGUS") && unknown.getMessage().contains("death-penalty-weights"));
        assertThrows(IllegalArgumentException.class, () -> withOverrides("death-penalty-weights: {NAUSEA: -1}"));
        assertThrows(IllegalArgumentException.class, () -> withOverrides("survival-effect-weights: {NAUSEA: 1}"));
        assertThrows(IllegalArgumentException.class, () -> withOverrides("death-penalty-weights: {NAUSEA: lots}"));
        assertThrows(IllegalArgumentException.class, () -> withOverrides("death-penalty-weights: {NAUSEA: 1.5}"));
        assertThrows(IllegalArgumentException.class, () -> withOverrides("""
                death-penalty-weights: {FEATHERS: 0, BLINDNESS: 0, NAUSEA: 0, WATER_ABOVE: 0, THUNDERSTORM: 0, JUMP_BOOST_X: 0}
                """));
    }
}
