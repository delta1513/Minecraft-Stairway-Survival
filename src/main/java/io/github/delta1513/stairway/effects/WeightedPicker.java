package io.github.delta1513.stairway.effects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Picks one key at random with probability {@code weight / sum of weights}. Weight 0 means never picked.
 * Entries are walked in the iteration order of the map given to the constructor (declaration order for an
 * {@link java.util.EnumMap}). {@link #pick} draws exactly one {@code nextInt(total)}, so with all weights 1 the
 * result is the same as a uniform pick by index.
 */
public final class WeightedPicker<E> {

    private final List<E> keys;
    private final int[] cumulative;
    private final int total;
    private final Map<E, Integer> weights;

    public WeightedPicker(Map<E, Integer> weights) {
        List<E> ks = new ArrayList<>();
        int[] cum = new int[weights.size()];
        long sum = 0;
        int i = 0;
        for (Map.Entry<E, Integer> e : weights.entrySet()) {
            int w = e.getValue();
            if (w < 0) {
                throw new IllegalArgumentException("Weight for " + e.getKey() + " must not be negative: " + w);
            }
            sum += w;
            if (sum > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Sum of weights exceeds " + Integer.MAX_VALUE + " (at " + e.getKey() + ")");
            }
            ks.add(e.getKey());
            cum[i++] = (int) sum;
        }
        if (sum == 0) {
            throw new IllegalArgumentException("All weights are 0 (every entry disabled); at least one must be positive");
        }
        this.keys = List.copyOf(ks);
        this.cumulative = cum;
        this.total = (int) sum;
        this.weights = Collections.unmodifiableMap(new LinkedHashMap<>(weights));
    }

    public E pick(Random random) {
        int r = random.nextInt(total);
        for (int i = 0; i < cumulative.length; i++) {
            if (r < cumulative[i]) {
                return keys.get(i);
            }
        }
        throw new IllegalStateException("unreachable: r=" + r + " total=" + total);
    }

    /** Unmodifiable copy of the weights, in pick order. */
    public Map<E, Integer> weights() {
        return weights;
    }
}
