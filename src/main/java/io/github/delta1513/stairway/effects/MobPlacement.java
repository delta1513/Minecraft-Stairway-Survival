package io.github.delta1513.stairway.effects;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** Spawns mobs close to a player (PLAN.md §10.2 #5 and #8). */
final class MobPlacement {

    /** Random columns tried before falling back to the player's own location. */
    private static final int ATTEMPTS = 12;

    private MobPlacement() {}

    /**
     * Spawns {@code count} mobs of {@code type} within {@code radius} blocks (per axis) of the
     * player, each on air above a solid block (two blocks of headroom); falls back to the
     * player's location when no such spot is found.
     */
    static void spawnNear(Player player, EntityType type, int count, int radius, Random random) {
        World world = player.getWorld();
        for (int i = 0; i < count; i++) {
            world.spawnEntity(findSpot(player, radius, random), type);
        }
    }

    static Location findSpot(Player player, int radius, Random random) {
        World world = player.getWorld();
        Location base = player.getLocation();
        int span = 2 * radius + 1;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int bx = base.getBlockX() + random.nextInt(span) - radius;
            int bz = base.getBlockZ() + random.nextInt(span) - radius;
            for (int y = base.getBlockY() + radius; y >= base.getBlockY() - radius; y--) {
                Block feet = world.getBlockAt(bx, y, bz);
                if (feet.getType().isAir()
                        && feet.getRelative(0, 1, 0).getType().isAir()
                        && feet.getRelative(0, -1, 0).getType().isSolid()) {
                    return new Location(world, bx + 0.5, y, bz + 0.5);
                }
            }
        }
        return base;
    }
}
