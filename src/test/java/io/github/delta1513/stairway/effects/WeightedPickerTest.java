package io.github.delta1513.stairway.effects;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WeightedPickerTest {

    enum K { A, B, C, D }

    private static Random fixed(int value) {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                assertTrue(value < bound, "value " + value + " out of bound " + bound);
                return value;
            }
        };
    }

    private static Map<K, Integer> weights(int a, int b, int c, int d) {
        Map<K, Integer> m = new EnumMap<>(K.class);
        m.put(K.A, a);
        m.put(K.B, b);
        m.put(K.C, c);
        m.put(K.D, d);
        return m;
    }

    @Test
    void allOnesEqualsOrdinalIndex() {
        WeightedPicker<K> p = new WeightedPicker<>(weights(1, 1, 1, 1));
        for (int i = 0; i < 4; i++) {
            assertEquals(K.values()[i], p.pick(fixed(i)));
        }
        Random bound = new Random() {
            @Override
            public int nextInt(int b) {
                assertEquals(4, b);
                return 0;
            }
        };
        p.pick(bound);
    }

    @Test
    void cumulativeBoundariesAndZeroWeightNeverPicked() {
        WeightedPicker<K> p = new WeightedPicker<>(weights(1, 3, 0, 2));
        assertEquals(K.A, p.pick(fixed(0)));
        for (int r = 1; r <= 3; r++) {
            assertEquals(K.B, p.pick(fixed(r)));
        }
        assertEquals(K.D, p.pick(fixed(4)));
        assertEquals(K.D, p.pick(fixed(5)));
        for (int r = 0; r < 6; r++) {
            assertNotEquals(K.C, p.pick(fixed(r)));
        }
    }

    @Test
    void usesNextIntExactlyOnce() {
        int[] calls = {0};
        Random r = new Random() {
            @Override
            public int nextInt(int bound) {
                calls[0]++;
                assertEquals(6, bound);
                return 0;
            }
        };
        new WeightedPicker<>(weights(1, 3, 0, 2)).pick(r);
        assertEquals(1, calls[0]);
    }

    @Test
    void statisticalRatiosMatchWeights() {
        WeightedPicker<K> p = new WeightedPicker<>(weights(1, 3, 0, 2));
        Map<K, Integer> counts = new EnumMap<>(K.class);
        Random r = new Random(12345);
        int n = 60_000;
        for (int i = 0; i < n; i++) {
            counts.merge(p.pick(r), 1, Integer::sum);
        }
        assertNull(counts.get(K.C));
        assertEquals(n / 6.0, counts.get(K.A), n * 0.02);
        assertEquals(n / 2.0, counts.get(K.B), n * 0.02);
        assertEquals(n / 3.0, counts.get(K.D), n * 0.02);
    }

    @Test
    void rejectsNegativeNamingTheKey() {
        var e = assertThrows(IllegalArgumentException.class, () -> new WeightedPicker<>(weights(1, -1, 1, 1)));
        assertTrue(e.getMessage().contains("B"), e.getMessage());
    }

    @Test
    void rejectsAllZero() {
        assertThrows(IllegalArgumentException.class, () -> new WeightedPicker<>(weights(0, 0, 0, 0)));
    }

    @Test
    void rejectsOverflow() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> new WeightedPicker<>(weights(Integer.MAX_VALUE, 1, 0, 0)));
        assertTrue(e.getMessage().contains("B"), e.getMessage());
    }

    @Test
    void weightsAccessorIsUnmodifiable() {
        WeightedPicker<K> p = new WeightedPicker<>(weights(1, 2, 3, 4));
        assertEquals(weights(1, 2, 3, 4), p.weights());
        assertThrows(UnsupportedOperationException.class, () -> p.weights().put(K.A, 9));
    }
}
