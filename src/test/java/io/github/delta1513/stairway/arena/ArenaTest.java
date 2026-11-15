package io.github.delta1513.stairway.arena;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArenaTest {

    private static final List<String> COLOURS = List.of("red", "orange", "yellow", "lime", "blue", "purple");

    private static Arena arenaAt(int sx, int sz) {
        return new Arena(sx, sz, 20, 101, COLOURS);
    }

    private static Set<Plot> all(Arena a) {
        return Set.copyOf(a.plots());
    }

    // ---- §3.1 plot centres -------------------------------------------------

    @Test
    void plotOffsetsAtOriginMatchPlanTable() {
        Arena a = arenaAt(0, 0);
        int[][] expected = {{20, 0}, {10, 17}, {-10, 17}, {-20, 0}, {-10, -17}, {10, -17}};

        assertEquals(6, a.plots().size());
        for (int i = 0; i < 6; i++) {
            Plot p = a.plots().get(i);
            assertEquals(i, p.index());
            assertEquals(COLOURS.get(i), p.colour());
            assertEquals(expected[i][0], p.cx(), "cx of plot " + i);
            assertEquals(expected[i][1], p.cz(), "cz of plot " + i);
        }
    }

    @Test
    void plotOffsetsAreRelativeToANonZeroSpawn() {
        Arena a = arenaAt(137, -42);
        int[][] expected = {{20, 0}, {10, 17}, {-10, 17}, {-20, 0}, {-10, -17}, {10, -17}};
        for (int i = 0; i < 6; i++) {
            Plot p = a.plots().get(i);
            assertEquals(137 + expected[i][0], p.cx());
            assertEquals(-42 + expected[i][1], p.cz());
        }
    }

    @Test
    void plotOffsetsAreRelativeToANegativeSpawn() {
        Arena a = arenaAt(-1000, -2000);
        assertEquals(-980, a.plots().get(0).cx());
        assertEquals(-2000, a.plots().get(0).cz());
        assertEquals(-1010, a.plots().get(4).cx());
        assertEquals(-2017, a.plots().get(4).cz());
    }

    @Test
    void plotLookupByColourIsCaseInsensitive() {
        Arena a = arenaAt(0, 0);
        assertEquals(a.plots().get(3), a.plotByColour("lime").orElseThrow());
        assertEquals(a.plots().get(3), a.plotByColour("LIME").orElseThrow());
        assertTrue(a.plotByColour("pink").isEmpty());
    }

    @Test
    void plotsListIsImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> arenaAt(0, 0).plots().clear());
    }

    @Test
    void rejectsWrongColourCount() {
        assertThrows(IllegalArgumentException.class, () -> new Arena(0, 0, 20, 101, List.of("a", "b")));
    }

    // ---- plot geometry: bounds, edges, chest -------------------------------

    @Test
    void plotCoversThreeByThreeColumns() {
        Plot p = arenaAt(0, 0).plots().get(0); // centre (20, 0)
        assertEquals(19, p.minX());
        assertEquals(21, p.maxX());
        assertEquals(-1, p.minZ());
        assertEquals(1, p.maxZ());
        assertTrue(p.contains(19, -1));
        assertTrue(p.contains(21, 1));
        assertTrue(p.contains(20, 0));
        assertFalse(p.contains(18, 0));
        assertFalse(p.contains(22, 0));
        assertFalse(p.contains(20, 2));
        assertFalse(p.contains(20, -2));
    }

    @Test
    void plotBorderEdgeCoordinatesAreBlockEdges() {
        Plot p = arenaAt(0, 0).plots().get(0); // centre (20, 0)
        // §7: square from cx-1 to cx+2 on block-edge coordinates (3 blocks wide)
        assertEquals(19.0, p.edgeMinX());
        assertEquals(22.0, p.edgeMaxX());
        assertEquals(-1.0, p.edgeMinZ());
        assertEquals(2.0, p.edgeMaxZ());
        assertEquals(3.0, p.edgeMaxX() - p.edgeMinX());
        assertEquals(3.0, p.edgeMaxZ() - p.edgeMinZ());
    }

    @Test
    void borderPointsTraceTheFourFacesEveryHalfBlock() {
        Plot p = arenaAt(0, 0).plots().get(0);
        List<double[]> pts = p.borderPoints(0.5);
        // perimeter 12 blocks / 0.5 = 24 distinct points around the square
        assertEquals(24, pts.size());
        for (double[] pt : pts) {
            boolean onX = pt[0] == p.edgeMinX() || pt[0] == p.edgeMaxX();
            boolean onZ = pt[1] == p.edgeMinZ() || pt[1] == p.edgeMaxZ();
            assertTrue(onX || onZ, "point must lie on the boundary: " + pt[0] + "," + pt[1]);
            assertTrue(pt[0] >= p.edgeMinX() && pt[0] <= p.edgeMaxX());
            assertTrue(pt[1] >= p.edgeMinZ() && pt[1] <= p.edgeMaxZ());
        }
        assertEquals(24, pts.stream().map(java.util.Arrays::toString).distinct().count());
    }

    @Test
    void chestColumnIsNorthWestCorner() {
        Plot p = arenaAt(0, 0).plots().get(0); // centre (20, 0)
        assertEquals(19, p.chestX());
        assertEquals(-1, p.chestZ());
        Plot q = arenaAt(10, 10).plots().get(2); // centre (0, 27)
        assertEquals(-1, q.chestX());
        assertEquals(26, q.chestZ());
    }

    @Test
    void spawnColumnIsPlotCentre() {
        Plot p = arenaAt(5, 6).plots().get(1);
        assertEquals(15, p.cx());
        assertEquals(23, p.cz());
    }

    // ---- §3.3 area bounds and world border ---------------------------------

    @Test
    void areaBoundsAreInclusive101x101() {
        Arena a = arenaAt(0, 0);
        assertEquals(-50, a.minX());
        assertEquals(50, a.maxX());
        assertEquals(-50, a.minZ());
        assertEquals(50, a.maxZ());
        assertEquals(101, a.maxX() - a.minX() + 1);
        assertEquals(101, a.maxZ() - a.minZ() + 1);
    }

    @Test
    void areaBoundsFollowANonZeroSpawn() {
        Arena a = arenaAt(137, -42);
        assertEquals(87, a.minX());
        assertEquals(187, a.maxX());
        assertEquals(-92, a.minZ());
        assertEquals(8, a.maxZ());
    }

    @Test
    void containsIsInclusiveAtEveryEdge() {
        Arena a = arenaAt(137, -42);
        assertTrue(a.contains(87, -92));
        assertTrue(a.contains(187, 8));
        assertTrue(a.contains(87, 8));
        assertTrue(a.contains(187, -92));
        assertTrue(a.contains(137, -42));
        assertFalse(a.contains(86, -42));
        assertFalse(a.contains(188, -42));
        assertFalse(a.contains(137, -93));
        assertFalse(a.contains(137, 9));
    }

    @Test
    void worldBorderIsCentredOnTheSpawnBlockCentre() {
        Arena a = arenaAt(137, -42);
        assertEquals(137.5, a.borderCenterX());
        assertEquals(-41.5, a.borderCenterZ());
        assertEquals(101.0, a.borderSize());
    }

    @Test
    void worldBorderMatchesBlockBoundsExactly() {
        Arena a = arenaAt(-7, 3);
        // border edges (centre +- size/2) coincide with the outer faces of the edge blocks
        assertEquals(a.minX(), a.borderCenterX() - a.borderSize() / 2.0);
        assertEquals(a.maxX() + 1, a.borderCenterX() + a.borderSize() / 2.0);
        assertEquals(a.minZ(), a.borderCenterZ() - a.borderSize() / 2.0);
        assertEquals(a.maxZ() + 1, a.borderCenterZ() + a.borderSize() / 2.0);
    }

    @Test
    void allPlotsAreInsideTheArena() {
        Arena a = arenaAt(0, 0);
        for (Plot p : a.plots()) {
            assertTrue(a.contains(p.minX(), p.minZ()));
            assertTrue(a.contains(p.maxX(), p.maxZ()));
        }
    }

    // ---- §3.2 zone classification ------------------------------------------

    @Test
    void insideCoversEveryColumnOfAnActivePlot() {
        Arena a = arenaAt(0, 0);
        Plot p = a.plots().get(0); // (20, 0)
        for (int x = p.minX(); x <= p.maxX(); x++) {
            for (int z = p.minZ(); z <= p.maxZ(); z++) {
                assertEquals(Zone.INSIDE, a.zoneOf(x, z, all(a)), x + "," + z);
            }
        }
    }

    @Test
    void ringIsTheBlockJustOutsideOnAllFourSides() {
        Arena a = arenaAt(0, 0);
        // plot 0 centred (20, 0): INSIDE x 19..21, z -1..1
        Set<Plot> active = all(a);
        assertEquals(Zone.RING, a.zoneOf(18, 0, active)); // west
        assertEquals(Zone.RING, a.zoneOf(22, 0, active)); // east
        assertEquals(Zone.RING, a.zoneOf(20, -2, active)); // north
        assertEquals(Zone.RING, a.zoneOf(20, 2, active)); // south
    }

    @Test
    void ringCornersAreRingAndDiagonalNeighbourOfCornerIsRing() {
        Arena a = arenaAt(0, 0);
        Set<Plot> active = all(a);
        // Chebyshev distance: diagonal corner blocks are still distance 1 from the edge
        assertEquals(Zone.RING, a.zoneOf(18, -2, active));
        assertEquals(Zone.RING, a.zoneOf(22, -2, active));
        assertEquals(Zone.RING, a.zoneOf(18, 2, active));
        assertEquals(Zone.RING, a.zoneOf(22, 2, active));
    }

    @Test
    void ringFullPerimeterIsExactly16BlocksPerPlot() {
        Arena a = arenaAt(0, 0);
        Set<Plot> active = Set.of(a.plots().get(0));
        int ring = 0;
        for (int x = -60; x <= 60; x++) {
            for (int z = -60; z <= 60; z++) {
                if (a.zoneOf(x, z, active) == Zone.RING) {
                    ring++;
                }
            }
        }
        assertEquals(5 * 5 - 3 * 3, ring);
    }

    @Test
    void wildStartsTwoBlocksOutsideOnAllFourSides() {
        Arena a = arenaAt(0, 0);
        Set<Plot> active = all(a);
        assertEquals(Zone.WILD, a.zoneOf(17, 0, active));
        assertEquals(Zone.WILD, a.zoneOf(23, 0, active));
        assertEquals(Zone.WILD, a.zoneOf(20, -3, active));
        assertEquals(Zone.WILD, a.zoneOf(20, 3, active));
        assertEquals(Zone.WILD, a.zoneOf(17, 3, active)); // diagonal
        assertEquals(Zone.WILD, a.zoneOf(0, 0, active)); // spawn centre
    }

    @Test
    void zonesWorkAroundNonZeroSpawn() {
        Arena a = arenaAt(137, -42);
        Set<Plot> active = all(a);
        Plot p = a.plots().get(4); // (127, -59)
        assertEquals(127, p.cx());
        assertEquals(-59, p.cz());
        assertEquals(Zone.INSIDE, a.zoneOf(126, -60, active));
        assertEquals(Zone.INSIDE, a.zoneOf(128, -58, active));
        assertEquals(Zone.RING, a.zoneOf(125, -59, active));
        assertEquals(Zone.RING, a.zoneOf(129, -59, active));
        assertEquals(Zone.RING, a.zoneOf(127, -61, active));
        assertEquals(Zone.RING, a.zoneOf(127, -57, active));
        assertEquals(Zone.WILD, a.zoneOf(124, -59, active));
        assertEquals(Zone.WILD, a.zoneOf(130, -59, active));
    }

    @Test
    void zoneIsIndependentOfWhichPlotIsNearest() {
        Arena a = arenaAt(0, 0);
        Set<Plot> active = all(a);
        // inside another team's plot is still INSIDE
        Plot lime = a.plotByColour("lime").orElseThrow(); // (-20, 0)
        assertEquals(Zone.INSIDE, a.zoneOf(lime.cx(), lime.cz(), active));
        assertEquals(Zone.RING, a.zoneOf(lime.cx() - 2, lime.cz(), active));
    }

    @Test
    void inactivePlotsAreNotSafe() {
        Arena a = arenaAt(0, 0);
        Plot red = a.plotByColour("red").orElseThrow(); // (20, 0)
        Plot lime = a.plotByColour("lime").orElseThrow(); // (-20, 0)

        Set<Plot> onlyLime = Set.of(lime);
        assertEquals(Zone.WILD, a.zoneOf(red.cx(), red.cz(), onlyLime));
        assertEquals(Zone.WILD, a.zoneOf(red.cx() - 2, red.cz(), onlyLime));
        assertEquals(Zone.INSIDE, a.zoneOf(lime.cx(), lime.cz(), onlyLime));

        Set<Plot> both = Set.of(red, lime);
        assertEquals(Zone.INSIDE, a.zoneOf(red.cx(), red.cz(), both));
    }

    @Test
    void noActivePlotsMeansEverythingIsWild() {
        Arena a = arenaAt(0, 0);
        Plot red = a.plotByColour("red").orElseThrow();
        assertEquals(Zone.WILD, a.zoneOf(red.cx(), red.cz(), Set.of()));
    }

    @Test
    void zoneUsesFlooredBlockColumnsForNegativeCoordinates() {
        Arena a = arenaAt(0, 0);
        Set<Plot> active = all(a);
        // plot 3 lime centred (-20, 0): INSIDE x -21..-19
        assertEquals(Zone.INSIDE, a.zoneOf(Arena.blockOf(-21.0), 0, active));
        assertEquals(Zone.INSIDE, a.zoneOf(Arena.blockOf(-19.01), 0, active));
        assertEquals(Zone.INSIDE, a.zoneOf(Arena.blockOf(-18.5), 0, active)); // floors to -19
        assertEquals(Zone.RING, a.zoneOf(Arena.blockOf(-18.0), 0, active)); // -18
        assertEquals(Zone.RING, a.zoneOf(Arena.blockOf(-21.5), 0, active)); // floors to -22
    }

    @Test
    void blockOfFloorsLikeMinecraft() {
        assertEquals(0, Arena.blockOf(0.0));
        assertEquals(0, Arena.blockOf(0.99));
        assertEquals(-1, Arena.blockOf(-0.01));
        assertEquals(-1, Arena.blockOf(-1.0));
        assertEquals(-2, Arena.blockOf(-1.01));
        assertEquals(137, Arena.blockOf(137.5));
    }
}
