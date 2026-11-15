package io.github.delta1513.stairway.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.delta1513.stairway.game.AssignResult;
import io.github.delta1513.stairway.game.GameManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /assignme <colour>} (PLAN.md §4.1), registered through the Brigadier lifecycle API. */
public final class AssignMeCommand {

    public static final String PERMISSION = "stairway.play";

    private final GameManager game;
    private final List<String> colours;

    public AssignMeCommand(GameManager game, List<String> colours) {
        this.game = game;
        this.colours = colours;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("assignme")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("colour", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String typed = builder.getRemainingLowerCase();
                            colours.stream().filter(c -> c.startsWith(typed)).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            handle(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "colour"));
                            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    /** The command's behaviour, separate from the Brigadier plumbing. */
    public void handle(CommandSender sender, String colour) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can join a team.", NamedTextColor.RED));
            return;
        }
        String existing = game.plotOf(player.getUniqueId()).map(p -> p.colour()).orElse(null);
        AssignResult result = game.assign(player.getUniqueId(), colour);
        switch (result) {
            case ASSIGNED -> {
                // The manager already broadcast "<player> joined <colour>".
            }
            case ALREADY_ASSIGNED -> player.sendMessage(Component.text("You are already on " + existing,
                    NamedTextColor.RED));
            case NOT_IN_LOBBY -> player.sendMessage(Component.text("You can only join a team in the lobby.",
                    NamedTextColor.RED));
            case UNKNOWN_COLOUR -> player.sendMessage(Component.text(
                    "Unknown colour. Choose one of: " + String.join(", ", colours), NamedTextColor.RED));
        }
    }
}
