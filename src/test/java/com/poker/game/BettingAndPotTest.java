package com.poker.game;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BettingAndPotTest {
    @Test void rejectsAmountsAboveAvailableChipsRatherThanSilentlyTurningThemIntoAllIn() {
        BettingRound round = new BettingRound(100);
        PlayerHandState player = new PlayerHandState("player", 0, 1000);
        assertThrows(IllegalArgumentException.class, () -> round.apply(player, new PokerAction(ActionType.BET, 1001)));
        assertEquals(1000, player.stack());
        round.apply(player, new PokerAction(ActionType.BET, 100));
        assertThrows(IllegalArgumentException.class, () -> round.apply(player, new PokerAction(ActionType.RAISE, 1001)));
        assertEquals(100, player.streetContribution());
        assertEquals(100, round.currentBet());
    }
    @Test void orphanedDeadSidePotMergesIntoNearestLowerLivePot() {
        PlayerHandState shortA = player("short-a", 0, 100);
        PlayerHandState shortB = player("short-b", 1, 200);
        PlayerHandState deepA = player("deep-a", 2, 1_000); deepA.fold();
        PlayerHandState deepB = player("deep-b", 3, 1_000); deepB.fold();
        var pots = new PotManager().buildPots(List.of(shortA, shortB, deepA, deepB));
        assertEquals(2, pots.size());
        assertEquals(400, pots.get(0).amount());
        assertEquals(java.util.Set.of("short-a", "short-b"), pots.get(0).eligibleUserIds());
        assertEquals(1_900, pots.get(1).amount());
        assertEquals(java.util.Set.of("short-b"), pots.get(1).eligibleUserIds());
    }

    @Test void minimumRaiseTargetNeverOverflowsIntoNegativeAmount() {
        BettingRound round = new BettingRound(Long.MAX_VALUE - 1);
        round.postBlind(new PlayerHandState("big", 0, Long.MAX_VALUE), Long.MAX_VALUE - 1);
        assertEquals(Long.MAX_VALUE, round.minimumRaiseTo());
    }

    @Test void shortOpeningAllInKeepsBigBlindAsMinimumRaiseIncrement() {
        BettingRound round = new BettingRound(100);
        PlayerHandState shortStack = new PlayerHandState("short", 0, 40);
        PlayerHandState deep = new PlayerHandState("deep", 1, 1_000);
        assertFalse(round.apply(shortStack, new PokerAction(ActionType.BET, 40)).fullRaise());
        assertEquals(100, round.lastFullRaiseSize());
        assertEquals(140, round.minimumRaiseTo());
        assertThrows(IllegalArgumentException.class, () -> round.apply(deep, new PokerAction(ActionType.RAISE, 80)));
    }

    @Test void shortBigBlindStillRequiresNominalBigBlindToEnterPot() {
        BettingRound round = new BettingRound(100);
        PlayerHandState small = new PlayerHandState("small", 0, 1_000);
        PlayerHandState big = new PlayerHandState("big", 1, 30);
        round.postBlind(small, 50); round.postBlind(big, 100);
        assertEquals(100, round.currentBet());
        assertEquals(50, round.toCall(small));
        assertEquals(200, round.minimumRaiseTo());
    }

    @Test void validatesBetCallRaiseAndShortAllIn() {
        BettingRound round = new BettingRound(100);
        PlayerHandState a = new PlayerHandState("a", 0, 1_000);
        PlayerHandState b = new PlayerHandState("b", 1, 1_000);
        PlayerHandState shortStack = new PlayerHandState("c", 2, 250);

        assertTrue(round.apply(a, new PokerAction(ActionType.BET, 100)).fullRaise());
        assertEquals(100, round.apply(b, PokerAction.of(ActionType.CALL)).paid());
        assertTrue(round.apply(a, new PokerAction(ActionType.RAISE, 300)).fullRaise());
        var shortResult = round.apply(shortStack, PokerAction.of(ActionType.ALL_IN));
        assertFalse(shortResult.fullRaise());
        assertEquals(300, round.currentBet());
        assertThrows(IllegalArgumentException.class,
            () -> round.apply(b, new PokerAction(ActionType.RAISE, 350)));
    }

    @Test void buildsMainAndSidePotsWhileExcludingFoldedPlayers() {
        PlayerHandState a = player("a", 0, 100);
        PlayerHandState b = player("b", 1, 300);
        PlayerHandState c = player("c", 2, 300);
        c.fold();

        var pots = new PotManager().buildPots(List.of(a, b, c));
        assertEquals(2, pots.size());
        assertEquals(300, pots.get(0).amount());
        assertEquals(java.util.Set.of("a", "b"), pots.get(0).eligibleUserIds());
        assertEquals(400, pots.get(1).amount());
        assertEquals(java.util.Set.of("b"), pots.get(1).eligibleUserIds());
    }

    @Test void shortAllInCanIncreaseCurrentBetWithoutBeingAFullRaise() {
        BettingRound round = new BettingRound(100);
        PlayerHandState opener = new PlayerHandState("a", 0, 1_000);
        PlayerHandState shortStack = new PlayerHandState("b", 1, 150);
        round.apply(opener, new PokerAction(ActionType.BET, 100));
        var result = round.apply(shortStack, PokerAction.of(ActionType.ALL_IN));
        assertEquals(150, result.currentBet());
        assertFalse(result.fullRaise());
        assertEquals(250, round.minimumRaiseTo());
    }

    private PlayerHandState player(String id, int seat, long contribution) {
        PlayerHandState player = new PlayerHandState(id, seat, contribution);
        player.commitChips(contribution);
        return player;
    }
}
