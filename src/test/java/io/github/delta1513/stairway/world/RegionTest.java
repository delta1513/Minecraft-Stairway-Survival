package io.github.delta1513.stairway.world;

import static org.junit.jupiter.api.Assertions.*;

import io.github.delta1513.stairway.arena.Arena;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegionTest {

    private final Arena arena = new Arena(0, 0, 20, 101, List.of("red", "orange", "yellow", "lime", "blue", "purple"));

    @Test
    void gameplayAreaCoversArenaColumnsFromFloorToTop() {
        Region r = Region.gameplayArea(arena, -64, 319);
        assertEquals(-50, r.minX());
        assertEquals(50, r.maxX());
        assertEquals(-50, r.minZ());
        assertEquals(50, r.maxZ());
        assertEquals(-64, r.minY());
        assertEquals(319, r.maxY());
        assertEquals(101L * 101L * 384L, r.volume());
    }

    @Test
    void floorAboveTopIsEmpty() {
        Region r = Region.gameplayArea(arena, 320, 319);
        assertTrue(r.isEmpty());
        assertEquals(0, r.volume());
    }

    @Test
    void twoLayerRegionHasTwentyThousandFourHundredTwoBlocks() {
        assertEquals(20_402L, Region.gameplayArea(arena, -64, -63).volume());
    }

    @Test
    void indexRoundTripsToCoordinatesWithoutRepeats() {
        Region r = new Region(10, 12, -3, -2, 5, 6);
        assertEquals(12, r.volume());
        var seen = new HashSet<List<Integer>>();
        for (long i = 0; i < r.volume(); i++) {
            int[] p = r.positionAt(i);
            assertTrue(r.contains(p[0], p[1], p[2]));
            assertTrue(seen.add(List.of(p[0], p[1], p[2])));
        }
        assertEquals(12, seen.size());
    }

    @Test
    void chunkRangeUsesFloorDivisionForNegativeCoordinates() {
        Region r = Region.gameplayArea(arena, 0, 1);
        assertEquals(-4, r.minChunkX()); // -50 >> 4
        assertEquals(3, r.maxChunkX()); // 50 >> 4
        assertEquals(-4, r.minChunkZ());
        assertEquals(3, r.maxChunkZ());
    }

    @Test
    void rejectsInvertedColumns() {
        assertThrows(IllegalArgumentException.class, () -> new Region(1, 0, 0, 0, 0, 0));
    }
}
