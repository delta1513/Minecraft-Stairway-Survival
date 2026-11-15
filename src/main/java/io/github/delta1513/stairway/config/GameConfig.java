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
        int blockOpsPerTick,
        int entitySpawnsPerTick,
        List<String> otherStones,
        List<String> colours,
        Map<String, Integer> starterKit) {

    /** Number of colours/plots required by the geometry (PLAN.md §3.1). */
    private static final int REQUIRED_COLOURS = 6;

    private static final String STARTER_KIT_KEY = "starter-kit";

    public GameConfig {
        if (gameplayAreaSize < 1 || gameplayAreaSize % 2 == 0) {
            throw new IllegalArgumentException("gameplay-area-size must be a positive odd number");
        }
        if (colours.size() != REQUIRED_COLOURS) {
            throw new IllegalArgumentException("colours must list exactly " + REQUIRED_COLOURS + " names");
        }
        otherStones = List.copyOf(otherStones);
        colours = List.copyOf(colours);
        starterKit = Collections.unmodifiableMap(new LinkedHashMap<>(starterKit));
    }

    /**
     * {@code getConfigurationSection} does not surface defaults for a missing section, so fall back
     * to the defaults explicitly when the user's file does not define the kit at all. A kit that is
     * defined replaces the default kit entirely.
     */
    private static ConfigurationSection starterKitSection(ConfigurationSection c) {
        if (c.contains(STARTER_KIT_KEY, true)) {
            return c.getConfigurationSection(STARTER_KIT_KEY);
        }
        Configuration root = c.getRoot();
        Configuration defaults = root == null ? null : root.getDefaults();
        return defaults == null ? null : defaults.getConfigurationSection(STARTER_KIT_KEY);
    }

    public static GameConfig from(ConfigurationSection c) {
        Map<String, Integer> kit = new LinkedHashMap<>();
        ConfigurationSection kitSection = starterKitSection(c);
        if (kitSection != null) {
            for (String key : kitSection.getKeys(false)) {
                kit.put(key, kitSection.getInt(key));
            }
        }
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
                c.getInt("block-ops-per-tick"),
                c.getInt("entity-spawns-per-tick"),
                c.getStringList("other-stones"),
                c.getStringList("colours"),
                kit);
    }
}
