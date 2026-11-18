package io.github.delta1513.stairway.effects;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class DeathPenaltyTest {

    private ServerMock server;
    private WorldMock world;
    private Player player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 5, 0.5));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void sixPenaltiesInSpecOrder() {
        assertEquals(6, DeathPenalty.values().length);
        assertEquals(DeathPenalty.FEATHERS, DeathPenalty.values()[0]);
        assertEquals(DeathPenalty.JUMP_BOOST_X, DeathPenalty.values()[5]);
    }

    private static WeightedPicker<DeathPenalty> picker() {
        return new WeightedPicker<>(io.github.delta1513.stairway.config.GameConfig.uniformWeights(DeathPenalty.class));
    }

    @Test
    void pickerUsesInjectedRandomUniformly() {
        Random fixed = new Random() {
            @Override
            public int nextInt(int bound) {
                assertEquals(6, bound);
                return 3;
            }
        };
        assertEquals(DeathPenalty.WATER_ABOVE, picker().pick(fixed));
        Map<DeathPenalty, Integer> counts = new EnumMap<>(DeathPenalty.class);
        Random r = new Random(1);
        for (int i = 0; i < 6000; i++) {
            counts.merge(picker().pick(r), 1, Integer::sum);
        }
        assertEquals(6, counts.size());
        counts.values().forEach(c -> assertTrue(c > 800 && c < 1200, "count " + c));
    }

    @Test
    void feathersFillEveryEmptyStorageSlotOnly() {
        player.getInventory().setItem(0, new ItemStack(Material.BREAD, 3));
        player.getInventory().setItem(20, new ItemStack(Material.DIRT, 64));
        DeathPenalty.FEATHERS.apply(player, 300);
        ItemStack[] storage = player.getInventory().getStorageContents();
        assertEquals(36, storage.length);
        for (int i = 0; i < storage.length; i++) {
            if (i == 0) {
                assertEquals(Material.BREAD, storage[i].getType());
            } else if (i == 20) {
                assertEquals(Material.DIRT, storage[i].getType());
            } else {
                assertEquals(Material.FEATHER, storage[i].getType(), "slot " + i);
                assertEquals(64, storage[i].getAmount());
            }
        }
    }

    @Test
    void blindnessAndNauseaLastThreeMinutes() {
        DeathPenalty.BLINDNESS.apply(player, 300);
        DeathPenalty.NAUSEA.apply(player, 300);
        PotionEffect blind = player.getPotionEffect(PotionEffectType.BLINDNESS);
        PotionEffect nausea = player.getPotionEffect(PotionEffectType.NAUSEA);
        assertNotNull(blind);
        assertNotNull(nausea);
        assertEquals(0, blind.getAmplifier());
        assertEquals(0, nausea.getAmplifier());
        assertEquals(3600, blind.getDuration());
        assertEquals(3600, nausea.getDuration());
    }

    @Test
    void jumpBoostXOneMinuteAmplifierNine() {
        DeathPenalty.JUMP_BOOST_X.apply(player, 300);
        PotionEffect jb = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        assertNotNull(jb);
        assertEquals(9, jb.getAmplifier());
        assertEquals(1200, jb.getDuration());
    }

    @Test
    void thunderstormReusesSurvivalWeatherWithConfiguredDuration() {
        DeathPenalty.THUNDERSTORM.apply(player, 120);
        assertTrue(world.hasStorm());
        assertTrue(world.isThundering());
        assertEquals(2400, world.getWeatherDuration());
        assertEquals(2400, world.getThunderDuration());
    }

    @Test
    void waterAppearsOneBlockAboveTheEyeBlock() {
        Location eye = player.getEyeLocation();
        DeathPenalty.WATER_ABOVE.apply(player, 300);
        assertEquals(Material.WATER, world.getBlockAt(eye.getBlockX(), eye.getBlockY() + 1, eye.getBlockZ()).getType());
    }

    @Test
    void waterDoesNotReplaceSolidBlocks() {
        Location eye = player.getEyeLocation();
        var above = world.getBlockAt(eye.getBlockX(), eye.getBlockY() + 1, eye.getBlockZ());
        above.setType(Material.STONE);
        DeathPenalty.WATER_ABOVE.apply(player, 300);
        assertEquals(Material.STONE, above.getType());
    }

    @Test
    void waterReplacesReplaceableBlocks() {
        Location eye = player.getEyeLocation();
        var above = world.getBlockAt(eye.getBlockX(), eye.getBlockY() + 1, eye.getBlockZ());
        above.setType(Material.SHORT_GRASS);
        DeathPenalty.WATER_ABOVE.apply(player, 300);
        assertEquals(Material.WATER, above.getType());
    }
}
