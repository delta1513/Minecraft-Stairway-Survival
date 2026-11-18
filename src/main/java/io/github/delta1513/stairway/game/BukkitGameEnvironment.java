package io.github.delta1513.stairway.game;

import io.github.delta1513.stairway.arena.Arena;
import io.github.delta1513.stairway.arena.Plot;
import io.github.delta1513.stairway.config.GameConfig;
import java.time.Duration;
import java.util.Collection;
import io.github.delta1513.stairway.messages.Messages;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/** {@link GameEnvironment} that talks to the real (or MockBukkit) server. */
public final class BukkitGameEnvironment implements GameEnvironment {

    /** Food level and saturation given on BUILD entry (PLAN.md §4.3). */
    private static final int FULL_FOOD = 20;
    private static final float FULL_SATURATION = 20f;

    private static final Duration TITLE_FADE = Duration.ofMillis(100);
    private static final Duration TITLE_STAY = Duration.ofMillis(900);
    private static final Duration WIN_TITLE_STAY = Duration.ofSeconds(5);

    private final Server server;
    private final World world;
    private final Arena arena;
    private final PlotPreparer plots;
    private final PenaltyApplier penalties;
    private final Messages messages;

    public BukkitGameEnvironment(Server server, World world, Arena arena, GameConfig config, PlotPreparer plots,
            PenaltyApplier penalties, Messages messages) {
        this.messages = messages;
        this.server = server;
        this.world = world;
        this.arena = arena;
        this.plots = plots;
        this.penalties = penalties;
    }

    @Override
    public Collection<UUID> onlinePlayers() {
        return server.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
    }

    @Override
    public String nameOf(UUID id) {
        OfflinePlayer p = server.getOfflinePlayer(id);
        String name = p.getName();
        return name == null ? id.toString() : name;
    }

    @Override
    public void broadcast(String message) {
        server.broadcast(Component.text(message));
    }

    @Override
    public void sendToLobby(UUID id) {
        Player p = server.getPlayer(id);
        if (p == null) {
            return;
        }
        p.setGameMode(GameMode.ADVENTURE);
        p.setInvulnerable(false);
        p.teleport(plots.worldSpawnSurface());
    }

    @Override
    public void preparePlots(Collection<Plot> activePlots) {
        plots.prepare(activePlots);
    }

    @Override
    public void releasePlots() {
        plots.releaseTickets();
    }

    @Override
    public void enterCountdown(UUID id, Plot plot) {
        Player p = server.getPlayer(id);
        if (p == null) {
            return;
        }
        p.setInvulnerable(true);
        p.teleport(plots.spawnPoint(plot));
    }

    @Override
    public void showCountdown(int secondsLeft) {
        Title title = Title.title(Component.text(secondsLeft, NamedTextColor.GOLD), Component.empty(),
                Title.Times.times(Duration.ZERO, TITLE_STAY, TITLE_FADE));
        for (Player p : server.getOnlinePlayers()) {
            p.showTitle(title);
        }
    }

    @Override
    public void enterBuild(UUID id, Plot plot, boolean teleport) {
        Player p = server.getPlayer(id);
        if (p == null) {
            return;
        }
        if (teleport) {
            p.teleport(plots.spawnPoint(plot));
        }
        p.setInvulnerable(false);
        p.setGameMode(GameMode.SURVIVAL);
        p.clearActivePotionEffects();
        p.getInventory().clear();
        AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(maxHealth == null ? 20.0 : maxHealth.getValue());
        p.setFoodLevel(FULL_FOOD);
        p.setSaturation(FULL_SATURATION);
    }

    @Override
    public void setSpectator(UUID id) {
        Player p = server.getPlayer(id);
        if (p == null) {
            return;
        }
        p.setInvulnerable(false);
        p.setGameMode(GameMode.SPECTATOR);
        if (!p.getWorld().equals(world)) {
            p.teleport(plots.worldSpawnSurface());
        }
    }

    @Override
    public void message(String section, UUID target) {
        messages.broadcast(section, target == null ? null : nameOf(target));
    }

    @Override
    public Runnable pickPenalty(UUID id) {
        Consumer<Player> penalty = penalties.pickRandom();
        return () -> {
            Player p = server.getPlayer(id);
            if (p != null) {
                penalty.accept(p);
            }
        };
    }

    @Override
    public void announceWinner(Plot winner) {
        Component text = winner == null
                ? Component.text("Nobody wins!", NamedTextColor.GRAY)
                : Component.text(winner.colour() + " wins!", NamedTextColor.GOLD);
        server.broadcast(text);
        Title title = Title.title(text, Component.empty(),
                Title.Times.times(TITLE_FADE, WIN_TITLE_STAY, TITLE_FADE));
        for (Player p : server.getOnlinePlayers()) {
            p.showTitle(title);
        }
    }

    @Override
    public void applyWorldSettings() {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(arena.borderCenterX(), arena.borderCenterZ());
        border.setSize(arena.borderSize());
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
    }

    /** Used by tasks: where a stranded player is sent. */
    public Location spawnSurface() {
        return plots.worldSpawnSurface();
    }
}
