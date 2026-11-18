package io.github.delta1513.stairway.task;

import io.github.delta1513.stairway.config.GameConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** The potion effects of the ring and the wild (PLAN.md §6), built from the config. */
public final class ZoneEffects {

    private static final long TICKS_PER_SECOND = 20;

    private final List<PotionEffect> ringEffects;
    private final PotionEffect wither;

    /** @throws IllegalArgumentException if {@code ring-effects} names an unknown potion effect type */
    public ZoneEffects(GameConfig config) {
        int duration = Math.toIntExact(config.ringEffectSeconds() * TICKS_PER_SECOND);
        List<PotionEffect> effects = new ArrayList<>();
        for (Map.Entry<String, Integer> e : config.ringEffects().entrySet()) {
            effects.add(new PotionEffect(resolve(e.getKey()), duration, e.getValue() - 1));
        }
        this.ringEffects = List.copyOf(effects);
        this.wither = new PotionEffect(PotionEffectType.WITHER, PotionEffect.INFINITE_DURATION,
                config.witherLevel() - 1);
    }

    private static PotionEffectType resolve(String key) {
        PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
        if (type == null) {
            throw new IllegalArgumentException("ring-effects: unknown potion effect type '" + key + "'");
        }
        return type;
    }

    /** The ring effects at full duration; the amplifier is level - 1. */
    List<PotionEffect> ringEffects() {
        return ringEffects;
    }

    PotionEffect wither() {
        return wither;
    }

    /** (Re)applies the ring effects at full duration, overwriting running ones. */
    public void applyRing(Player player) {
        for (PotionEffect effect : ringEffects) {
            player.addPotionEffect(effect);
        }
    }

    /** Wither: infinite, never removed by the plugin. */
    public void applyWild(Player player) {
        player.addPotionEffect(wither);
    }
}
