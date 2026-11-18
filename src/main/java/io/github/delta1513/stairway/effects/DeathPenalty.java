package io.github.delta1513.stairway.effects;

import java.util.function.BiConsumer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The six build-phase death penalties (PLAN.md §9.1), applied one tick after a bed respawn.
 * Picked by weight (config {@code death-penalty-weights}, see {@link WeightedPicker}).
 * {@link #apply} takes the player and the configured thunder duration (for the thunderstorm).
 */
public enum DeathPenalty {
    FEATHERS("Inventory full of feathers", (p, thunderSeconds) -> {
        ItemStack[] storage = p.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            if (storage[i] == null || storage[i].getType().isAir()) {
                storage[i] = new ItemStack(Material.FEATHER, Material.FEATHER.getMaxStackSize());
            }
        }
        p.getInventory().setStorageContents(storage);
    }),
    BLINDNESS("Blindness (3 min)", (p, s) ->
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, DeathPenalty.LONG_EFFECT_TICKS, 0))),
    NAUSEA("Nausea (3 min)", (p, s) ->
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, DeathPenalty.LONG_EFFECT_TICKS, 0))),
    WATER_ABOVE("Water above you", (p, s) -> {
        Block above = p.getEyeLocation().getBlock().getRelative(0, 1, 0);
        if (above.getType().isAir() || above.isReplaceable()) {
            above.setType(Material.WATER); // physics on, so the source actually flows
        }
    }),
    THUNDERSTORM("Thunderstorm", (p, thunderSeconds) -> Weather.thunderstorm(p.getWorld(), thunderSeconds)),
    JUMP_BOOST_X("Jump Boost X (1 min)", (p, s) -> p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,
            DeathPenalty.SHORT_EFFECT_TICKS, DeathPenalty.JUMP_BOOST_X_AMPLIFIER)));

    /** 3 minutes. */
    public static final int LONG_EFFECT_TICKS = 180 * 20;
    /** 1 minute. */
    public static final int SHORT_EFFECT_TICKS = 60 * 20;
    /** Jump Boost X (level 10). */
    public static final int JUMP_BOOST_X_AMPLIFIER = 9;

    private final String displayName;
    private final BiConsumer<Player, Integer> action;

    DeathPenalty(String displayName, BiConsumer<Player, Integer> action) {
        this.displayName = displayName;
        this.action = action;
    }

    /**
     * Key of this effect in {@code messages.json} under {@code effects} (PLAN.md §15.2): the constant name
     * with the {@code DEATH_} prefix; the {@code _X} of "Jump Boost X" is dropped.
     */
    public String messageKey() {
        String name = name();
        return "DEATH_" + (name.endsWith("_X") ? name.substring(0, name.length() - 2) : name);
    }

    /** Text for telling the player (and chat) which penalty they got. */
    public String displayName() {
        return displayName;
    }

    /**
     * @param thunderDurationSeconds {@code thunder-duration-seconds}, used by {@link #THUNDERSTORM}
     */
    public void apply(Player player, int thunderDurationSeconds) {
        action.accept(player, thunderDurationSeconds);
    }
}
