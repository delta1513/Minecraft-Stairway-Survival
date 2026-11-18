package io.github.delta1513.stairway.effects;

import io.github.delta1513.stairway.world.BlockMatcher;
import io.github.delta1513.stairway.world.ScanReplaceJob;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The 13 random survival-phase effects (PLAN.md §10.2), in table order. {@link #apply} either acts
 * immediately (time, weather, potions, mob spawns) or submits a bulk job to the shared
 * {@code BlockJobQueue}, which then changes the blocks over the following ticks.
 */
public enum SurvivalEffect {
    PERMANENT_NIGHT("Permanent night", ctx -> {
        ctx.world().setTime(SurvivalEffect.NIGHT_TIME);
        ctx.world().setGameRule(GameRules.ADVANCE_TIME, false);
    }),
    THUNDERSTORM("Thunderstorm", ctx -> Weather.thunderstorm(ctx.world(), ctx.thunderDurationSeconds())),
    REMOVE_PLANKS("Remove planks", ctx -> replaceAll(ctx, m -> Tag.PLANKS.isTagged(m), Material.AIR)),
    REMOVE_ORES("Remove ores", ctx -> replaceAll(ctx, SurvivalEffect::isOre, Material.AIR)),
    ZOMBIE_SKELETON("Zombie + skeleton", ctx -> {
        for (Player p : ctx.participants()) {
            MobPlacement.spawnNear(p, EntityType.ZOMBIE, 1, SurvivalEffect.ZOMBIE_SKELETON_RADIUS, ctx.random());
            MobPlacement.spawnNear(p, EntityType.SKELETON, 1, SurvivalEffect.ZOMBIE_SKELETON_RADIUS, ctx.random());
        }
    }),
    REMOVE_OTHER_STONES("Remove other stones", ctx -> replaceAll(ctx, m -> ctx.otherStones().contains(m), Material.AIR)),
    REMOVE_GRASS("Remove grass", ctx -> replaceAll(ctx, m -> m == Material.GRASS_BLOCK, Material.AIR)),
    SILVERFISH("Silverfish", ctx -> {
        for (Player p : ctx.participants()) {
            MobPlacement.spawnNear(p, EntityType.SILVERFISH, SurvivalEffect.SILVERFISH_COUNT, SurvivalEffect.SILVERFISH_RADIUS, ctx.random());
        }
    }),
    LEAVES_TO_CHICKENS("Leaves to chickens", ctx -> replaceAndSpawn(ctx, m -> Tag.LEAVES.isTagged(m), null, EntityType.CHICKEN)),
    FLOWERS_TO_CREEPERS("Flowers to creepers", ctx -> replaceAndSpawn(ctx, m -> Tag.FLOWERS.isTagged(m),
            // two-tall flowers: spawn only for the lower half (the upper half is removed silently)
            (type, snap, x, y, z) -> !snap.isUpperHalf(x, y, z), EntityType.CREEPER)),
    WATER_TO_LAVA("Water to lava", ctx -> replaceAll(ctx, m -> m == Material.WATER || m == Material.BUBBLE_COLUMN,
            Material.LAVA)),
    HUNGER_NAUSEA("Hunger + nausea", ctx -> {
        for (Player p : ctx.participants()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, SurvivalEffect.TIMED_EFFECT_TICKS, SurvivalEffect.HUNGER_AMPLIFIER));
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, SurvivalEffect.TIMED_EFFECT_TICKS, 0));
        }
    }),
    JUMP_BOOST_X("Jump Boost X", ctx -> {
        for (Player p : ctx.participants()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, SurvivalEffect.TIMED_EFFECT_TICKS, SurvivalEffect.JUMP_BOOST_X_AMPLIFIER));
        }
    });

    /** World time of "permanent night" (PLAN.md §10.2 #1). */
    public static final long NIGHT_TIME = 18000;
    /** Duration of the potion-based effects: 60 s. */
    public static final int TIMED_EFFECT_TICKS = 60 * 20;
    /** Hunger III. */
    public static final int HUNGER_AMPLIFIER = 2;
    /** Jump Boost X (level 10). */
    public static final int JUMP_BOOST_X_AMPLIFIER = 9;

    private static final int ZOMBIE_SKELETON_RADIUS = 2;
    private static final int SILVERFISH_COUNT = 10;
    private static final int SILVERFISH_RADIUS = 3;

    private final String displayName;
    private final Consumer<EffectContext> action;

    SurvivalEffect(String displayName, Consumer<EffectContext> action) {
        this.displayName = displayName;
        this.action = action;
    }

    /**
     * Key of this effect in {@code messages.json} under {@code effects} (PLAN.md §15.2): the constant name
     * with the {@code SURVIVAL_} prefix; the {@code _X} of "Jump Boost X" is dropped.
     */
    public String messageKey() {
        String name = name();
        return "SURVIVAL_" + (name.endsWith("_X") ? name.substring(0, name.length() - 2) : name);
    }

    /** Name for the chat broadcast when the effect fires. */
    public String displayName() {
        return displayName;
    }

    /** Applies the effect. */
    public void apply(EffectContext ctx) {
        action.accept(ctx);
    }

    /** Uniform pick with replacement (PLAN.md §10.2). */
    public static SurvivalEffect random(Random random) {
        SurvivalEffect[] all = values();
        return all[random.nextInt(all.length)];
    }

    static boolean isOre(Material m) {
        return m.name().endsWith("_ORE");
    }

    private static void replaceAll(EffectContext ctx, Predicate<Material> filter, Material replacement) {
        ctx.queue().submit(new ScanReplaceJob(ctx.region(), ctx.snapshots(), ctx.scanExecutor(),
                BlockMatcher.ofMaterial(filter), ctx.editor(), replacement, null));
    }

    /**
     * Removes matching blocks and queues one mob at the centre of each (throttled by the queue's
     * entity spawn budget; no cap on the count).
     */
    private static void replaceAndSpawn(EffectContext ctx, Predicate<Material> filter, BlockMatcher reported,
            EntityType mob) {
        ctx.queue().submit(new ScanReplaceJob(ctx.region(), ctx.snapshots(), ctx.scanExecutor(),
                BlockMatcher.ofMaterial(filter), ctx.editor(), Material.AIR,
                (x, y, z) -> ctx.queue().queueSpawn(
                        () -> ctx.world().spawnEntity(new Location(ctx.world(), x + 0.5, y, z + 0.5), mob)),
                reported));
    }
}
