package io.github.delta1513.stairway.game;

import io.github.delta1513.stairway.arena.Plot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Records every call the manager makes; online players are controlled by the test. */
final class FakeEnvironment implements GameEnvironment {

    final Set<UUID> online = new LinkedHashSet<>();
    final List<String> broadcasts = new ArrayList<>();
    final List<String> calls = new ArrayList<>();
    final List<Integer> countdownShown = new ArrayList<>();
    Plot winner;
    boolean winnerAnnounced;
    int worldSettingsApplied;
    List<Plot> prepared = List.of();

    @Override
    public Collection<UUID> onlinePlayers() {
        return List.copyOf(online);
    }

    @Override
    public String nameOf(UUID player) {
        return "P" + player.toString().substring(0, 4);
    }

    @Override
    public void broadcast(String message) {
        broadcasts.add(message);
    }

    @Override
    public void sendToLobby(UUID player) {
        calls.add("lobby:" + player);
    }

    @Override
    public void preparePlots(Collection<Plot> activePlots) {
        prepared = List.copyOf(activePlots);
        calls.add("prepare");
    }

    @Override
    public void releasePlots() {
        calls.add("release");
    }

    @Override
    public void enterCountdown(UUID player, Plot plot) {
        calls.add("countdown:" + player + ":" + plot.colour());
    }

    @Override
    public void showCountdown(int secondsLeft) {
        countdownShown.add(secondsLeft);
    }

    @Override
    public void enterBuild(UUID player, Plot plot, boolean teleport) {
        calls.add("build:" + player + ":" + plot.colour() + ":" + teleport);
    }

    @Override
    public void setSpectator(UUID player) {
        calls.add("spectator:" + player);
    }

    @Override
    public void applyRandomPenalty(UUID player) {
        calls.add("penalty:" + player);
    }

    @Override
    public void announceWinner(Plot winner) {
        winnerAnnounced = true;
        this.winner = winner;
    }

    @Override
    public void applyWorldSettings() {
        worldSettingsApplied++;
    }

    boolean called(String call) {
        return calls.contains(call);
    }

    long count(String prefix) {
        return calls.stream().filter(c -> c.startsWith(prefix)).count();
    }
}
