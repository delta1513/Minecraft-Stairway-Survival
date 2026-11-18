package io.github.delta1513.stairway.effects;

import static org.junit.jupiter.api.Assertions.*;

import io.github.delta1513.stairway.world.BlockJobQueue;
import io.github.delta1513.stairway.world.FakeWorld;
import io.github.delta1513.stairway.world.Region;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class SurvivalEffectTest {

    private ServerMock server;
    private WorldMock world;
    private FakeWorld blocks;
    private BlockJobQueue queue;
    private final Region region = new Region(-20, 20, -20, 20, 0, 10);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        blocks = new FakeWorld();
        queue = new BlockJobQueue(10_000, 200);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EffectContext ctx(List<Player> players) {
        return new EffectContext(world, region, players, queue, blocks, blocks, Runnable::run, 300,
                Set.of(Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.TUFF), new Random(7));
    }

    private void drain() {
        for (int i = 0; i < 1000 && !queue.isIdle(); i++) {
            queue.tick();
        }
        assertTrue(queue.isIdle());
    }

    private Player player(double x, double y, double z) {
        Player p = server.addPlayer();
        p.teleport(new Location(world, x, y, z));
        return p;
    }

    // ---- picker ----

    @Test
    void thirteenEffectsInSpecOrder() {
        assertEquals(13, SurvivalEffect.values().length);
        assertEquals(SurvivalEffect.PERMANENT_NIGHT, SurvivalEffect.values()[0]);
        assertEquals(SurvivalEffect.JUMP_BOOST_X, SurvivalEffect.values()[12]);
    }

    private static WeightedPicker<SurvivalEffect> picker() {
        return new WeightedPicker<>(io.github.delta1513.stairway.config.GameConfig.uniformWeights(SurvivalEffect.class));
    }

    @Test
    void pickerUsesInjectedRandomUniformlyWithReplacement() {
        Random fixed = new Random() {
            @Override
            public int nextInt(int bound) {
                assertEquals(13, bound);
                return 4;
            }
        };
        assertEquals(SurvivalEffect.ZOMBIE_SKELETON, picker().pick(fixed));
        assertEquals(SurvivalEffect.ZOMBIE_SKELETON, picker().pick(fixed)); // repeats allowed
        Map<SurvivalEffect, Integer> counts = new EnumMap<>(SurvivalEffect.class);
        Random r = new Random(42);
        for (int i = 0; i < 13_000; i++) {
            counts.merge(picker().pick(r), 1, Integer::sum);
        }
        assertEquals(13, counts.size());
        counts.values().forEach(c -> assertTrue(c > 800 && c < 1200, "count " + c));
    }

    // ---- time / weather ----

    @Test
    void permanentNightSetsTimeAndStopsDaylightCycle() {
        world.setTime(1000);
        SurvivalEffect.PERMANENT_NIGHT.apply(ctx(List.of()));
        assertEquals(18000, world.getTime());
        assertFalse(world.getGameRuleValue(GameRules.ADVANCE_TIME));
    }

    @Test
    void thunderstormUsesConfiguredDuration() {
        SurvivalEffect.THUNDERSTORM.apply(ctx(List.of()));
        assertTrue(world.hasStorm());
        assertTrue(world.isThundering());
        assertEquals(300 * 20, world.getWeatherDuration());
        assertEquals(300 * 20, world.getThunderDuration());
    }

    // ---- potions ----

    @Test
    void hungerNauseaAffectsAllParticipants() {
        Player a = player(0, 5, 0);
        Player b = player(3, 5, 3);
        SurvivalEffect.HUNGER_NAUSEA.apply(ctx(List.of(a, b)));
        for (Player p : List.of(a, b)) {
            PotionEffect hunger = p.getPotionEffect(PotionEffectType.HUNGER);
            PotionEffect nausea = p.getPotionEffect(PotionEffectType.NAUSEA);
            assertNotNull(hunger);
            assertEquals(2, hunger.getAmplifier());
            assertEquals(1200, hunger.getDuration());
            assertNotNull(nausea);
            assertEquals(0, nausea.getAmplifier());
            assertEquals(1200, nausea.getDuration());
        }
    }

    @Test
    void jumpBoostXIsAmplifierNineForSixtySeconds() {
        Player a = player(0, 5, 0);
        SurvivalEffect.JUMP_BOOST_X.apply(ctx(List.of(a)));
        PotionEffect jb = a.getPotionEffect(PotionEffectType.JUMP_BOOST);
        assertNotNull(jb);
        assertEquals(9, jb.getAmplifier());
        assertEquals(1200, jb.getDuration());
    }

    @Test
    void potionEffectsIgnoreNonParticipants() {
        Player a = player(0, 5, 0);
        Player bystander = player(1, 5, 1);
        SurvivalEffect.JUMP_BOOST_X.apply(ctx(List.of(a)));
        assertNull(bystander.getPotionEffect(PotionEffectType.JUMP_BOOST));
    }

    // ---- mob spawns near players ----

    @Test
    void zombieAndSkeletonPerParticipantWithinTwoBlocks() {
        Player a = player(0.5, 5, 0.5);
        Player b = player(30.5, 5, 30.5);
        SurvivalEffect.ZOMBIE_SKELETON.apply(ctx(List.of(a, b)));
        assertEquals(2, world.getEntitiesByClass(Zombie.class).size());
        assertEquals(2, world.getEntitiesByClass(Skeleton.class).size());
        for (var e : world.getEntitiesByClass(Zombie.class)) {
            Player nearest = e.getLocation().distance(a.getLocation()) < e.getLocation().distance(b.getLocation()) ? a : b;
            assertTrue(Math.abs(e.getLocation().getX() - nearest.getLocation().getX()) <= 2.5);
            assertTrue(Math.abs(e.getLocation().getZ() - nearest.getLocation().getZ()) <= 2.5);
        }
    }

    @Test
    void silverfishTenPerParticipantWithinThreeBlocks() {
        Player a = player(0.5, 5, 0.5);
        SurvivalEffect.SILVERFISH.apply(ctx(List.of(a)));
        var fish = world.getEntitiesByClass(Silverfish.class);
        assertEquals(10, fish.size());
        for (var f : fish) {
            assertTrue(Math.abs(f.getLocation().getX() - 0.5) <= 3.5);
            assertTrue(Math.abs(f.getLocation().getZ() - 0.5) <= 3.5);
        }
    }

    @Test
    void mobsSpawnOnAirAboveSolidBlockWhenAvailable() {
        // Mock world is all air; put a solid floor at y=4 around the player.
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                world.getBlockAt(x, 4, z).setType(Material.STONE);
            }
        }
        Player a = player(0.5, 5, 0.5);
        SurvivalEffect.SILVERFISH.apply(ctx(List.of(a)));
        for (var f : world.getEntitiesByClass(Silverfish.class)) {
            assertEquals(5.0, f.getLocation().getY(), 0.001);
            assertEquals(5, f.getLocation().getBlockY(), "standing on the floor at y=4");
        }
    }

    @Test
    void noParticipantsMeansNoMobs() {
        SurvivalEffect.SILVERFISH.apply(ctx(List.of()));
        SurvivalEffect.ZOMBIE_SKELETON.apply(ctx(List.of()));
        assertTrue(world.getEntities().isEmpty());
    }

    @Test
    void otherStonesConfigIsResolvedAndValidated() {
        assertEquals(Set.of(Material.GRANITE, Material.TUFF), EffectContext.resolveMaterials(List.of("GRANITE", "tuff")));
        assertThrows(IllegalArgumentException.class, () -> EffectContext.resolveMaterials(List.of("NOT_A_BLOCK")));
        assertThrows(IllegalArgumentException.class, () -> EffectContext.resolveMaterials(List.of("DIAMOND")));
    }

    // ---- block removal ----

    @Test
    void removePlanksCoversEveryPlankVariant() {
        blocks.put(0, 1, 0, Material.OAK_PLANKS);
        blocks.put(1, 1, 0, Material.CHERRY_PLANKS);
        blocks.put(2, 1, 0, Material.BAMBOO_PLANKS);
        blocks.put(3, 1, 0, Material.OAK_LOG);
        blocks.put(4, 1, 0, Material.OAK_STAIRS);
        SurvivalEffect.REMOVE_PLANKS.apply(ctx(List.of()));
        drain();
        assertEquals(Material.AIR, blocks.get(0, 1, 0));
        assertEquals(Material.AIR, blocks.get(1, 1, 0));
        assertEquals(Material.AIR, blocks.get(2, 1, 0));
        assertEquals(Material.OAK_LOG, blocks.get(3, 1, 0));
        assertEquals(Material.OAK_STAIRS, blocks.get(4, 1, 0));
    }

    @Test
    void removeOresMatchesNameSuffixIncludingDeepslateAndNether() {
        blocks.put(0, 1, 0, Material.COAL_ORE);
        blocks.put(1, 1, 0, Material.DEEPSLATE_DIAMOND_ORE);
        blocks.put(2, 1, 0, Material.NETHER_GOLD_ORE);
        blocks.put(3, 1, 0, Material.RAW_IRON_BLOCK);
        blocks.put(4, 1, 0, Material.COAL_BLOCK);
        blocks.put(5, 1, 0, Material.ANCIENT_DEBRIS);
        SurvivalEffect.REMOVE_ORES.apply(ctx(List.of()));
        drain();
        assertEquals(Material.AIR, blocks.get(0, 1, 0));
        assertEquals(Material.AIR, blocks.get(1, 1, 0));
        assertEquals(Material.AIR, blocks.get(2, 1, 0));
        assertEquals(Material.RAW_IRON_BLOCK, blocks.get(3, 1, 0));
        assertEquals(Material.COAL_BLOCK, blocks.get(4, 1, 0));
        assertEquals(Material.ANCIENT_DEBRIS, blocks.get(5, 1, 0));
    }

    @Test
    void removeOtherStonesUsesConfiguredList() {
        blocks.put(0, 1, 0, Material.GRANITE);
        blocks.put(1, 1, 0, Material.DIORITE);
        blocks.put(2, 1, 0, Material.ANDESITE);
        blocks.put(3, 1, 0, Material.TUFF);
        blocks.put(4, 1, 0, Material.DEEPSLATE);
        // default FakeWorld block is STONE and must survive
        SurvivalEffect.REMOVE_OTHER_STONES.apply(ctx(List.of()));
        drain();
        for (int x = 0; x < 4; x++) {
            assertEquals(Material.AIR, blocks.get(x, 1, 0));
        }
        assertEquals(Material.DEEPSLATE, blocks.get(4, 1, 0));
        assertEquals(Material.STONE, blocks.get(5, 1, 0));
    }

    @Test
    void removeGrassLeavesDirt() {
        blocks.put(0, 1, 0, Material.GRASS_BLOCK);
        blocks.put(1, 1, 0, Material.DIRT);
        blocks.put(2, 1, 0, Material.PODZOL);
        SurvivalEffect.REMOVE_GRASS.apply(ctx(List.of()));
        drain();
        assertEquals(Material.AIR, blocks.get(0, 1, 0));
        assertEquals(Material.DIRT, blocks.get(1, 1, 0));
        assertEquals(Material.PODZOL, blocks.get(2, 1, 0));
    }

    @Test
    void waterAndBubbleColumnBecomeLavaOtherFluidsUntouched() {
        blocks.put(0, 1, 0, Material.WATER);
        blocks.put(1, 1, 0, Material.BUBBLE_COLUMN);
        blocks.put(2, 1, 0, Material.LAVA);
        blocks.put(3, 1, 0, Material.OAK_STAIRS); // could be waterlogged: not Material.WATER, so untouched
        SurvivalEffect.WATER_TO_LAVA.apply(ctx(List.of()));
        drain();
        assertEquals(Material.LAVA, blocks.get(0, 1, 0));
        assertEquals(Material.LAVA, blocks.get(1, 1, 0));
        assertEquals(Material.LAVA, blocks.get(2, 1, 0));
        assertEquals(Material.OAK_STAIRS, blocks.get(3, 1, 0));
    }

    @Test
    void blockEffectsOnlyTouchTheRegion() {
        blocks.put(0, 11, 0, Material.OAK_PLANKS); // above region
        blocks.put(0, -1, 0, Material.OAK_PLANKS); // below region (already removed layers)
        blocks.put(25, 1, 0, Material.OAK_PLANKS); // outside area
        SurvivalEffect.REMOVE_PLANKS.apply(ctx(List.of()));
        drain();
        assertEquals(Material.OAK_PLANKS, blocks.get(0, 11, 0));
        assertEquals(Material.OAK_PLANKS, blocks.get(0, -1, 0));
        assertEquals(Material.OAK_PLANKS, blocks.get(25, 1, 0));
    }

    // ---- block removal with mobs ----

    @Test
    void everyLeafBlockBecomesAirAndOneChickenAtItsCentre() {
        blocks.put(0, 1, 0, Material.OAK_LEAVES);
        blocks.put(1, 1, 0, Material.CHERRY_LEAVES);
        blocks.put(2, 5, -3, Material.AZALEA_LEAVES);
        SurvivalEffect.LEAVES_TO_CHICKENS.apply(ctx(List.of()));
        drain();
        assertEquals(Material.AIR, blocks.get(0, 1, 0));
        assertEquals(Material.AIR, blocks.get(1, 1, 0));
        assertEquals(Material.AIR, blocks.get(2, 5, -3));
        var chickens = world.getEntitiesByClass(Chicken.class);
        assertEquals(3, chickens.size());
        assertTrue(chickens.stream().anyMatch(c -> c.getLocation().getX() == 0.5 && c.getLocation().getY() == 1
                && c.getLocation().getZ() == 0.5));
    }

    @Test
    void chickenSpawnsAreThrottledByTheEntityBudgetButAllEventuallySpawn() {
        queue = new BlockJobQueue(10_000, 4);
        for (int x = 0; x < 10; x++) {
            blocks.put(x, 1, 0, Material.OAK_LEAVES);
        }
        SurvivalEffect.LEAVES_TO_CHICKENS.apply(ctx(List.of()));
        queue.tick(); // scan + replace all ten, 4 spawns
        assertEquals(4, world.getEntitiesByClass(Chicken.class).size());
        queue.tick();
        assertEquals(8, world.getEntitiesByClass(Chicken.class).size());
        drain();
        assertEquals(10, world.getEntitiesByClass(Chicken.class).size());
    }

    @Test
    void flowersBecomeAirWithOneCreeperPerPlantTwoTallOnlyAtLowerHalf() {
        blocks.put(0, 1, 0, Material.DANDELION);
        blocks.put(1, 1, 0, Material.POPPY);
        blocks.put(2, 1, 0, Material.SUNFLOWER); // lower
        blocks.putUpper(2, 2, 0, Material.SUNFLOWER); // upper
        blocks.put(3, 1, 0, Material.ROSE_BUSH);
        blocks.putUpper(3, 2, 0, Material.ROSE_BUSH);
        blocks.put(4, 1, 0, Material.GRASS_BLOCK); // not a flower
        SurvivalEffect.FLOWERS_TO_CREEPERS.apply(ctx(List.of()));
        drain();
        for (int x = 0; x <= 3; x++) {
            assertEquals(Material.AIR, blocks.get(x, 1, 0));
        }
        assertEquals(Material.AIR, blocks.get(2, 2, 0));
        assertEquals(Material.AIR, blocks.get(3, 2, 0));
        assertEquals(Material.GRASS_BLOCK, blocks.get(4, 1, 0));
        var creepers = world.getEntitiesByClass(Creeper.class);
        assertEquals(4, creepers.size());
        assertTrue(creepers.stream().noneMatch(c -> c.getLocation().getY() == 2));
    }
}
