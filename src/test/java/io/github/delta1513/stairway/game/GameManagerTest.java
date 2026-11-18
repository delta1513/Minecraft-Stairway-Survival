package io.github.delta1513.stairway.game;

import static org.junit.jupiter.api.Assertions.*;

import io.github.delta1513.stairway.arena.Arena;
import io.github.delta1513.stairway.config.GameConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameManagerTest {

    private static final List<String> COLOURS = List.of("red", "orange", "yellow", "lime", "blue", "purple");

    private FakeEnvironment env;
    private ManualScheduler scheduler;
    private CountingSurvivalHandler survival;
    private GameManager game;
    private final List<Phase> phases = new ArrayList<>();

    private final UUID a = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");
    private final UUID b = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");
    private final UUID c = UUID.fromString("cccc0000-0000-0000-0000-000000000003");
    private final UUID d = UUID.fromString("dddd0000-0000-0000-0000-000000000004");

    static GameConfig config(int minTeams, int countdownSeconds) {
        return new GameConfig("world", minTeams, countdownSeconds, 319, 60, 2, 101, 20, 1, 10, 64, 300, 3, Map.of(), 10, 5, 30, 60, 20000, 200,
                List.of("GRANITE"), COLOURS, Map.of(),
                GameConfig.uniformWeights(io.github.delta1513.stairway.effects.DeathPenalty.class),
                GameConfig.uniformWeights(io.github.delta1513.stairway.effects.SurvivalEffect.class));
    }

    @BeforeEach
    void setUp() {
        env = new FakeEnvironment();
        scheduler = new ManualScheduler();
        survival = new CountingSurvivalHandler();
        Arena arena = new Arena(0, 0, 20, 101, COLOURS);
        game = new GameManager(config(2, 3), arena, env, scheduler, survival);
        game.addPhaseListener((from, to) -> phases.add(to));
    }

    private void join(UUID... ids) {
        for (UUID id : ids) {
            env.online.add(id);
            game.handleJoin(id);
        }
    }

    /** a on red, b on blue, both online, game in COUNTDOWN. */
    private void startTwoTeams() {
        join(a, b);
        assertEquals(AssignResult.ASSIGNED, game.assign(a, "red"));
        assertEquals(AssignResult.ASSIGNED, game.assign(b, "blue"));
        assertEquals(Phase.COUNTDOWN, game.phase());
    }

    private void startBuild() {
        startTwoTeams();
        scheduler.tick(20 * 3);
        assertEquals(Phase.BUILD, game.phase());
    }

    /** a and c on red, b on blue, in BUILD (so disqualifying a does not end the game). */
    private void startThreeTeamsInBuild() {
        join(a, b, c);
        game.assign(a, "red");
        game.assign(c, "red");
        game.assign(b, "blue");
        scheduler.tick(20 * 3);
        assertEquals(Phase.BUILD, game.phase());
    }

    // ---- LOBBY ----

    @Test
    void startsInLobby() {
        assertEquals(Phase.LOBBY, game.phase());
    }

    @Test
    void joiningInLobbySendsToLobby() {
        join(a);
        assertTrue(env.called("lobby:" + a));
    }

    @Test
    void assignRecordsAndBroadcasts() {
        join(a);
        assertEquals(AssignResult.ASSIGNED, game.assign(a, "Red"));
        assertEquals("red", game.plotOf(a).orElseThrow().colour());
        assertTrue(env.broadcasts.stream().anyMatch(m -> m.contains("joined red")));
    }

    @Test
    void assignTwiceChangesNothing() {
        join(a);
        game.assign(a, "red");
        env.broadcasts.clear();
        assertEquals(AssignResult.ALREADY_ASSIGNED, game.assign(a, "blue"));
        assertEquals("red", game.plotOf(a).orElseThrow().colour());
        assertTrue(env.broadcasts.isEmpty());
    }

    @Test
    void unknownColourRejected() {
        join(a);
        assertEquals(AssignResult.UNKNOWN_COLOUR, game.assign(a, "pink"));
        assertTrue(game.plotOf(a).isEmpty());
    }

    @Test
    void assignOutsideLobbyRejected() {
        startTwoTeams();
        assertEquals(AssignResult.NOT_IN_LOBBY, game.assign(c, "lime"));
    }

    @Test
    void oneTeamDoesNotStart() {
        join(a, b);
        game.assign(a, "red");
        game.assign(b, "red");
        assertEquals(Phase.LOBBY, game.phase());
    }

    @Test
    void unassignedOnlinePlayerBlocksStart() {
        join(a, b, c);
        game.assign(a, "red");
        game.assign(b, "blue");
        assertEquals(Phase.LOBBY, game.phase());
    }

    @Test
    void unassignedPlayerQuittingTriggersStart() {
        join(a, b, c);
        game.assign(a, "red");
        game.assign(b, "blue");
        env.online.remove(c);
        game.handleQuit(c);
        assertEquals(Phase.COUNTDOWN, game.phase());
    }

    @Test
    void unassignedPlayerJoiningDoesNotStartAndNewJoinerBlocks() {
        join(a);
        game.assign(a, "red");
        join(b);
        game.assign(b, "blue");
        assertEquals(Phase.COUNTDOWN, game.phase());
    }

    @Test
    void minTeamsConfigIsRespected() {
        Arena arena = new Arena(0, 0, 20, 101, COLOURS);
        game = new GameManager(config(3, 3), arena, env, scheduler, survival);
        join(a, b, c);
        game.assign(a, "red");
        game.assign(b, "blue");
        assertEquals(Phase.LOBBY, game.phase());
        game.assign(c, "lime");
        assertEquals(Phase.COUNTDOWN, game.phase());
    }

    // ---- COUNTDOWN ----

    @Test
    void countdownPreparesOnlyActivePlotsAndTeleportsPlayers() {
        startTwoTeams();
        assertEquals(List.of("red", "blue"), env.prepared.stream().map(p -> p.colour()).sorted(
                java.util.Comparator.comparing(COLOURS::indexOf)).toList());
        assertTrue(env.called("countdown:" + a + ":red"));
        assertTrue(env.called("countdown:" + b + ":blue"));
        assertEquals(2, game.activePlots().size());
    }

    @Test
    void countdownLocksAssignedPlayersUntilBuild() {
        startTwoTeams();
        assertTrue(game.isMoveLocked(a));
        scheduler.tick(20 * 3);
        assertFalse(game.isMoveLocked(a));
    }

    @Test
    void countdownShowsEachSecondThenEntersBuild() {
        startTwoTeams();
        assertEquals(List.of(3), env.countdownShown);
        scheduler.tick(20);
        scheduler.tick(20);
        assertEquals(List.of(3, 2, 1), env.countdownShown);
        assertEquals(Phase.COUNTDOWN, game.phase());
        scheduler.tick(20);
        assertEquals(Phase.BUILD, game.phase());
        assertEquals(List.of(Phase.COUNTDOWN, Phase.BUILD), phases);
        assertEquals(0, scheduler.pending());
    }

    @Test
    void buildEntryAppliesBuildTreatmentToOnlineAssigned() {
        startBuild();
        assertTrue(env.called("build:" + a + ":red:false"));
        assertTrue(env.called("build:" + b + ":blue:false"));
    }

    @Test
    void unassignedOnlinePlayersBecomeSpectatorsAtCountdownStart() {
        join(a, b, c);
        game.assign(a, "red");
        game.assign(b, "blue");
        env.online.remove(c);
        game.handleQuit(c);
        // c is back, still unassigned, countdown running
        env.online.add(c);
        game.handleJoin(c);
        assertTrue(env.called("spectator:" + c));
    }

    @Test
    void forceStartMakesUnassignedSpectators() {
        join(a, b, c);
        game.assign(a, "red");
        game.startCountdown();
        assertEquals(Phase.COUNTDOWN, game.phase());
        assertTrue(env.called("spectator:" + c));
        assertTrue(env.called("spectator:" + b));
        assertFalse(env.called("countdown:" + c + ":red"));
    }

    // ---- forceAssign (admin override, PLAN.md §11.1) ----

    @Test
    void forceAssignAssignsAnUnassignedPlayer() {
        join(a);
        assertEquals(AssignResult.ASSIGNED, game.forceAssign(a, "lime"));
        assertEquals("lime", game.plotOf(a).orElseThrow().colour());
        assertTrue(env.broadcasts.contains(env.nameOf(a) + " joined lime"));
    }

    @Test
    void forceAssignOverridesAnExistingAssignment() {
        join(a, b);
        game.assign(a, "red");
        assertEquals(AssignResult.ASSIGNED, game.forceAssign(a, "blue"));
        assertEquals("blue", game.plotOf(a).orElseThrow().colour());
        // b still unassigned, so no start yet
        assertEquals(Phase.LOBBY, game.phase());
    }

    @Test
    void forceAssignToTheSameColourKeepsItAssigned() {
        join(a, b);
        game.assign(a, "red");
        assertEquals(AssignResult.ASSIGNED, game.forceAssign(a, "red"));
        assertEquals("red", game.plotOf(a).orElseThrow().colour());
    }

    @Test
    void forceAssignUnknownColourLeavesTheOldAssignment() {
        join(a, b);
        game.assign(a, "red");
        assertEquals(AssignResult.UNKNOWN_COLOUR, game.forceAssign(a, "pink"));
        assertEquals("red", game.plotOf(a).orElseThrow().colour());
    }

    @Test
    void forceAssignOutsideLobbyIsRejected() {
        startBuild();
        assertEquals(AssignResult.NOT_IN_LOBBY, game.forceAssign(a, "lime"));
        assertEquals("red", game.plotOf(a).orElseThrow().colour());
    }

    @Test
    void forceAssignCanTriggerTheCountdown() {
        join(a, b);
        game.assign(a, "red");
        game.forceAssign(b, "blue");
        assertEquals(Phase.COUNTDOWN, game.phase());
    }

    @Test
    void assignedPlayerRejoiningDuringCountdownIsTeleportedAndLocked() {
        startTwoTeams();
        env.online.remove(a);
        game.handleQuit(a);
        env.calls.clear();
        env.online.add(a);
        game.handleJoin(a);
        assertTrue(env.called("countdown:" + a + ":red"));
        assertTrue(game.isMoveLocked(a));
    }

    // ---- BUILD joins ----

    @Test
    void lateAssignedJoinerIsTeleportedToPlotInBuild() {
        join(a, b, c);
        game.assign(c, "lime");
        game.assign(a, "red");
        game.assign(b, "blue");
        // c is assigned and online -> all assigned; start
        assertEquals(Phase.COUNTDOWN, game.phase());
        env.online.remove(c);
        game.handleQuit(c);
        scheduler.tick(60);
        assertEquals(Phase.BUILD, game.phase());
        env.online.add(c);
        game.handleJoin(c);
        assertTrue(env.called("build:" + c + ":lime:true"));
    }

    @Test
    void returningPlayerIsLeftAlone() {
        startBuild();
        env.online.remove(a);
        game.handleQuit(a);
        env.calls.clear();
        env.online.add(a);
        game.handleJoin(a);
        assertTrue(env.calls.isEmpty());
    }

    @Test
    void unassignedJoinerInBuildIsSpectator() {
        startBuild();
        join(c);
        assertTrue(env.called("spectator:" + c));
        assertFalse(game.isAliveParticipant(c));
    }

    @Test
    void disqualifiedPlayerRejoiningBecomesSpectator() {
        join(a, b, c);
        game.assign(a, "red");
        game.assign(b, "blue");
        game.assign(c, "lime");
        scheduler.tick(60);
        game.disqualify(a, "test");
        env.online.remove(a);
        game.handleQuit(a);
        env.calls.clear();
        env.online.add(a);
        game.handleJoin(a);
        assertTrue(env.called("spectator:" + a));
    }

    // ---- disqualification & win ----

    @Test
    void disqualifyMarksSpectatorBroadcastsAndEndsGameWhenOneTeamLeft() {
        startBuild();
        game.disqualify(a, "boom");
        assertFalse(game.isAliveParticipant(a));
        assertTrue(env.called("spectator:" + a));
        assertTrue(env.broadcasts.stream().anyMatch(m -> m.contains("was disqualified (boom)")));
        assertEquals(Phase.ENDED, game.phase());
        assertEquals("blue", env.winner.colour());
        assertTrue(env.called("release"));
    }

    @Test
    void teamWithAnotherAliveMemberKeepsPlaying() {
        join(a, b, c);
        game.assign(a, "red");
        game.assign(c, "red");
        game.assign(b, "blue");
        scheduler.tick(60);
        game.disqualify(a, "x");
        assertEquals(Phase.BUILD, game.phase());
        game.disqualify(c, "x");
        assertEquals(Phase.ENDED, game.phase());
        assertEquals("blue", env.winner.colour());
    }

    @Test
    void disqualifyingTwiceOrNonParticipantIsIgnored() {
        startBuild();
        join(c);
        game.disqualify(c, "x");
        assertEquals(Phase.BUILD, game.phase());
        game.disqualify(a, "x");
        long broadcasts = env.broadcasts.size();
        game.disqualify(a, "x");
        assertEquals(broadcasts, env.broadcasts.size());
    }

    @Test
    void disqualifyIgnoredOutsideBuildAndSurvival() {
        startTwoTeams();
        game.disqualify(a, "x");
        assertTrue(env.broadcasts.stream().noneMatch(m -> m.contains("disqualified")));
        assertEquals(Phase.COUNTDOWN, game.phase());
    }

    @Test
    void offlinePlayersStillCountForTheirTeam() {
        join(a, b);
        game.assign(a, "red");
        game.assign(b, "blue");
        scheduler.tick(60);
        env.online.remove(b);
        game.handleQuit(b);
        game.disqualify(a, "x");
        // blue's only player is offline but still counts -> blue wins
        assertEquals("blue", env.winner.colour());
    }

    @Test
    void quittingNeverDisqualifies() {
        startBuild();
        env.online.remove(a);
        game.handleQuit(a);
        assertTrue(game.isAliveParticipant(a));
        assertEquals(Phase.BUILD, game.phase());
    }

    // ---- death & respawn ----

    @Test
    void buildRespawnAtBedSpinsWheelOneTickLaterThenAppliesPenaltyAfterWheelSpinSeconds() {
        startBuild();
        env.messages.clear();
        game.onRespawn(a, true);
        assertEquals(0, env.count("pick:"));
        scheduler.tick(1);
        assertEquals(1, env.count("pick:"));
        assertEquals(List.of("wheel_spin:" + a), env.messages);
        assertEquals(0, env.count("penalty:"));
        scheduler.tick(5 * 20 - 1);
        assertEquals(0, env.count("penalty:"));
        scheduler.tick(1);
        assertTrue(env.called("penalty:" + a));
        assertTrue(game.isAliveParticipant(a));
    }

    @Test
    void penaltySkippedIfPlayerGoesOfflineDuringTheSpin() {
        startBuild();
        game.onRespawn(a, true);
        scheduler.tick(1);
        env.online.remove(a);
        scheduler.tick(5 * 20);
        assertEquals(0, env.count("penalty:"));
    }

    @Test
    void penaltySkippedIfPlayerDisqualifiedDuringTheSpin() {
        startThreeTeamsInBuild();
        game.onRespawn(a, true);
        scheduler.tick(1);
        game.disqualify(a, "x");
        scheduler.tick(5 * 20);
        assertEquals(0, env.count("penalty:"));
    }

    @Test
    void penaltySkippedIfPhaseLeavesBuildDuringTheSpin() {
        startBuild();
        game.onRespawn(a, true);
        scheduler.tick(1);
        game.startSurvival(null);
        scheduler.tick(5 * 20);
        assertEquals(0, env.count("penalty:"));
    }

    @Test
    void penaltySkippedAfterReset() {
        startBuild();
        game.onRespawn(a, true);
        scheduler.tick(1);
        game.reset();
        scheduler.tick(5 * 20);
        assertEquals(0, env.count("penalty:"));
    }

    @Test
    void buildRespawnWithoutBedDisqualifiesAfterRespawn() {
        startBuild();
        game.onRespawn(a, false);
        assertTrue(game.isAliveParticipant(a));
        scheduler.tick(1);
        assertFalse(game.isAliveParticipant(a));
        assertTrue(env.broadcasts.stream().anyMatch(m -> m.contains("(no bed)")));
        assertEquals(0, env.count("penalty:"));
    }

    @Test
    void penaltyNotAppliedIfGameEndedInBetween() {
        startBuild();
        game.onRespawn(a, true);
        game.disqualify(b, "x");
        scheduler.tick(1);
        assertEquals(0, env.count("penalty:"));
    }

    @Test
    void respawnIgnoredForNonParticipants() {
        startBuild();
        join(c);
        game.onRespawn(c, false);
        scheduler.tick(1);
        assertEquals(Phase.BUILD, game.phase());
    }

    @Test
    void survivalDeathDisqualifies() {
        startBuild();
        game.startSurvival(null);
        game.onDeath(a);
        assertFalse(game.isAliveParticipant(a));
        assertTrue(env.broadcasts.stream().anyMatch(m -> m.contains("(died)")));
    }

    @Test
    void buildDeathAloneDoesNotDisqualify() {
        startBuild();
        game.onDeath(a);
        assertTrue(game.isAliveParticipant(a));
    }

    @Test
    void worldChangeAwayFromGameWorldDisqualifies() {
        startBuild();
        game.onWorldChange(a, false);
        assertFalse(game.isAliveParticipant(a));
    }

    @Test
    void worldChangeBackIntoGameWorldIsHarmless() {
        startBuild();
        game.onWorldChange(a, true);
        assertTrue(game.isAliveParticipant(a));
    }

    @Test
    void leavingGameplayAreaDisqualifies() {
        startBuild();
        game.onLeftGameplayArea(a);
        assertFalse(game.isAliveParticipant(a));
        assertTrue(env.broadcasts.stream().anyMatch(m -> m.contains("outside the gameplay area")));
    }

    // ---- survival ----

    @Test
    void startSurvivalSwitchesPhaseMessagesAndCallsHandler() {
        startBuild();
        env.messages.clear();
        game.startSurvival(a);
        assertEquals(Phase.SURVIVAL, game.phase());
        assertEquals(1, survival.starts);
        assertEquals(List.of("survival_start:" + a), env.messages);
        assertEquals(Phase.SURVIVAL, phases.get(phases.size() - 1));
    }

    @Test
    void adminForcedSurvivalHasNoTargetPlayer() {
        startBuild();
        env.messages.clear();
        game.startSurvival(null);
        assertEquals(List.of("survival_start:null"), env.messages);
    }

    @Test
    void startSurvivalOnlyFromBuild() {
        startTwoTeams();
        game.startSurvival(null);
        assertEquals(Phase.COUNTDOWN, game.phase());
        assertEquals(0, survival.starts);
    }

    @Test
    void winInSurvivalStopsHandler() {
        startBuild();
        game.startSurvival(null);
        game.disqualify(b, "x");
        assertEquals(Phase.ENDED, game.phase());
        assertEquals(1, survival.stops);
    }

    @Test
    void winInBuildDoesNotCallSurvivalStop() {
        startBuild();
        game.disqualify(b, "x");
        assertEquals(0, survival.stops);
    }

    // ---- reset ----

    @Test
    void resetReturnsToLobbyAndClearsEverything() {
        startBuild();
        game.disqualify(a, "x");
        game.reset();
        assertEquals(Phase.LOBBY, game.phase());
        assertTrue(game.plotOf(b).isEmpty());
        assertTrue(game.activePlots().isEmpty());
        assertEquals(1, env.worldSettingsApplied);
        assertTrue(env.called("lobby:" + a));
        assertTrue(env.called("lobby:" + b));
        // can play again
        assertEquals(AssignResult.ASSIGNED, game.assign(a, "red"));
    }

    // ---- flavour messages ----

    @Test
    void countdownMessageIsSentOnceWhenCountdownStarts() {
        startTwoTeams();
        assertEquals(1, env.messages.stream().filter(m -> m.equals("countdown:null")).count());
        scheduler.tick(20 * 5);
        assertEquals(1, env.messages.stream().filter(m -> m.equals("countdown:null")).count());
    }

    private static final class FixedRandom extends java.util.Random {
        int next;

        @Override
        public int nextInt(int bound) {
            return Math.min(next, bound - 1);
        }
    }

    private long reminders() {
        return env.messages.stream().filter(m -> m.equals("lobby_reminders:null")).count();
    }

    @Test
    void lobbyRemindersFireAfterRandomDelaysWithinConfiguredRange() {
        FixedRandom random = new FixedRandom();
        game = new GameManager(config(2, 3), new Arena(0, 0, 20, 101, COLOURS), env, scheduler, survival, random);
        random.next = 0; // delay = min = 30 s
        game.startLobbyReminders();
        scheduler.tick(30 * 20 - 1);
        assertEquals(0, reminders());
        random.next = 30; // next delay = max = 60 s
        scheduler.tick(1);
        assertEquals(1, reminders());
        scheduler.tick(60 * 20 - 1);
        assertEquals(1, reminders());
        scheduler.tick(1);
        assertEquals(2, reminders());
    }

    @Test
    void lobbyRemindersStopWhenLobbyEnds() {
        FixedRandom random = new FixedRandom();
        game = new GameManager(config(2, 3), new Arena(0, 0, 20, 101, COLOURS), env, scheduler, survival, random);
        game.startLobbyReminders();
        startTwoTeams();
        scheduler.tick(20 * 120);
        assertEquals(0, reminders());
    }

    @Test
    void lobbyRemindersRestartOnReset() {
        FixedRandom random = new FixedRandom();
        game = new GameManager(config(2, 3), new Arena(0, 0, 20, 101, COLOURS), env, scheduler, survival, random);
        game.startLobbyReminders();
        startTwoTeams();
        game.reset();
        scheduler.tick(30 * 20);
        assertEquals(1, reminders());
    }

    @Test
    void remindersNeverStartOutsideLobby() {
        startTwoTeams();
        game.startLobbyReminders();
        scheduler.tick(20 * 120);
        assertEquals(0, reminders());
    }

    @Test
    void resetDuringCountdownCancelsTimer() {
        startTwoTeams();
        game.reset();
        scheduler.tick(100);
        assertEquals(Phase.LOBBY, game.phase());
    }
}
