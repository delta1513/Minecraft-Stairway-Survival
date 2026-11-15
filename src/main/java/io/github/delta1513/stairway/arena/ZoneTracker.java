package io.github.delta1513.stairway.arena;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Remembers each player's zone from the last check so effects are applied once per entry
 * (PLAN.md §6). Pure Java.
 */
public final class ZoneTracker {

    private final Map<UUID, Zone> last = new HashMap<>();

    /**
     * Records the player's current zone.
     *
     * @return the zone entered, or empty if the zone is unchanged since the last check
     */
    public Optional<Zone> update(UUID player, Zone now) {
        Zone before = last.put(player, now);
        return now == before ? Optional.empty() : Optional.of(now);
    }

    public void forget(UUID player) {
        last.remove(player);
    }

    public void clear() {
        last.clear();
    }
}
