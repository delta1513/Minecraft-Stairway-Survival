package io.github.delta1513.stairway.effects;

import static org.junit.jupiter.api.Assertions.*;

import io.github.delta1513.stairway.messages.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
    private final List<String> broadcasts = new ArrayList<>();
    private Messages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        StringBuilder json = new StringBuilder("{\"effects\": {");
        for (DeathPenalty p : DeathPenalty.values()) {
            json.append("\"").append(p.messageKey()).append("\": [\"").append(p.messageKey())
                    .append(" for PLAYER_NAME\"],");
        }
        json.setLength(json.length() - 1);
        json.append("}}");
        messages = new Messages(new Random(), c -> broadcasts.add(PlainTextComponentSerializer.plainText().serialize(c)),
                Logger.getAnonymousLogger(), json::toString, json::toString);
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

    private RandomPenaltyApplier applier(int index) {
        return new RandomPenaltyApplier(
                new WeightedPicker<>(io.github.delta1513.stairway.config.GameConfig.uniformWeights(DeathPenalty.class)),
                fixed(index), 300, messages);
    }

    @Test
    void pickingAppliesNothingUntilTheActionRuns() {
        PlayerMock p = server.addPlayer();
        var action = applier(DeathPenalty.BLINDNESS.ordinal()).pickRandom();
        assertNull(p.getPotionEffect(PotionEffectType.BLINDNESS));
        assertTrue(broadcasts.isEmpty());
        action.accept(p);
        assertNotNull(p.getPotionEffect(PotionEffectType.BLINDNESS));
    }

    @Test
    void actionBroadcastsTheEffectMessageForThePlayer() {
        PlayerMock p = server.addPlayer("Steve");
        applier(DeathPenalty.BLINDNESS.ordinal()).pickRandom().accept(p);
        assertEquals(List.of("DEATH_BLINDNESS for Steve"), broadcasts);
        assertNull(p.nextMessage(), "no private 'Death penalty' message any more");
    }

    @Test
    void featherPenaltyFillsInventory() {
        PlayerMock p = server.addPlayer();
        applier(DeathPenalty.FEATHERS.ordinal()).pickRandom().accept(p);
        assertTrue(p.getInventory().contains(Material.FEATHER));
    }

    @Test
    void onlyNonZeroWeightIsEverPicked() {
        java.util.Map<DeathPenalty, Integer> w = new java.util.EnumMap<>(DeathPenalty.class);
        for (DeathPenalty d : DeathPenalty.values()) {
            w.put(d, d == DeathPenalty.NAUSEA ? 5 : 0);
        }
        RandomPenaltyApplier a = new RandomPenaltyApplier(new WeightedPicker<>(w), new Random(99), 300, messages);
        PlayerMock p = server.addPlayer("Steve");
        for (int i = 0; i < 50; i++) {
            a.pickRandom().accept(p);
        }
        assertEquals(50, broadcasts.size());
        broadcasts.forEach(b -> assertEquals("DEATH_NAUSEA for Steve", b));
    }

    @Test
    void everyPenaltyIsReachableFromTheRandom() {
        PlayerMock p = server.addPlayer("Steve");
        for (int i = 0; i < DeathPenalty.values().length; i++) {
            applier(i).pickRandom().accept(p);
            assertEquals(DeathPenalty.values()[i].messageKey() + " for Steve", broadcasts.get(i));
        }
    }
}
