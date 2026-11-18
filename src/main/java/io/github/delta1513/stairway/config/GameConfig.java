package io.github.delta1513.stairway.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Typed, immutable view of {@code config.yml} (PLAN.md §11.2). Defaults live in the bundled
 * {@code config.yml}; the section passed to {@link #from} is expected to have them layered
 * underneath (as {@code JavaPlugin#getConfig()} does), so there are no magic numbers here.
 */
public record GameConfig(
        String gameWorld,
        int minTeams,
        int countdownSeconds,
        int survivalTriggerY,
        int layerIntervalSeconds,
        int layersPerInterval,
        int gameplayAreaSize,
        int plotCircleRadius,
        int zoneCheckIntervalTicks,
        int particleIntervalTicks,
        int particleViewDistance,
        int thunderDurationSeconds,
        int ringEffectSeconds,
        Map<String, Integer> ringEffects,
        int witherLevel,
        int wheelSpinSeconds,
        int lobbyReminderMinSeconds,
        int lobbyReminderMaxSeconds,
        int blockOpsPerTick,
        int entitySpawnsPerTick,
        List<String> otherStones,
        List<String> colours,
        Map<String, Integer> starterKit) {

    /** Number of colours/plots required by the geometry (PLAN.md §3.1). */
    private static final int REQUIRED_COLOURS = 6;

    private static final String STARTER_KIT_KEY = "starter-kit";
    private static final String RING_EFFECTS_KEY = "ring-effects";

    public GameConfig {
        if (gameplayAreaSize < 1 || gameplayAreaSize % 2 == 0) {
            throw new IllegalArgumentException("gameplay-area-size must be a positive odd number");
        }
        if (colours.size() != REQUIRED_COLOURS) {
            throw new IllegalArgumentException("colours must list exactly " + REQUIRED_COLOURS + " names");
        }
        if (ringEffectSeconds < 1 || witherLevel < 1 || wheelSpinSeconds < 0) {
            throw new IllegalArgumentException(
                    "ring-effect-seconds and wither-level must be positive, wheel-spin-seconds not negative");
        }
        for (Map.Entry<String, Integer> e : ringEffects.entrySet()) {
            if (e.getValue() < 1) {
                throw new IllegalArgumentException("ring-effects level for " + e.getKey() + " must be at least 1");
            }
        }
        if (lobbyReminderMinSeconds < 1 || lobbyReminderMaxSeconds < lobbyReminderMinSeconds) {
            throw new IllegalArgumentException(
                    "lobby-reminder-min-seconds must be positive and not above lobby-reminder-max-seconds");
        }
        otherStones = List.copyOf(otherStones);
        ringEffects = Collections.unmodifiableMap(new LinkedHashMap<>(ringEffects));
        colours = List.copyOf(colours);
        starterKit = Collections.unmodifiableMap(new LinkedHashMap<>(starterKit));
    }

    /**
     * {@code getConfigurationSection} does not surface defaults for a missing section, so fall back
     * to the defaults explicitly when the user's file does not define the kit at all. A kit that is
     * defined replaces the default kit entirely.
     */
    private static ConfigurationSection mapSection(ConfigurationSection c, String key) {
        if (c.contains(key, true)) {
            return c.getConfigurationSection(key);
        }
        Configuration root = c.getRoot();
        Configuration defaults = root == null ? null : root.getDefaults();
        return defaults == null ? null : defaults.getConfigurationSection(key);
    }

    private static Map<String, Integer> intMap(ConfigurationSection c, String key) {
        Map<String, Integer> map = new LinkedHashMap<>();
        ConfigurationSection section = mapSection(c, key);
        if (section != null) {
            for (String k : section.getKeys(false)) {
                map.put(k, section.getInt(k));
            }
        }
        return map;
    }

    public static GameConfig from(ConfigurationSection c) {
        return new GameConfig(
                c.getString("game-world"),
                c.getInt("min-teams"),
                c.getInt("countdown-seconds"),
                c.getInt("survival-trigger-y"),
                c.getInt("layer-interval-seconds"),
                c.getInt("layers-per-interval"),
                c.getInt("gameplay-area-size"),
                c.getInt("plot-circle-radius"),
                c.getInt("zone-check-interval-ticks"),
                c.getInt("particle-interval-ticks"),
                c.getInt("particle-view-distance"),
                c.getInt("thunder-duration-seconds"),
                c.getInt("ring-effect-seconds"),
                intMap(c, RING_EFFECTS_KEY),
                c.getInt("wither-level"),
                c.getInt("wheel-spin-seconds"),
                c.getInt("lobby-reminder-min-seconds"),
                c.getInt("lobby-reminder-max-seconds"),
                c.getInt("block-ops-per-tick"),
                c.getInt("entity-spawns-per-tick"),
                c.getStringList("other-stones"),
                c.getStringList("colours"),
                intMap(c, STARTER_KIT_KEY));
    }
}
