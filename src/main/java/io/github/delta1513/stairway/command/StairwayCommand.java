package io.github.delta1513.stairway.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.delta1513.stairway.arena.Plot;
import io.github.delta1513.stairway.game.AssignResult;
import io.github.delta1513.stairway.game.GameManager;
import io.github.delta1513.stairway.game.Phase;
import io.github.delta1513.stairway.game.SurvivalPhaseHandler;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /stairway ...} admin commands (PLAN.md §11.1), permission {@code stairway.admin}. */
public final class StairwayCommand {

    public static final String PERMISSION = "stairway.admin";

    private final Server server;
    private final GameManager game;
    private final SurvivalPhaseHandler survival;
    private final List<String> colours;

    public StairwayCommand(Server server, GameManager game, SurvivalPhaseHandler survival, List<String> colours) {
        this.server = server;
        this.game = game;
        this.survival = survival;
        this.colours = colours;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("stairway")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("status").executes(ctx -> run(ctx.getSource(), this::status)))
                .then(Commands.literal("assign")
                        .then(playerArg("player")
                                .then(Commands.argument("colour", StringArgumentType.word())
                                        .suggests((c, b) -> suggest(b, colours))
                                        .executes(ctx -> {
                                            CommandSender sender = ctx.getSource().getSender();
                                            assign(sender, StringArgumentType.getString(ctx, "player"),
                                                    StringArgumentType.getString(ctx, "colour"));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("start").executes(ctx -> run(ctx.getSource(), this::start)))
                .then(Commands.literal("survival").executes(ctx -> run(ctx.getSource(), this::survival)))
                .then(Commands.literal("disqualify")
                        .then(playerArg("player").executes(ctx -> {
                            disqualify(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"));
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(Commands.literal("reset").executes(ctx -> run(ctx.getSource(), this::reset)))
                .build();
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> playerArg(String name) {
        return Commands.argument(name, StringArgumentType.word()).suggests((c, b) -> suggest(b,
                server.getOnlinePlayers().stream().map(Player::getName).toList()));
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(SuggestionsBuilder builder,
            List<String> options) {
        String typed = builder.getRemainingLowerCase();
        options.stream().filter(o -> o.toLowerCase().startsWith(typed)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private int run(CommandSourceStack src, java.util.function.Consumer<CommandSender> action) {
        action.accept(src.getSender());
        return Command.SINGLE_SUCCESS;
    }

    // ---- behaviour (separate from the Brigadier plumbing) ----

    public void status(CommandSender sender) {
        sender.sendMessage(Component.text("Phase: " + game.phase(), NamedTextColor.GOLD));
        Map<Plot, int[]> teams = new LinkedHashMap<>(); // plot -> {assigned, alive}
        game.assignments().entrySet().stream()
                .sorted(java.util.Comparator.comparingInt(e -> e.getValue().index()))
                .forEach(e -> teams.computeIfAbsent(e.getValue(), p -> new int[2])[0]++);
        for (Map.Entry<Plot, int[]> t : teams.entrySet()) {
            t.getValue()[1] = game.aliveCount(t.getKey());
        }
        if (teams.isEmpty()) {
            sender.sendMessage(Component.text("No teams yet.", NamedTextColor.GRAY));
        }
        teams.forEach((plot, n) -> sender.sendMessage(Component.text(
                plot.colour() + ": " + n[1] + " alive of " + n[0] + " players", NamedTextColor.GRAY)));
        survival.nextLayerY().ifPresent(y -> sender.sendMessage(Component.text(
                "Next layer to remove: Y=" + y, NamedTextColor.GRAY)));
    }

    public void assign(CommandSender sender, String playerName, String colour) {
        Optional<UUID> id = resolveOnlineOrCached(playerName);
        if (id.isEmpty()) {
            error(sender, "Unknown player: " + playerName);
            return;
        }
        AssignResult result = game.forceAssign(id.get(), colour);
        switch (result) {
            case ASSIGNED -> ok(sender, playerName + " assigned to " + colour);
            case UNKNOWN_COLOUR -> error(sender, "Unknown colour. Choose one of: " + String.join(", ", colours));
            case NOT_IN_LOBBY -> error(sender, "Players can only be assigned in the lobby.");
            case ALREADY_ASSIGNED -> error(sender, playerName + " is already assigned."); // not produced by forceAssign
        }
    }

    public void start(CommandSender sender) {
        if (game.phase() != Phase.LOBBY) {
            error(sender, "The game can only be started from the lobby (phase is " + game.phase() + ").");
            return;
        }
        if (game.assignments().isEmpty()) {
            error(sender, "Nobody is assigned to a team yet.");
            return;
        }
        game.startCountdown();
    }

    public void survival(CommandSender sender) {
        if (game.phase() != Phase.BUILD) {
            error(sender, "Survival can only be forced during BUILD (phase is " + game.phase() + ").");
            return;
        }
        game.startSurvival("Survival phase forced by an admin.");
    }

    public void disqualify(CommandSender sender, String playerName) {
        Optional<UUID> id = resolveParticipant(playerName);
        if (id.isEmpty() || !game.isAliveParticipant(id.get())) {
            error(sender, playerName + " is not an alive participant of a running game.");
            return;
        }
        game.disqualify(id.get(), "disqualified by an admin");
    }

    public void reset(CommandSender sender) {
        game.reset();
        ok(sender, "Game reset; back in the lobby.");
    }

    // ---- player lookup ----

    private Optional<UUID> resolveOnlineOrCached(String name) {
        Player online = server.getPlayerExact(name);
        if (online != null) {
            return Optional.of(online.getUniqueId());
        }
        OfflinePlayer cached = server.getOfflinePlayerIfCached(name);
        return Optional.ofNullable(cached).map(OfflinePlayer::getUniqueId);
    }

    /** Participants may be offline (PLAN.md §8.2), so also match assigned players by name. */
    private Optional<UUID> resolveParticipant(String name) {
        Player online = server.getPlayerExact(name);
        if (online != null) {
            return Optional.of(online.getUniqueId());
        }
        return game.assignments().keySet().stream()
                .filter(id -> name.equalsIgnoreCase(server.getOfflinePlayer(id).getName()))
                .findFirst();
    }

    private static void ok(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.GREEN));
    }

    private static void error(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.RED));
    }
}
