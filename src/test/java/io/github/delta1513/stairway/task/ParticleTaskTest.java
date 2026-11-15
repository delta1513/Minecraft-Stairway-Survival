package io.github.delta1513.stairway.task;

import static org.junit.jupiter.api.Assertions.*;

import io.github.delta1513.stairway.arena.Plot;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParticleTaskTest {

    @Test
    void levelsSpanTenAboveAndBelowEveryBlock() {
        List<Double> ys = ParticleTask.levels(100.5, -64, 320);
        assertEquals(90.0, ys.get(0));
        assertEquals(110.0, ys.get(ys.size() - 1));
        assertEquals(21, ys.size());
    }

    @Test
    void levelsAreClampedToWorldHeight() {
        List<Double> low = ParticleTask.levels(-60, -64, 320);
        assertEquals(-64.0, low.get(0));
        List<Double> high = ParticleTask.levels(318, -64, 320);
        assertEquals(320.0, high.get(high.size() - 1));
    }

    @Test
    void viewDistanceIsHorizontal() {
        Plot p = new Plot(0, "red", 20, 0);
        assertTrue(ParticleTask.inView(p, 20.5 - 63, 0.5, 64));
        assertFalse(ParticleTask.inView(p, 20.5 - 65, 0.5, 64));
    }
}
