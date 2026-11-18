package io.github.delta1513.stairway.messages;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parsed {@code messages.json} (PLAN.md §15): sections of message strings, addressed by a dotted key
 * such as {@code lobby_reminders} or {@code effects.DEATH_FEATHERS}. Pure Java apart from Gson.
 */
public final class MessageBook {

    public static final MessageBook EMPTY = new MessageBook(Map.of());

    private final Map<String, List<String>> sections;

    private MessageBook(Map<String, List<String>> sections) {
        this.sections = sections;
    }

    /**
     * @throws IllegalArgumentException if the text is not a JSON object
     * @throws com.google.gson.JsonParseException if the text is not valid JSON
     */
    public static MessageBook parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("messages.json must contain a JSON object");
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        flatten("", root.getAsJsonObject(), out);
        return new MessageBook(out);
    }

    private static void flatten(String prefix, JsonObject object, Map<String, List<String>> out) {
        for (Map.Entry<String, JsonElement> e : object.entrySet()) {
            String key = prefix + e.getKey();
            JsonElement value = e.getValue();
            if (value.isJsonArray()) {
                List<String> messages = new ArrayList<>();
                JsonArray array = value.getAsJsonArray();
                for (JsonElement item : array) {
                    if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                        messages.add(item.getAsString());
                    }
                }
                out.put(key, List.copyOf(messages));
            } else if (value.isJsonObject()) {
                flatten(key + ".", value.getAsJsonObject(), out);
            }
        }
    }

    /** The messages of a section; empty if the section is missing (an empty section gives an empty list). */
    public Optional<List<String>> section(String key) {
        return Optional.ofNullable(sections.get(key));
    }
}
