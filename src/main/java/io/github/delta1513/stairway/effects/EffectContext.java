package io.github.delta1513.stairway.effects;

import io.github.delta1513.stairway.config.GameConfig;
import io.github.delta1513.stairway.world.BlockEditor;
import io.github.delta1513.stairway.world.BlockJobQueue;
import io.github.delta1513.stairway.world.BukkitBlocks;
import io.github.delta1513.stairway.world.Region;
import io.github.delta1513.stairway.world.SnapshotProvider;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Everything a {@link SurvivalEffect} needs, passed in by the caller so effects do not depend on
 * the game state machine.
 *
 * @param world                  the game world
 * @param region                 region the block effects act on: gameplay area × [nextLayerY, top]
 * @param participants           alive, online participants (the caller filters)
 * @param queue                  shared block job queue; also throttles mob spawns
 * @param editor                 main-thread block access
 * @param snapshots              chunk snapshot source for scanning
 * @param scanExecutor           runs scans off the main thread
 * @param thunderDurationSeconds {@code thunder-duration-seconds}
 * @param otherStones            materials of the "remove other stones" effect
 * @param random                 randomness for mob spawn placement
 */
public record EffectContext(
        World world,
        Region region,
        List<Player> participants,
        BlockJobQueue queue,
        BlockEditor editor,
        SnapshotProvider snapshots,
        Executor scanExecutor,
        int thunderDurationSeconds,
        Set<Material> otherStones,
        Random random) {

    public EffectContext {
        participants = List.copyOf(participants);
        otherStones = Set.copyOf(otherStones);
    }

    /** Production wiring: Bukkit block access, scans on the async scheduler, settings from config. */
    public static EffectContext of(World world, Region region, List<Player> participants, BlockJobQueue queue,
            Executor scanExecutor, GameConfig config, Random random) {
        BukkitBlocks blocks = new BukkitBlocks(world);
        return new EffectContext(world, region, participants, queue, blocks, blocks, scanExecutor,
                config.thunderDurationSeconds(), resolveMaterials(config.otherStones()), random);
    }

    /** Parses {@code other-stones}; unknown names are a configuration error, reported up front. */
    public static Set<Material> resolveMaterials(List<String> names) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material m = Material.matchMaterial(name);
            if (m == null || !m.isBlock()) {
                throw new IllegalArgumentException("Unknown block material in other-stones: " + name);
            }
            out.add(m);
        }
        return out.isEmpty() ? Set.of() : out;
    }
}
