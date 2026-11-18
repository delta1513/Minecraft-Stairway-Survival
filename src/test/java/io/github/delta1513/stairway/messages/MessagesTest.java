package io.github.delta1513.stairway.messages;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class MessagesTest {

    private static final String DEFAULTS = "{\"countdown\": [\"default countdown\"]}";

    private final List<Component> sent = new ArrayList<>();
    private final List<LogRecord> logs = new ArrayList<>();
    private final Logger logger = Logger.getAnonymousLogger();
    private int nextIndex;

    MessagesTest() {
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logs.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
    }

    /** Random whose nextInt always returns the configured index (clamped to the bound). */
    private Random fixed() {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return Math.min(nextIndex, bound - 1);
            }
        };
    }

    private Messages messages(String user) {
        return new Messages(fixed(), sent::add, logger, () -> user, () -> DEFAULTS);
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private long logged(Level level) {
        return logs.stream().filter(l -> l.getLevel().equals(level)).count();
    }

    @Test
    void picksUniformlyFromSectionByRandomIndex() {
        Messages m = messages("{\"countdown\": [\"a\", \"b\", \"c\"]}");
        for (int i = 0; i < 3; i++) {
            nextIndex = i;
            m.broadcast("countdown", null);
        }
        assertEquals(List.of("a", "b", "c"), sent.stream().map(MessagesTest::plain).toList());
    }

    @Test
    void parsesMiniMessage() {
        Messages m = messages("{\"countdown\": [\"<red>hot</red>\"]}");
        m.broadcast("countdown", null);
        Component c = sent.get(0);
        assertEquals("hot", plain(c));
        assertEquals(NamedTextColor.RED, c.children().isEmpty() ? c.color() : c.children().get(0).color());
    }

    @Test
    void playerNameIsReplacedAsPlainText() {
        Messages m = messages("{\"ring_entered\": [\"hi <b>PLAYER_NAME</b>!\"]}");
        m.broadcast("ring_entered", "Alice");
        assertEquals("hi Alice!", plain(sent.get(0)));
    }

    @Test
    void playerNameCannotInjectMiniMessageTags() {
        Messages m = messages("{\"ring_entered\": [\"hi PLAYER_NAME\"]}");
        m.broadcast("ring_entered", "<red>Evil</red>");
        assertEquals("hi <red>Evil</red>", plain(sent.get(0)));
    }

    @Test
    void noTargetBecomesEveryone() {
        Messages m = messages("{\"survival_start\": [\"PLAYER_NAME wins, PLAYER_NAME\"]}");
        m.broadcast("survival_start", null);
        assertEquals("everyone wins, everyone", plain(sent.get(0)));
    }

    @Test
    void nestedEffectSectionsAreAddressedByDottedKey() {
        Messages m = messages("{\"effects\": {\"DEATH_FEATHERS\": [\"fluff\"]}}");
        m.broadcast(Messages.effectSection("DEATH_FEATHERS"), "x");
        assertEquals("fluff", plain(sent.get(0)));
    }

    @Test
    void emptySectionSendsNothingWithoutWarning() {
        Messages m = messages("{\"countdown\": []}");
        m.broadcast("countdown", null);
        assertTrue(sent.isEmpty());
        assertEquals(0, logged(Level.WARNING));
    }

    @Test
    void missingSectionSendsNothingAndWarnsOnce() {
        Messages m = messages("{\"countdown\": [\"x\"]}");
        m.broadcast("wheel_spin", null);
        m.broadcast("wheel_spin", null);
        assertTrue(sent.isEmpty());
        assertEquals(1, logged(Level.WARNING));
    }

    @Test
    void invalidJsonLogsErrorAndUsesBundledDefaults() {
        Messages m = messages("{ this is not json");
        assertEquals(1, logged(Level.SEVERE));
        m.broadcast("countdown", null);
        assertEquals("default countdown", plain(sent.get(0)));
    }

    @Test
    void nonObjectJsonIsInvalid() {
        Messages m = messages("[\"a\"]");
        assertEquals(1, logged(Level.SEVERE));
        m.broadcast("countdown", null);
        assertEquals("default countdown", plain(sent.get(0)));
    }

    @Test
    void unreadableFileFallsBackToDefaults() {
        Messages m = new Messages(fixed(), sent::add, logger, () -> {
            throw new IOException("boom");
        }, () -> DEFAULTS);
        m.broadcast("countdown", null);
        assertEquals("default countdown", plain(sent.get(0)));
    }

    @Test
    void reloadPicksUpChangesAndResetsTheWarning() {
        String[] file = {"{}"};
        Messages m = new Messages(fixed(), sent::add, logger, () -> file[0], () -> DEFAULTS);
        m.broadcast("countdown", null);
        assertTrue(sent.isEmpty());
        assertEquals(1, logged(Level.WARNING));

        file[0] = "{\"countdown\": [\"now here\"]}";
        m.reload();
        m.broadcast("countdown", null);
        assertEquals("now here", plain(sent.get(0)));

        file[0] = "{}";
        m.reload();
        m.broadcast("countdown", null);
        assertEquals(2, logged(Level.WARNING));
    }
}
