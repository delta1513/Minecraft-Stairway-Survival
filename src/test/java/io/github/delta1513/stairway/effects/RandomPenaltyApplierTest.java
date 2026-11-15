package io.github.delta1513.stairway.effects;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class RandomPenaltyApplierTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static Random fixed(int value) {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return value;
            }
        };
    }

    @Test
    void appliesTheRandomlyPickedPenaltyAndTellsThePlayer() {
        PlayerMock p = server.addPlayer();
        new RandomPenaltyApplier(fixed(DeathPenalty.BLINDNESS.ordinal()), 300).applyRandom(p);
        assertNotNull(p.getPotionEffect(PotionEffectType.BLINDNESS));
        String msg = p.nextMessage();
        assertNotNull(msg);
        assertTrue(msg.contains(DeathPenalty.BLINDNESS.displayName()), msg);
    }

    @Test
    void featherPenaltyFillsInventory() {
        PlayerMock p = server.addPlayer();
        new RandomPenaltyApplier(fixed(DeathPenalty.FEATHERS.ordinal()), 300).applyRandom(p);
        assertTrue(p.getInventory().contains(Material.FEATHER));
    }

    @Test
    void everyPenaltyIsReachableFromTheRandom() {
        PlayerMock p = server.addPlayer();
        for (int i = 0; i < DeathPenalty.values().length; i++) {
            new RandomPenaltyApplier(fixed(i), 300).applyRandom(p);
            String msg = p.nextMessage();
            assertTrue(msg.contains(DeathPenalty.values()[i].displayName()), msg);
        }
    }
}
