package io.github.delta1513.stairway.game;

import java.util.Locale;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.Material;

/** Maps a configured colour name to its bed material (PLAN.md §3.1) and particle colour (§7). */
public final class TeamColours {

    private static final Map<String, Color> COLOURS = Map.of(
            "red", Color.RED,
            "orange", Color.ORANGE,
            "yellow", Color.YELLOW,
            "lime", Color.LIME,
            "blue", Color.BLUE,
            "purple", Color.PURPLE);

    private TeamColours() {}

    /** {@code <COLOUR>_BED}, or a white bed for a custom colour name without a matching bed. */
    public static Material bed(String colour) {
        Material m = Material.matchMaterial(colour.toUpperCase(Locale.ROOT) + "_BED");
        return m == null ? Material.WHITE_BED : m;
    }

    public static Color dust(String colour) {
        return COLOURS.getOrDefault(colour.toLowerCase(Locale.ROOT), Color.WHITE);
    }
}
