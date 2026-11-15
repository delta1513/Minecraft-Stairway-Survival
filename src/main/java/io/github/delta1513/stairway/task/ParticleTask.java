package io.github.delta1513.stairway.task;

import io.github.delta1513.stairway.arena.Plot;
import io.github.delta1513.stairway.config.GameConfig;
import io.github.delta1513.stairway.game.GameManager;
import io.github.delta1513.stairway.game.TeamColours;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Coloured dust along the plot borders, drawn per viewer (PLAN.md §7). */
public final class ParticleTask implements Runnable {

    /** Spacing of particles along the horizontal edges, in blocks. */
    static final double HORIZONTAL_STEP = 0.5;
    /** Spacing of particles in Y, in blocks. */
    static final double VERTICAL_STEP = 1.0;
    /** Particles are drawn this far above and below the viewer. */
    static final int VERTICAL_RANGE = 10;
    private static final float DUST_SIZE = 1.0f;

    private final GameManager game;
    private final GameConfig config;
    private final World world;

    public ParticleTask(GameManager game, GameConfig config, World world) {
        this.game = game;
        this.config = config;
        this.world = world;
    }

    /** Y levels to draw for a viewer at {@code playerY}, clamped to {@code [minY, maxY]}. */
    static List<Double> levels(double playerY, int minY, int maxY) {
        List<Double> ys = new ArrayList<>();
        double from = Math.max(minY, Math.floor(playerY) - VERTICAL_RANGE);
        double to = Math.min(maxY, Math.floor(playerY) + VERTICAL_RANGE);
        for (double y = from; y <= to; y += VERTICAL_STEP) {
            ys.add(y);
        }
        return ys;
    }

    static boolean inView(Plot plot, double px, double pz, double viewDistance) {
        return Math.hypot(plot.cx() + 0.5 - px, plot.cz() + 0.5 - pz) <= viewDistance;
    }

    @Override
    public void run() {
        List<Plot> plots = game.activePlots();
        for (Player player : world.getPlayers()) {
            List<Double> ys = null;
            for (Plot plot : plots) {
                if (!inView(plot, player.getX(), player.getZ(), config.particleViewDistance())) {
                    continue;
                }
                if (ys == null) {
                    ys = levels(player.getY(), world.getMinHeight(), world.getMaxHeight());
                }
                Particle.DustOptions dust = new Particle.DustOptions(TeamColours.dust(plot.colour()), DUST_SIZE);
                for (double[] xz : plot.borderPoints(HORIZONTAL_STEP)) {
                    for (double y : ys) {
                        player.spawnParticle(Particle.DUST, xz[0], y, xz[1], 1, dust);
                    }
                }
            }
        }
    }
}
