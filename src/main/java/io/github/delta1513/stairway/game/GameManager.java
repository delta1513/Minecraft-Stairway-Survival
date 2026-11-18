package io.github.delta1513.stairway.game;

import io.github.delta1513.stairway.arena.Arena;
import io.github.delta1513.stairway.arena.Plot;
import io.github.delta1513.stairway.config.GameConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * The game state machine, teams and disqualification (PLAN.md §4, §8, §9). Contains no Bukkit calls:
 * the server is reached through {@link GameEnvironment} and time through {@link GameScheduler}.
 */
public final class GameManager {

    private static final long TICKS_PER_SECOND = 20;
    /** The respawn penalty / "no bed" disqualification happens this many ticks after the respawn. */
    private static final long POST_RESPAWN_DELAY_TICKS = 1;

    private final GameConfig config;
    private final Arena arena;
    private final GameEnvironment env;
    private final GameScheduler scheduler;
    private final SurvivalPhaseHandler survivalHandler;
    private final List<PhaseListener> listeners = new CopyOnWriteArrayList<>();

    private Phase phase = Phase.LOBBY;
    private final Map<UUID, Plot> assignments = new LinkedHashMap<>();
    private final Set<UUID> disqualified = new HashSet<>();
    /** Players that already went through BUILD entry (so a later join is a "return", not a first join). */
    private final Set<UUID> started = new HashSet<>();
    private List<Plot> activePlots = List.of();
    private GameScheduler.Handle countdownTask;
    private int countdownLeft;
    private boolean survivalRan;
    private final Random random;
    private GameScheduler.Handle lobbyReminderTask;
    private final List<GameScheduler.Handle> pendingPenalties = new ArrayList<>();

    public GameManager(GameConfig config, Arena arena, GameEnvironment env, GameScheduler scheduler,
            SurvivalPhaseHandler survivalHandler) {
        this(config, arena, env, scheduler, survivalHandler, new Random());
    }

    /** @param random picks the lobby reminder delays */
    public GameManager(GameConfig config, Arena arena, GameEnvironment env, GameScheduler scheduler,
            SurvivalPhaseHandler survivalHandler, Random random) {
        this.random = random;
        this.config = config;
        this.arena = arena;
        this.env = env;
        this.scheduler = scheduler;
        this.survivalHandler = survivalHandler;
    }

    // ---- queries ----

    public Phase phase() {
        return phase;
    }

    public Arena arena() {
        return arena;
    }

    public void addPhaseListener(PhaseListener listener) {
        listeners.add(listener);
    }

    public Optional<Plot> plotOf(UUID player) {
        return Optional.ofNullable(assignments.get(player));
    }

    /** Plots with at least one assigned player, fixed when the countdown starts. */
    public List<Plot> activePlots() {
        return activePlots;
    }

    /** An assigned player who has not been disqualified (online or not), during a running game. */
    public boolean isAliveParticipant(UUID player) {
        return isRunning() && assignments.containsKey(player) && !disqualified.contains(player);
    }

    public boolean isMoveLocked(UUID player) {
        return phase == Phase.COUNTDOWN && assignments.containsKey(player) && !disqualified.contains(player);
    }

    public int aliveCount(Plot plot) {
        return (int) assignments.entrySet().stream()
                .filter(e -> e.getValue().equals(plot) && !disqualified.contains(e.getKey()))
                .count();
    }

    public Map<UUID, Plot> assignments() {
        return Map.copyOf(assignments);
    }

    private boolean isRunning() {
        return phase == Phase.BUILD || phase == Phase.SURVIVAL;
    }

    // ---- LOBBY (PLAN.md §4.1) ----

    /**
     * Starts the lobby reminder messages (PLAN.md §15.2): one {@code lobby_reminders} message after each random
     * delay between {@code lobby-reminder-min-seconds} and {@code lobby-reminder-max-seconds}, for as long as the
     * phase is LOBBY. Called on enable; {@link #reset()} restarts it.
     */
    public void startLobbyReminders() {
        stopLobbyReminders();
        if (phase == Phase.LOBBY) {
            scheduleLobbyReminder();
        }
    }

    private void scheduleLobbyReminder() {
        int min = config.lobbyReminderMinSeconds();
        int seconds = min + random.nextInt(config.lobbyReminderMaxSeconds() - min + 1);
        lobbyReminderTask = scheduler.runLater(seconds * TICKS_PER_SECOND, () -> {
            if (phase != Phase.LOBBY) {
                lobbyReminderTask = null;
                return;
            }
            env.message("lobby_reminders", null);
            scheduleLobbyReminder();
        });
    }

