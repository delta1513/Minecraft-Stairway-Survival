package io.github.delta1513.stairway.effects;

import org.bukkit.World;

/** Weather helpers shared by the survival "thunderstorm" effect and the death penalty. */
public final class Weather {

    private static final int TICKS_PER_SECOND = 20;

    private Weather() {}

    /** PLAN.md §10.2 #2: storm + thunder, both lasting {@code durationSeconds}. */
    public static void thunderstorm(World world, int durationSeconds) {
        int ticks = durationSeconds * TICKS_PER_SECOND;
        world.setStorm(true);
        world.setThundering(true);
        world.setWeatherDuration(ticks);
        world.setThunderDuration(ticks);
    }
}
