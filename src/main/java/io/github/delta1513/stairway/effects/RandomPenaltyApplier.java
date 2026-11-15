package io.github.delta1513.stairway.effects;

import io.github.delta1513.stairway.game.PenaltyApplier;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** {@link PenaltyApplier} that picks a {@link DeathPenalty} uniformly, applies it and tells the player. */
public final class RandomPenaltyApplier implements PenaltyApplier {

    private final Random random;
    private final int thunderDurationSeconds;

    public RandomPenaltyApplier(Random random, int thunderDurationSeconds) {
        this.random = random;
        this.thunderDurationSeconds = thunderDurationSeconds;
    }

    @Override
    public void applyRandom(Player player) {
        DeathPenalty penalty = DeathPenalty.random(random);
        penalty.apply(player, thunderDurationSeconds);
        player.sendMessage(Component.text("Death penalty: " + penalty.displayName(), NamedTextColor.RED));
    }
}