    private void stopLobbyReminders() {
        if (lobbyReminderTask != null) {
            lobbyReminderTask.cancel();
            lobbyReminderTask = null;
        }
    }

    public AssignResult assign(UUID player, String colour) {
        if (phase != Phase.LOBBY) {
            return AssignResult.NOT_IN_LOBBY;
        }
        Optional<Plot> plot = arena.plotByColour(colour);
        if (plot.isEmpty()) {
            return AssignResult.UNKNOWN_COLOUR;
        }
        if (assignments.containsKey(player)) {
            return AssignResult.ALREADY_ASSIGNED;
        }
        assignments.put(player, plot.get());
        env.broadcast(env.nameOf(player) + " joined " + plot.get().colour());
        checkStart();
        return AssignResult.ASSIGNED;
    }

    /** Admin override: replaces any existing assignment. Only in LOBBY. */
    public AssignResult forceAssign(UUID player, String colour) {
        if (phase != Phase.LOBBY) {
            return AssignResult.NOT_IN_LOBBY;
        }
        if (arena.plotByColour(colour).isEmpty()) {
            return AssignResult.UNKNOWN_COLOUR;
        }
        assignments.remove(player);
        return assign(player, colour);
    }

    public void handleJoin(UUID player) {
        switch (phase) {
            case LOBBY -> {
                env.sendToLobby(player);
                checkStart();
            }
            case COUNTDOWN -> {
                Plot plot = assignments.get(player);
                if (plot != null && !disqualified.contains(player)) {
                    env.enterCountdown(player, plot);
                } else {
                    env.setSpectator(player);
                }
            }
            case BUILD, SURVIVAL -> {
                Plot plot = assignments.get(player);
                if (plot == null || disqualified.contains(player)) {
                    env.setSpectator(player);
                } else if (started.add(player)) {
                    env.enterBuild(player, plot, true);
                }
            }
            case ENDED -> {
            }
        }
    }

    public void handleQuit(UUID player) {
        if (phase == Phase.LOBBY) {
            checkStart();
        }
    }

    /** Every online player is assigned and the online assigned players span at least min-teams colours. */
    private void checkStart() {
        if (phase != Phase.LOBBY) {
            return;
        }
        Set<Plot> colours = new HashSet<>();
        for (UUID id : env.onlinePlayers()) {
            Plot plot = assignments.get(id);
            if (plot == null) {
                return;
            }
            colours.add(plot);
        }
        if (!colours.isEmpty() && colours.size() >= config.minTeams()) {
            startCountdown();
        }
    }

    // ---- COUNTDOWN (PLAN.md §4.2) ----

    /** Starts the countdown now. Unassigned online players become spectators. */
    public void startCountdown() {
        if (phase != Phase.LOBBY) {
            return;
        }
        activePlots = assignments.values().stream()
                .distinct()
                .sorted(Comparator.comparingInt(Plot::index))
                .toList();
        setPhase(Phase.COUNTDOWN);
        env.message("countdown", null);
        env.preparePlots(activePlots);
        for (UUID id : env.onlinePlayers()) {
            Plot plot = assignments.get(id);
            if (plot == null) {
                env.setSpectator(id);
            } else {
                env.enterCountdown(id, plot);
            }
        }
        countdownLeft = config.countdownSeconds();
        if (countdownLeft <= 0) {
            startBuild();
            return;
        }
        env.showCountdown(countdownLeft);
        countdownTask = scheduler.runRepeating(TICKS_PER_SECOND, TICKS_PER_SECOND, this::countdownTick);
    }

