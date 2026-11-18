package io.github.delta1513.stairway.effects;

import io.github.delta1513.stairway.game.PenaltyApplier;
import java.util.Random;
import java.util.function.Consumer;
import io.github.delta1513.stairway.messages.Messages;
import org.bukkit.entity.Player;

/** {@link PenaltyApplier} that picks a {@link DeathPenalty} uniformly; its action applies it and broadcasts the effect message. */
public final class RandomPenaltyApplier implements PenaltyApplier {

    private final Random random;
    private final int thunderDurationSeconds;
    private final Messages messages;

    public RandomPenaltyApplier(Random random, int thunderDurationSeconds, Messages messages) {
        this.random = random;
        this.thunderDurationSeconds = thunderDurationSeconds;
        this.messages = messages;
    }

    @Override
    public Consumer<Player> pickRandom() {
        DeathPenalty penalty = DeathPenalty.random(random);
        return player -> {
            penalty.apply(player, thunderDurationSeconds);
            messages.broadcast(Messages.effectSection(penalty.messageKey()), player.getName());
        };
    }
}
