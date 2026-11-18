package io.github.delta1513.stairway.effects;

import io.github.delta1513.stairway.game.PenaltyApplier;
import java.util.Random;
import java.util.function.Consumer;
import io.github.delta1513.stairway.messages.Messages;
import org.bukkit.entity.Player;

/** {@link PenaltyApplier} that picks a {@link DeathPenalty} by weight (config {@code death-penalty-weights}); its action applies it and broadcasts the effect message. */
public final class RandomPenaltyApplier implements PenaltyApplier {

    private final WeightedPicker<DeathPenalty> picker;
    private final Random random;
    private final int thunderDurationSeconds;
    private final Messages messages;

    public RandomPenaltyApplier(WeightedPicker<DeathPenalty> picker, Random random, int thunderDurationSeconds, Messages messages) {
        this.picker = picker;
        this.random = random;
        this.thunderDurationSeconds = thunderDurationSeconds;
        this.messages = messages;
    }

    @Override
    public Consumer<Player> pickRandom() {
        DeathPenalty penalty = picker.pick(random);
        return player -> {
            penalty.apply(player, thunderDurationSeconds);
            messages.broadcast(Messages.effectSection(penalty.messageKey()), player.getName());
        };
    }
}