    private void countdownTick() {
        countdownLeft--;
        if (countdownLeft <= 0) {
            cancelCountdown();
            startBuild();
        } else {
            env.showCountdown(countdownLeft);
        }
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    // ---- BUILD (PLAN.md §4.3) ----

    private void startBuild() {
        setPhase(Phase.BUILD);
        for (UUID id : env.onlinePlayers()) {
            Plot plot = assignments.get(id);
            if (plot != null && !disqualified.contains(id) && started.add(id)) {
                env.enterBuild(id, plot, false);
            }
        }
    }

    // ---- SURVIVAL (PLAN.md §4.4) ----

    /**
     * Enters SURVIVAL from BUILD and broadcasts the {@code survival_start} message.
     *
     * @param trigger the player who reached the trigger height, or {@code null} when an admin forced it
     */
    public void startSurvival(UUID trigger) {
        if (phase != Phase.BUILD) {
            return;
        }
        env.message("survival_start", trigger);
        survivalRan = true;
        setPhase(Phase.SURVIVAL);
        survivalHandler.start();
    }

    // ---- deaths, world changes (PLAN.md §8.1, §9) ----

    /** {@code PlayerRespawnEvent} for any player. */
    public void onRespawn(UUID player, boolean bedSpawn) {
        if (!isRunning()) {
            return;
        }
        if (disqualified.contains(player) && assignments.containsKey(player)) {
            // Keep a disqualified player in spectator mode across the respawn.
            scheduler.runLater(POST_RESPAWN_DELAY_TICKS, () -> env.setSpectator(player));
            return;
        }
        if (phase != Phase.BUILD || !isAliveParticipant(player)) {
            return;
        }
        if (bedSpawn) {
            scheduler.runLater(POST_RESPAWN_DELAY_TICKS, () -> spinWheel(player));
        } else {
            scheduler.runLater(POST_RESPAWN_DELAY_TICKS, () -> disqualify(player, "no bed"));
        }
    }

    /** Wheel spin (PLAN.md §15.3): pick a penalty, announce the spin, apply the penalty a few seconds later. */
    private void spinWheel(UUID player) {
        if (!canBePenalised(player)) {
            return;
        }
        Runnable penalty = env.pickPenalty(player);
        env.message("wheel_spin", player);
        GameScheduler.Handle[] self = new GameScheduler.Handle[1];
        self[0] = scheduler.runLater(config.wheelSpinSeconds() * TICKS_PER_SECOND, () -> {
            pendingPenalties.remove(self[0]);
            if (canBePenalised(player)) {
                penalty.run();
            }
        });
        pendingPenalties.add(self[0]);
    }

    /** Online, alive participant during BUILD. */
    private boolean canBePenalised(UUID player) {
        return phase == Phase.BUILD && isAliveParticipant(player) && env.onlinePlayers().contains(player);
    }

    /** {@code PlayerDeathEvent}: only SURVIVAL deaths matter here (BUILD is handled on respawn). */
    public void onDeath(UUID player) {
        if (phase == Phase.SURVIVAL && isAliveParticipant(player)) {
            disqualify(player, "died");
        }
    }

    public void onWorldChange(UUID player, boolean nowInGameWorld) {
        if (!nowInGameWorld) {
            disqualify(player, "left the overworld");
        }
    }

    public void onLeftGameplayArea(UUID player) {
        disqualify(player, "outside the gameplay area");
    }

    // ---- disqualification (PLAN.md §8) ----

    public void disqualify(UUID player, String reason) {
        if (!isAliveParticipant(player)) {
            return;
        }
        disqualified.add(player);
        env.setSpectator(player);
        env.broadcast(env.nameOf(player) + " was disqualified (" + reason + ")");
        checkWin();
    }

    private void checkWin() {
        if (!isRunning()) {
            return;
        }
        List<Plot> alive = assignments.entrySet().stream()
                .filter(e -> !disqualified.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        if (alive.size() <= 1) {
            endGame(alive.isEmpty() ? null : alive.get(0));
        }
    }

    // ---- ENDED / reset (PLAN.md §4.5) ----

    private void endGame(Plot winner) {
        cancelPendingPenalties();
        stopSurvivalIfRunning();
        setPhase(Phase.ENDED);
        env.releasePlots();
        env.announceWinner(winner);
    }

    /** Back to LOBBY: clears assignments, sends everyone to the lobby, re-applies world settings. */
    public void reset() {
        cancelCountdown();
        cancelPendingPenalties();
        stopSurvivalIfRunning();
        env.releasePlots();
        assignments.clear();
        disqualified.clear();
        started.clear();
        activePlots = List.of();
        setPhase(Phase.LOBBY);
        env.applyWorldSettings();
        for (UUID id : env.onlinePlayers()) {
            env.sendToLobby(id);
        }
        startLobbyReminders();
    }

    private void cancelPendingPenalties() {
        pendingPenalties.forEach(GameScheduler.Handle::cancel);
        pendingPenalties.clear();
    }

    private void stopSurvivalIfRunning() {
        if (survivalRan) {
            survivalRan = false;
            survivalHandler.stop();
        }
    }

    private void setPhase(Phase next) {
        Phase from = phase;
        phase = next;
        if (next != Phase.LOBBY) {
            stopLobbyReminders();
        }
        for (PhaseListener l : listeners) {
            l.phaseChanged(from, next);
        }
    }
}
