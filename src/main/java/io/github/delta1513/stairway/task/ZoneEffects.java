package io.github.delta1513.stairway.task;

import io.github.delta1513.stairway.arena.Zone;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** The potion effects applied on entering the ring or the wild (PLAN.md §6). */
public final class ZoneEffects {

    /** Ring effect duration: 60 seconds. */
    static final int RING_DURATION_TICKS = 60 * 20;

    private ZoneEffects() {}

    /** Ring effects as (type, level); the amplifier is level - 1. */
    static PotionEffect[] ringEffects() {
        return new PotionEffect[] {
            ring(PotionEffectType.POISON, 1),
            ring(PotionEffectType.BLINDNESS, 1),
            ring(PotionEffectType.NAUSEA, 1),
            ring(PotionEffectType.SLOWNESS, 3),
            ring(PotionEffectType.MINING_FATIGUE, 5),
            ring(PotionEffectType.HUNGER, 1),
        };
    }

    static PotionEffect wither() {
        return new PotionEffect(PotionEffectType.WITHER, PotionEffect.INFINITE_DURATION, 5 - 1);
    }

    private static PotionEffect ring(PotionEffectType type, int level) {
        return new PotionEffect(type, RING_DURATION_TICKS, level - 1);
    }

    /** Applies what entering {@code zone} does; INSIDE does nothing. */
    public static void applyOnEntry(Player player, Zone zone) {
        switch (zone) {
            case RING -> {
                for (PotionEffect effect : ringEffects()) {
                    player.addPotionEffect(effect);
                }
            }
            case WILD -> player.addPotionEffect(wither());
            case INSIDE -> {
            }
        }
    }
}
