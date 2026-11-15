package io.github.delta1513.stairway.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SurvivalClockTest {

    private final List<String> log = new ArrayList<>();
    private ManualScheduler scheduler;
    private SurvivalClock clock;

    private final SurvivalClock.Listener listener = new SurvivalClock.Listener() {
        @Override
        public void removeLayers(int fromY, int toY) {
            log.add("remove:" + fromY + ".." + toY);
        }

        @Override
        public void fireEffect(int nextLayerY) {
            log.add("effect:" + nextLayerY);
        }

        @Override
        public void display(int nextLayerY, int secondsLeft) {
            log.add("display:" + nextLayerY + ":" + secondsLeft);
        }

        @Override
        public void finished(int nextLayerY) {
            log.add("finished:" + nextLayerY);
        }
    };

    @BeforeEach
    void setUp() {
        scheduler = new ManualScheduler();
        // bottom -64, top 319, 60 s interval, 2 layers
        clock = new SurvivalClock(scheduler, -64, 319, 60, 2, listener);
    }

    private List<String> nonDisplay() {
        return log.stream().filter(s -> !s.startsWith("display:")).toList();
    }

    @Test
    void startShowsInitialDisplayAndNothingFiresBeforeOneInterval() {
        clock.start();
        assertEquals(-64, clock.nextLayerY());
        assertEquals("display:-64:60", log.get(0));
        scheduler.tick(20 * 60 - 1);
        assertTrue(nonDisplay().isEmpty());
    }

    @Test
    void firstFiringIsOneIntervalAfterStart() {
        clock.start();
        scheduler.tick(20 * 60);
        assertEquals(List.of("remove:-64..-63", "effect:-62"), nonDisplay());
        assertEquals(-62, clock.nextLayerY());
    }

    @Test
    void removesBeforeEffectAndAdvancesEachInterval() {
        clock.start();
        scheduler.tick(20 * 60 * 3);
        assertEquals(List.of("remove:-64..-63", "effect:-62", "remove:-62..-61", "effect:-60", "remove:-60..-59",
                "effect:-58"), nonDisplay());
    }

    @Test
    void displayUpdatesEverySecond() {
        clock.start();
        scheduler.tick(20 * 3);
        assertEquals(List.of("display:-64:60", "display:-64:59", "display:-64:58", "display:-64:57"), log);
    }

    @Test
    void displayCountdownRestartsAfterFiring() {
        clock.start();
        scheduler.tick(20 * 60);
        assertEquals("display:-62:60", log.get(log.size() - 1));
    }

    @Test
    void stopsWhenNextLayerPassesTop() {
        clock.start();
        // 384 layers / 2 = 192 intervals; the last one removes 318..319
        scheduler.tick(20 * 60 * 192);
        assertEquals(320, clock.nextLayerY());
        assertTrue(clock.isFinished());
        List<String> events = nonDisplay();
        assertEquals("remove:318..319", events.get(events.size() - 3));
        assertEquals("effect:320", events.get(events.size() - 2));
        assertEquals("finished:320", events.get(events.size() - 1));
        assertEquals(0, scheduler.pending());
        int before = log.size();
        scheduler.tick(20 * 120);
        assertEquals(before, log.size());
    }

    @Test
    void lastRemovalIsClampedToTheTop() {
        SurvivalClock odd = new SurvivalClock(scheduler, 0, 4, 1, 2, listener);
        odd.start();
        scheduler.tick(20 * 3);
        assertEquals(List.of("remove:0..1", "effect:2", "remove:2..3", "effect:4", "remove:4..4", "effect:5",
                "finished:5"), nonDisplay());
    }

    @Test
    void stopCancelsEverything() {
        clock.start();
        scheduler.tick(20 * 30);
        clock.stop();
        int before = log.size();
        scheduler.tick(20 * 120);
        assertEquals(before, log.size());
        assertEquals(0, scheduler.pending());
    }

    @Test
    void startTwiceRestartsFromTheBottom() {
        clock.start();
        scheduler.tick(20 * 60);
        clock.start();
        assertEquals(-64, clock.nextLayerY());
        assertEquals(1, scheduler.pending());
    }
}
