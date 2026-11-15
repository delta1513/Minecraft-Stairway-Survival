package io.github.delta1513.stairway.arena;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ZoneTrackerTest {

    private final UUID p = UUID.randomUUID();

    @Test
    void reportsOnlyChanges() {
        ZoneTracker t = new ZoneTracker();
        assertEquals(Optional.of(Zone.INSIDE), t.update(p, Zone.INSIDE));
        assertEquals(Optional.empty(), t.update(p, Zone.INSIDE));
        assertEquals(Optional.of(Zone.RING), t.update(p, Zone.RING));
        assertEquals(Optional.empty(), t.update(p, Zone.RING));
        assertEquals(Optional.of(Zone.INSIDE), t.update(p, Zone.INSIDE));
        assertEquals(Optional.of(Zone.RING), t.update(p, Zone.RING));
    }

    @Test
    void playersAreIndependent() {
        ZoneTracker t = new ZoneTracker();
        t.update(p, Zone.RING);
        assertEquals(Optional.of(Zone.RING), t.update(UUID.randomUUID(), Zone.RING));
    }
}
