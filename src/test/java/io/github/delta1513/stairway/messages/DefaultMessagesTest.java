package io.github.delta1513.stairway.messages;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.delta1513.stairway.effects.DeathPenalty;
import io.github.delta1513.stairway.effects.SurvivalEffect;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class DefaultMessagesTest {

    private static final List<String> SECTIONS = List.of("lobby_reminders", "countdown", "survival_start",
            "ring_entered", "wither_doomed", "wheel_spin");

    private static JsonObject defaults() throws Exception {
        try (InputStream in = DefaultMessagesTest.class.getResourceAsStream("/messages.json")) {
            assertNotNull(in, "messages.json must be bundled");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void everySectionHasExactlyOneMessage() throws Exception {
        JsonObject json = defaults();
        for (String section : SECTIONS) {
            JsonArray a = json.getAsJsonArray(section);
            assertNotNull(a, section);
            assertEquals(1, a.size(), section);
        }
        JsonObject effects = json.getAsJsonObject("effects");
        for (String key : effects.keySet()) {
            assertEquals(1, effects.getAsJsonArray(key).size(), key);
        }
    }

    @Test
    void topLevelSectionsAreExactlyThoseInThePlan() throws Exception {
        Set<String> expected = new TreeSet<>(SECTIONS);
        expected.add("effects");
        assertEquals(expected, new TreeSet<>(defaults().keySet()));
    }

    @Test
    void effectKeysMatchTheEnumsExactly() throws Exception {
        Set<String> fromEnums = new TreeSet<>();
        Arrays.stream(DeathPenalty.values()).forEach(p -> fromEnums.add(p.messageKey()));
        Arrays.stream(SurvivalEffect.values()).forEach(e -> fromEnums.add(e.messageKey()));
        assertEquals(19, fromEnums.size());
        assertEquals(fromEnums, new TreeSet<>(defaults().getAsJsonObject("effects").keySet()));
    }

    @Test
    void messageKeysFollowThePlanNames() {
        assertEquals("DEATH_JUMP_BOOST", DeathPenalty.JUMP_BOOST_X.messageKey());
        assertEquals("DEATH_WATER_ABOVE", DeathPenalty.WATER_ABOVE.messageKey());
        assertEquals("SURVIVAL_JUMP_BOOST", SurvivalEffect.JUMP_BOOST_X.messageKey());
        assertEquals("SURVIVAL_PERMANENT_NIGHT", SurvivalEffect.PERMANENT_NIGHT.messageKey());
    }

    @Test
    void bundledFileParsesAsMessageBook() throws Exception {
        try (InputStream in = DefaultMessagesTest.class.getResourceAsStream("/messages.json")) {
            MessageBook book = MessageBook.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(book.section("effects.DEATH_FEATHERS").isPresent());
            assertTrue(book.section("lobby_reminders").isPresent());
        }
    }
}
