package io.github.delta1513.stairway.world;

import java.util.function.Predicate;
import org.bukkit.Material;

/** Decides, off the main thread, whether a scanned block is to be replaced. */
@FunctionalInterface
public interface BlockMatcher {

    boolean matches(Material type, BlockSnapshot snapshot, int x, int y, int z);

    /** Matches purely by material. */
    static BlockMatcher ofMaterial(Predicate<Material> predicate) {
        return (type, snapshot, x, y, z) -> predicate.test(type);
    }
}
