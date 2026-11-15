package io.github.delta1513.stairway.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Resolves the configured material-to-amount starter kit into items (PLAN.md §5). */
public final class StarterKit {

    /** Config key standing for the team's bed colour. */
    public static final String TEAM_BED_KEY = "TEAM_BED";

    private StarterKit() {}

    public static List<ItemStack> resolve(Map<String, Integer> kit, Material teamBed, Logger log) {
        List<ItemStack> items = new ArrayList<>();
        for (Map.Entry<String, Integer> e : kit.entrySet()) {
            Material material = TEAM_BED_KEY.equalsIgnoreCase(e.getKey())
                    ? teamBed
                    : Material.matchMaterial(e.getKey().toUpperCase(Locale.ROOT));
            if (material == null || !material.isItem() || e.getValue() <= 0) {
                log.warning("Ignoring invalid starter-kit entry " + e.getKey() + ": " + e.getValue());
                continue;
            }
            items.add(new ItemStack(material, e.getValue()));
        }
        return items;
    }
}
