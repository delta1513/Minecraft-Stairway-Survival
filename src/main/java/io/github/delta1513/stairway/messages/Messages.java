package io.github.delta1513.stairway.messages;

import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * The flavour chat messages (PLAN.md §15). Picks one message per section uniformly at random, parses it as
 * MiniMessage and broadcasts it. {@code PLAYER_NAME} is replaced with the target player's name after parsing,
 * as plain text, so a name can never inject MiniMessage tags.
 */
public final class Messages {

    /** Placeholder replaced with the target player's name. */
    public static final String PLAYER_PLACEHOLDER = "PLAYER_NAME";
    /** What the placeholder becomes when the message has no target player. */
    public static final String NO_TARGET_NAME = "everyone";
    public static final String EFFECTS_PREFIX = "effects.";

    /** Where the text of a JSON file comes from. */
    @FunctionalInterface
    public interface Source {
        /** @return the file text, or {@code null} if there is no such file */
        String read() throws IOException;
    }

    private final Random random;
    private final Consumer<Component> broadcaster;
    private final Logger logger;
    private final Source userFile;
    private final Source bundled;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Set<String> warned = new HashSet<>();
    private volatile MessageBook book = MessageBook.EMPTY;

    public Messages(Random random, Consumer<Component> broadcaster, Logger logger, Source userFile, Source bundled) {
        this.random = random;
        this.broadcaster = broadcaster;
        this.logger = logger;
        this.userFile = userFile;
        this.bundled = bundled;
        reload();
    }

    /** Key of the message section for an effect (a {@code messageKey()} of a penalty or survival effect). */
    public static String effectSection(String effectKey) {
        return EFFECTS_PREFIX + effectKey;
    }

    /** Re-reads the user's file; invalid or unreadable JSON falls back to the bundled defaults. */
    public void reload() {
        warned.clear();
        try {
            String text = userFile.read();
            if (text != null) {
                book = MessageBook.parse(text);
                return;
            }
            logger.warning("messages.json not found; using the bundled defaults.");
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            logger.log(Level.SEVERE, "messages.json is invalid (" + e.getMessage() + "); using the bundled defaults.");
        }
        book = loadBundled();
    }

    private MessageBook loadBundled() {
        try {
            String text = bundled.read();
            if (text != null) {
                return MessageBook.parse(text);
            }
            logger.severe("The bundled messages.json is missing; no messages will be sent.");
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            logger.log(Level.SEVERE, "The bundled messages.json is invalid: " + e.getMessage(), e);
        }
        return MessageBook.EMPTY;
    }

    /**
     * Picks and renders a message from a section.
     *
     * @param playerName the target player's name, or {@code null} for none ({@code everyone})
     * @return empty if the section is missing (warned once) or empty
     */
    public Optional<Component> pick(String section, String playerName) {
        Optional<List<String>> messages = book.section(section);
        if (messages.isEmpty()) {
            if (warned.add(section)) {
                logger.warning("messages.json has no section '" + section + "'; sending nothing for it.");
            }
            return Optional.empty();
        }
        List<String> list = messages.get();
        if (list.isEmpty()) {
            return Optional.empty();
        }
        String raw = list.get(random.nextInt(list.size()));
        Component parsed = miniMessage.deserialize(raw);
        String name = playerName == null ? NO_TARGET_NAME : playerName;
        return Optional.of(parsed.replaceText(TextReplacementConfig.builder()
                .matchLiteral(PLAYER_PLACEHOLDER)
                .replacement(Component.text(name))
                .build()));
    }

    /** Broadcasts a message from the section to everyone, if there is one. */
    public void broadcast(String section, String playerName) {
        pick(section, playerName).ifPresent(broadcaster);
    }
}
