package com.poker.server.table;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.game.FixedDeckProvider;
import com.poker.model.Card;
import com.poker.model.Rank;
import com.poker.model.Role;
import com.poker.model.Suit;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PokerTableRuntimeTest {
    @Test void everyPlayerDisconnectingStillSettlesOnceAndFreesAllSeats() throws Exception {
        List<com.poker.persistence.HandSettlement> settlements = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("all-offline", 1, 50, 2, 1,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper(), settlements::add)) {
            table.join(1, "alice", 10000); table.join(2, "bob", 10000);
            table.ready(1, true); table.ready(2, true);
            table.setConnected(1, false); table.setConnected(2, false);
            waitFor(table::isUnused, 3);
            assertEquals(1, settlements.size());
            assertEquals(20000, settlements.getFirst().players().stream()
                .mapToLong(com.poker.persistence.HandSettlement.PlayerResult::finalStack).sum());
            assertEquals(10050, settlements.getFirst().players().stream()
                .filter(player -> player.userId() == 2).findFirst().orElseThrow().finalStack(),
                "An offline winner must be credited before releasing the seat");
        }
    }

    @Test void seededHandsConserveChipsAndRejectedActionsNeverMutateState() {
        var random = new java.util.Random(20261011);
        for (int scenario = 0; scenario < 60; scenario++) {
            int count = 2 + random.nextInt(7);
            List<com.poker.persistence.HandSettlement> settlements = new CopyOnWriteArrayList<>();
            var prompts = new java.util.concurrent.ConcurrentHashMap<Long, com.poker.protocol.Message>();
            try (PokerTableRuntime table = new PokerTableRuntime("seeded-" + scenario, 1, 50, count, 60,
                new FixedDeckProvider(deck()), (id, message) -> {
                    if (message.type() == com.poker.protocol.MessageType.ACTION_REQUIRED) prompts.put(id, message);
                }, new ObjectMapper(), settlements::add)) {
                long total = 0;
                for (int i = 1; i <= count; i++) { long chips = 100 + random.nextInt(2000); total += chips; table.join(i, "player" + i, chips); }
                for (int i = 1; i <= count; i++) table.ready(i, true);
                int actions = 0;
                while (table.snapshot(1, Role.PLAYER).status() == com.poker.model.TableStatus.IN_HAND) {
                    assertTrue(++actions < 100, "A seeded hand must terminate");
                    var snapshot = table.snapshot(1, Role.PLAYER);
                    long actor = snapshot.currentTurnUserId();
                    assertThrows(IllegalStateException.class, () -> table.action(actor,
                        com.poker.game.PokerAction.of(com.poker.game.ActionType.FOLD), snapshot.stateVersion() - 1));
                    assertEquals(snapshot, table.snapshot(1, Role.PLAYER), "Stale action must not change chips/turn/history");
                    var prompt = prompts.get(actor).payload();
                    int choice = random.nextInt(4);
                    var type = choice == 0 ? com.poker.game.ActionType.FOLD
                        : choice == 1 && prompt.path("canRaise").asBoolean() ? com.poker.game.ActionType.ALL_IN
                        : prompt.path("toCall").asLong() == 0 ? com.poker.game.ActionType.CHECK : com.poker.game.ActionType.CALL;
                    act(table, actor, com.poker.game.PokerAction.of(type));
                }
                assertEquals(1, settlements.size());
                assertEquals(total, settlements.getFirst().players().stream()
                    .mapToLong(com.poker.persistence.HandSettlement.PlayerResult::finalStack).sum());
                assertTrue(settlements.getFirst().players().stream().allMatch(player -> player.finalStack() >= 0));
            }
        }
    }

    @Test void offlineSeatsAreRemovedOnlyAfterSettlementAndReconnectKeepsSeat() throws Exception {
        List<com.poker.persistence.HandSettlement> settlements = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("offline-cleanup", 1, 50, 3, 1,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper(), settlements::add)) {
            for (int i = 1; i <= 3; i++) table.join(i, "player" + i, 10000);
            for (int i = 1; i <= 3; i++) table.ready(i, true);
            table.setConnected(2, false); table.setConnected(3, false); table.setConnected(3, true);
            assertTrue(table.hasSeat(2), "Keep in-hand seat for resume and settlement");
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.FOLD));
            waitFor(() -> table.snapshot(3, Role.PLAYER).status() == com.poker.model.TableStatus.BETWEEN_HANDS, 3);
            assertFalse(table.hasSeat(2), "Offline seat must be released at the hand boundary");
            assertTrue(table.hasSeat(3), "Resumed seat must survive cleanup");
            assertEquals(1, settlements.size());
            assertEquals(30000, settlements.getFirst().players().stream()
                .mapToLong(com.poker.persistence.HandSettlement.PlayerResult::finalStack).sum());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {2, 3, 8})
    void pacedDealBlocksActionsAndStartsFreshTwentySecondTurn(int players) throws Exception {
        List<Sent> sent = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("paced", 1, 50, players, 20,
            new FixedDeckProvider(deck()), (id, message) -> sent.add(new Sent(id, message)), new ObjectMapper(), ignored -> {}, true)) {
            for (int i = 1; i <= players; i++) table.join(i, "player" + i, 10000);
            table.spectate(99);
            for (int i = 1; i <= players; i++) table.ready(i, true);
            TableSnapshot initial = table.snapshot(1, Role.PLAYER);
            assertEquals(com.poker.game.HandPhase.DEALING, initial.phase());
            assertEquals(com.poker.common.DealTiming.duration(players * 2), initial.dealDurationMillis());
            assertNull(initial.currentTurnUserId()); assertEquals(0, initial.turnSeconds());
            long version = initial.stateVersion();
            assertTrue(assertThrows(IllegalStateException.class, () -> table.action(1,
                com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL), version)).getMessage().contains("Dealing"));
            assertEquals(version, table.snapshot(1, Role.PLAYER).stateVersion());
            assertTrue(sent.stream().noneMatch(event -> event.message().type() == com.poker.protocol.MessageType.ACTION_REQUIRED));
            Thread.sleep(60); table.setConnected(1, false); table.setConnected(1, true);
            TableSnapshot resumed = table.snapshot(1, Role.PLAYER);
            assertEquals(initial.handId(), resumed.handId()); assertEquals(initial.phase(), resumed.phase());
            assertTrue(resumed.dealRemainingMillis() < initial.dealRemainingMillis());
            assertEquals(2, resumed.seats().stream().filter(seat -> seat.userId() == 1).findFirst().orElseThrow().holeCards().size());
            assertTrue(resumed.seats().stream().filter(seat -> seat.userId() != 1).allMatch(seat -> seat.holeCards().isEmpty()));
            waitFor(() -> table.snapshot(1, Role.PLAYER).phase() == com.poker.game.HandPhase.BETTING, 10);
            TableSnapshot betting = table.snapshot(1, Role.PLAYER);
            assertTrue(betting.turnSeconds() >= 19); assertEquals(players == 2 ? 1L : players == 3 ? 1L : 4L, betting.currentTurnUserId());
            long actor = betting.currentTurnUserId();
            table.action(actor, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL), betting.stateVersion());
            var action = table.snapshot(1, Role.PLAYER).recentActions().getLast();
            assertEquals("CALL", action.action()); assertEquals(actor, action.userId()); assertFalse(action.automatic());
            assertTrue(action.paid() > 0);
            assertEquals(players + 1, sent.stream().filter(event -> event.message().type() == com.poker.protocol.MessageType.TABLE_ACTION &&
                event.message().payload().path("sequence").asLong() == action.sequence()).count());
        }
    }

    @Test void pacedAllInRunoutShowsEveryStreetBeforeSettlement() throws Exception {
        List<Sent> sent = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("runout", 1, 50, 2, 20,
            new FixedDeckProvider(deck()), (id, message) -> sent.add(new Sent(id, message)), new ObjectMapper(), ignored -> {}, true)) {
            table.join(1, "sb", 100); table.join(2, "bb", 100); table.ready(1, true); table.ready(2, true);
            waitFor(() -> table.snapshot(1, Role.PLAYER).phase() == com.poker.game.HandPhase.BETTING, 4);
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.ALL_IN));
            assertEquals(com.poker.game.Street.FLOP, table.snapshot(1, Role.PLAYER).street());
            assertNull(table.snapshot(1, Role.PLAYER).currentTurnUserId());
            waitFor(() -> table.snapshot(1, Role.PLAYER).status() == com.poker.model.TableStatus.BETWEEN_HANDS, 5);
            for (int cards : new int[]{3, 4, 5}) assertTrue(sent.stream().anyMatch(event ->
                event.message().type() == com.poker.protocol.MessageType.TABLE_STATE &&
                event.message().payload().path("board").size() == cards &&
                event.message().payload().path("phase").asText().equals("DEALING") &&
                event.message().payload().path("currentTurnUserId").isNull()));
            assertEquals(200, table.snapshot(1, Role.PLAYER).seats().stream().mapToLong(SeatSnapshot::stack).sum());
            assertFalse(table.snapshot(1, Role.PLAYER).winnerPayouts().isEmpty());
        }
    }

    @Test void leavingDuringDealSettlesOnceAndInvalidatesOldDealCallback() throws Exception {
        List<com.poker.persistence.HandSettlement> settlements = new CopyOnWriteArrayList<>();
        List<Sent> sent = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("leave-deal", 1, 50, 2, 20,
            new FixedDeckProvider(deck()), (id, message) -> sent.add(new Sent(id, message)), new ObjectMapper(), settlements::add, true)) {
            table.join(1, "sb", 10000); table.join(2, "bb", 10000); table.ready(1, true); table.ready(2, true);
            table.leave(1); assertEquals(1, settlements.size());
            assertEquals(List.of(new com.poker.persistence.HandSettlement.ActionRecord(1, 1, "PRE_FLOP", "FOLD", 0)),
                settlements.getFirst().actions(), "Leaving must persist the same automatic Fold that peers observe");
            assertEquals(com.poker.model.TableStatus.BETWEEN_HANDS, table.snapshot(2, Role.PLAYER).status());
            Thread.sleep(com.poker.common.DealTiming.duration(4) + 100);
            assertEquals(1, settlements.size());
            assertNull(table.snapshot(2, Role.PLAYER).phase());
            assertTrue(sent.stream().noneMatch(event -> event.message().type() == com.poker.protocol.MessageType.ACTION_REQUIRED));
        }
    }

    private void waitFor(java.util.function.BooleanSupplier condition, int seconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (!condition.getAsBoolean()) { if (System.nanoTime() > deadline) fail("Table phase did not advance"); Thread.sleep(10); }
    }

    @Test void publicActionHistoryIsBoundedAndRejectedActionEmitsNothing() {
        List<Sent> sent = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("action-history", 1, 50, 3, 60,
            new FixedDeckProvider(deck()), (id, message) -> sent.add(new Sent(id, message)), new ObjectMapper())) {
            for (int i = 1; i <= 3; i++) table.join(i, "player" + i, 10000);
            for (int i = 1; i <= 3; i++) table.ready(i, true);
            for (int i = 0; i < 30; i++) {
                var view = table.snapshot(1, Role.PLAYER);
                table.action(view.currentTurnUserId(), new com.poker.game.PokerAction(com.poker.game.ActionType.RAISE, 200 + i * 100), view.stateVersion());
            }
            var before = table.snapshot(1, Role.PLAYER);
            assertEquals(16, before.recentActions().size()); assertEquals(32, before.recentActions().getLast().sequence());
            int messages = sent.size();
            assertThrows(IllegalArgumentException.class, () -> table.action(before.currentTurnUserId(),
                com.poker.game.PokerAction.of(com.poker.game.ActionType.CHECK), before.stateVersion()));
            assertEquals(messages, sent.size()); assertEquals(before.stateVersion(), table.snapshot(1, Role.PLAYER).stateVersion());
            assertEquals(before.recentActions(), table.snapshot(99, Role.OBSERVER).recentActions());
        }
    }

    @Test void foldedDealerDoesNotConvertAMultiwayHandIntoHeadsUpOrdering() {
        try (PokerTableRuntime table = new PokerTableRuntime("folded-button", 1, 50, 3, 60,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper())) {
            for (int i = 1; i <= 3; i++) table.join(i, "player" + i, 10000);
            for (int i = 1; i <= 3; i++) table.ready(i, true);
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.FOLD));
            act(table, 2, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            act(table, 3, com.poker.game.PokerAction.of(com.poker.game.ActionType.CHECK));
            assertEquals(2L, table.snapshot(2, Role.PLAYER).currentTurnUserId());
            assertEquals(0, table.snapshot(2, Role.PLAYER).dealerSeat());
            assertEquals(1, table.snapshot(2, Role.PLAYER).smallBlindSeat());
        }
    }
    @Test void sparseSeatsAndFoldedSmallBlindKeepTheOriginalButtonForPostflop() {
        try (PokerTableRuntime table = new PokerTableRuntime("sparse", 1, 50, 5, 60,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper())) {
            for (int i = 1; i <= 5; i++) table.join(i, "player" + i, 10_000);
            table.leave(2); table.leave(4);
            table.ready(1, true); table.ready(3, true); table.ready(5, true);
            assertEquals(1L, table.snapshot(1, Role.PLAYER).currentTurnUserId());
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            act(table, 3, com.poker.game.PokerAction.of(com.poker.game.ActionType.FOLD));
            act(table, 5, com.poker.game.PokerAction.of(com.poker.game.ActionType.CHECK));
            TableSnapshot flop = table.snapshot(1, Role.PLAYER);
            assertEquals(5L, flop.currentTurnUserId());
            assertEquals(0, flop.dealerSeat()); assertEquals(2, flop.smallBlindSeat()); assertEquals(4, flop.bigBlindSeat());
            act(table, 5, com.poker.game.PokerAction.of(com.poker.game.ActionType.CHECK));
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.CHECK));
            assertEquals(5L, table.snapshot(1, Role.PLAYER).currentTurnUserId());
        }
    }

    @Test void skipsAllInBlindAndFoldedDealerWithoutReassigningPositionsMidHand() {
        try (PokerTableRuntime table = new PokerTableRuntime("all-in-blind", 1, 50, 3, 60,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper())) {
            table.join(1, "dealer", 1000); table.join(2, "short-sb", 50); table.join(3, "bb", 1000);
            table.ready(1, true); table.ready(2, true); table.ready(3, true);
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            assertEquals(3L, table.snapshot(1, Role.PLAYER).currentTurnUserId());
            act(table, 3, com.poker.game.PokerAction.of(com.poker.game.ActionType.CHECK));
            assertEquals(3L, table.snapshot(1, Role.PLAYER).currentTurnUserId());
            act(table, 3, new com.poker.game.PokerAction(com.poker.game.ActionType.BET, 100));
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.FOLD));
            assertEquals(com.poker.model.TableStatus.BETWEEN_HANDS, table.snapshot(3, Role.PLAYER).status());
            assertEquals(0, table.snapshot(3, Role.PLAYER).dealerSeat());
        }
    }

    @Test void raiseAndReraiseContinueClockwiseAndFlopStillStartsAfterDealer() {
        try (PokerTableRuntime table = new PokerTableRuntime("re-raise", 1, 50, 3, 60,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper())) {
            for (int i = 1; i <= 3; i++) table.join(i, "player" + i, 10000);
            for (int i = 1; i <= 3; i++) table.ready(i, true);
            act(table, 1, new com.poker.game.PokerAction(com.poker.game.ActionType.RAISE, 200));
            act(table, 2, new com.poker.game.PokerAction(com.poker.game.ActionType.RAISE, 400));
            act(table, 3, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            assertEquals(1L, table.snapshot(1, Role.PLAYER).currentTurnUserId());
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            assertEquals(2L, table.snapshot(1, Role.PLAYER).currentTurnUserId());
            assertEquals(com.poker.game.Street.FLOP, table.snapshot(1, Role.PLAYER).street());
        }
    }

    @Test void gloryChangesOnlyAfterSuccessfulSettlementNotWhenPostingBlinds() {
        var initial = com.poker.model.GlorySummary.from(25000, 24, 99);
        try (PokerTableRuntime table = new PokerTableRuntime("glory", 1, 50, 2, 60,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper())) {
            table.join(1, "alice", 25000, initial); table.join(2, "bob", 25000, initial);
            table.ready(1, true); table.ready(2, true);
            assertEquals(initial, table.snapshot(1, Role.PLAYER).seats().getFirst().glory());
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.FOLD));
            var result = table.snapshot(1, Role.PLAYER);
            assertEquals("Đồng", result.seats().getFirst().glory().wealthTier());
            assertEquals("Vàng", result.seats().get(1).glory().achievementTier());
            assertEquals(100, result.seats().getFirst().glory().gamesPlayed());
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {2, 3, 8})
    void keepsPositionsForAllStreetsAndRotatesOnlyForNextHand(int count) {
        List<Sent> sent = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("order", 1, 50, count, 60,
            new FixedDeckProvider(deck()), (id, message) -> sent.add(new Sent(id, message)), new ObjectMapper())) {
            for (int i = 1; i <= count; i++) table.join(i, "player" + i, 10_000);
            for (int i = 1; i <= count; i++) table.ready(i, true);
            var orders = new java.util.LinkedHashMap<com.poker.game.Street, List<Long>>();
            while (table.snapshot(1, Role.PLAYER).status() == com.poker.model.TableStatus.IN_HAND) {
                TableSnapshot state = table.snapshot(1, Role.PLAYER);
                assertEquals(0, state.dealerSeat());
                assertEquals(count == 2 ? 0 : 1, state.smallBlindSeat());
                assertEquals(count == 2 ? 1 : 2, state.bigBlindSeat());
                long actor = state.currentTurnUserId();
                orders.computeIfAbsent(state.street(), ignored -> new ArrayList<>()).add(actor);
                var prompt = sent.stream().filter(s -> s.userId() == actor && s.message().seq() == state.stateVersion()
                    && s.message().type() == com.poker.protocol.MessageType.ACTION_REQUIRED).reduce((a,b) -> b).orElseThrow();
                act(table, actor, com.poker.game.PokerAction.of(prompt.message().payload().path("toCall").asLong() == 0
                    ? com.poker.game.ActionType.CHECK : com.poker.game.ActionType.CALL));
            }
            List<Long> pre = switch (count) { case 2 -> List.of(1L,2L); case 3 -> List.of(1L,2L,3L);
                default -> List.of(4L,5L,6L,7L,8L,1L,2L,3L); };
            List<Long> post = count == 2 ? List.of(2L,1L) : count == 3 ? List.of(2L,3L,1L)
                : List.of(2L,3L,4L,5L,6L,7L,8L,1L);
            assertEquals(pre, orders.get(com.poker.game.Street.PRE_FLOP));
            for (var street : List.of(com.poker.game.Street.FLOP, com.poker.game.Street.TURN, com.poker.game.Street.RIVER))
                assertEquals(post, orders.get(street));
            for (int i = 1; i <= count; i++) table.ready(i, true);
            assertEquals(1, table.snapshot(1, Role.PLAYER).dealerSeat());
        }
    }
    @Test void enteringHeadsUpDoesNotChargeThePreviousBigBlindAgain() {
        try (PokerTableRuntime table = new PokerTableRuntime("heads-up", 1, 50, 3, 60,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper())) {
            table.join(1, "button", 10_000); table.join(2, "small", 10_000); table.join(3, "big", 10_000);
            table.ready(1, true); table.ready(2, true); table.ready(3, true);
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.FOLD));
            act(table, 2, com.poker.game.PokerAction.of(com.poker.game.ActionType.FOLD));
            table.leave(1);
            table.ready(2, true); table.ready(3, true);
            TableSnapshot next = table.snapshot(2, Role.PLAYER);
            assertEquals(3L, next.currentTurnUserId(), "Previous BB becomes button/SB on entering heads-up");
            assertEquals(100, next.seats().stream().filter(s -> s.userId() == 2).findFirst().orElseThrow().streetContribution());
        }
    }
    @Test void queuedCommandTimeoutCancelsMutationButStartedCommandReportsItsOutcome() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean blockFirstSend = new AtomicBoolean(true);
        try (PokerTableRuntime table = new PokerTableRuntime("command-timeout", 1, 50, 2, 60,
            new FixedDeckProvider(deck()), (id, message) -> {
                if (blockFirstSend.compareAndSet(true, false)) {
                    started.countDown();
                    try { release.await(); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                }
            }, new ObjectMapper()); var callers = Executors.newFixedThreadPool(2)) {
            var first = callers.submit(() -> table.join(1, "first", 10_000));
            if (!started.await(2, TimeUnit.SECONDS)) { release.countDown(); fail("Game command did not start"); }
            var queued = callers.submit(() -> table.join(2, "cancelled", 10_000));
            try {
                var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> queued.get(7, TimeUnit.SECONDS));
                assertTrue(failure.getCause().getMessage().contains("timed out before starting"));
                assertFalse(first.isDone(), "Started command must wait for its real commit outcome");
            } finally { release.countDown(); }
            assertEquals(1, first.get(2, TimeUnit.SECONDS).seats().size());
            assertFalse(table.hasSeat(2), "A timed-out queued join must never mutate the table later");
        } finally { release.countDown(); }
    }

    @Test void closingTableReleasesWaitingQueuedCalls() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean blockFirstSend = new AtomicBoolean(true);
        try (PokerTableRuntime table = new PokerTableRuntime("closed-queue", 1, 50, 2, 60,
            new FixedDeckProvider(deck()), (id, message) -> {
                if (blockFirstSend.compareAndSet(true, false)) {
                    started.countDown();
                    try { release.await(); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                }
            }, new ObjectMapper()); var callers = Executors.newFixedThreadPool(2)) {
            var first = callers.submit(() -> table.join(1, "first", 10_000));
            if (!started.await(2, TimeUnit.SECONDS)) { release.countDown(); fail("Game command did not start"); }
            var queued = callers.submit(() -> table.join(2, "cancelled", 10_000));
            table.close();
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> queued.get(1, TimeUnit.SECONDS));
            assertTrue(failure.getCause().getMessage().contains("closed"));
            release.countDown();
            assertEquals(1, first.get(2, TimeUnit.SECONDS).seats().size());
            assertThrows(IllegalStateException.class, () -> table.snapshot(1, Role.PLAYER));
        } finally { release.countDown(); }
    }

    @Test void callsFromGameThreadDoNotQueueBehindThemselves() {
        AtomicReference<PokerTableRuntime> reference = new AtomicReference<>();
        AtomicReference<TableSnapshot> captured = new AtomicReference<>();
        try (PokerTableRuntime table = new PokerTableRuntime("reentrant-read", 1, 50, 2, 60,
            new FixedDeckProvider(deck()), (id, message) -> captured.set(reference.get().snapshot(id, Role.PLAYER)), new ObjectMapper())) {
            reference.set(table);
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> table.join(1, "alice", 10_000));
            assertEquals(1, captured.get().seats().size());
        }
    }

    @Test void everyPlayerLeavingSettlesOnceAndThenRemovesEverySeat() {
        List<com.poker.persistence.HandSettlement> settlements = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("all-leave", 1, 50, 3, 60,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper(), settlements::add)) {
            table.join(1, "alice", 10_000); table.join(2, "bob", 10_000); table.join(3, "carol", 10_000);
            table.ready(1, true); table.ready(2, true); table.ready(3, true);
            table.leave(1); table.leave(2); table.leave(3);
            TableSnapshot result = table.snapshot(99, Role.OBSERVER);
            assertEquals(com.poker.model.TableStatus.BETWEEN_HANDS, result.status());
            assertTrue(result.seats().isEmpty());
            assertEquals(1, settlements.size());
            assertEquals(30_000, settlements.getFirst().players().stream()
                .mapToLong(com.poker.persistence.HandSettlement.PlayerResult::finalStack).sum());
        }
    }

    @Test void cumulativeShortRaisesReopenEachPlayerAccordingToAmountTheyLastFaced() {
        List<Sent> sent = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("personal-reopen", 1, 10, 5, 60,
            new FixedDeckProvider(deck()), (id, message) -> sent.add(new Sent(id, message)), new ObjectMapper())) {
            table.join(1, "middle-caller", 1_000); table.join(2, "short-200", 200);
            table.join(3, "last-caller", 1_000); table.join(4, "opener", 1_000); table.join(5, "short-125", 125);
            for (long user = 1; user <= 5; user++) table.ready(user, true);
            act(table, 4, new com.poker.game.PokerAction(com.poker.game.ActionType.RAISE, 100));
            act(table, 5, com.poker.game.PokerAction.of(com.poker.game.ActionType.ALL_IN));
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            act(table, 2, com.poker.game.PokerAction.of(com.poker.game.ActionType.ALL_IN));
            act(table, 3, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            var openerPrompt = sent.stream().filter(event -> event.userId() == 4 &&
                event.message().type() == com.poker.protocol.MessageType.ACTION_REQUIRED)
                .reduce((a, b) -> b).orElseThrow().message().payload();
            assertTrue(openerPrompt.path("canRaise").asBoolean());
            act(table, 4, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            assertThrows(IllegalArgumentException.class,
                () -> act(table, 1, new com.poker.game.PokerAction(com.poker.game.ActionType.RAISE, 280)));
            assertDoesNotThrow(() -> act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL)));
        }
    }

    @Test void bothDeepPlayersLeavingCannotStrandDeadSidePot() {
        List<com.poker.persistence.HandSettlement> settlements = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("dead-side", 1, 10, 4, 60,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper(), settlements::add)) {
            table.join(1, "short-a", 100); table.join(2, "short-b", 200);
            table.join(3, "deep-a", 10_000); table.join(4, "deep-b", 10_000);
            table.ready(1, true); table.ready(2, true); table.ready(3, true); table.ready(4, true);
            act(table, 4, new com.poker.game.PokerAction(com.poker.game.ActionType.RAISE, 1_000));
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            act(table, 2, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            act(table, 3, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            table.leave(4);
            assertDoesNotThrow(() -> table.leave(3));
            assertEquals(com.poker.model.TableStatus.BETWEEN_HANDS, table.snapshot(1, Role.PLAYER).status());
            assertEquals(20_300, settlements.getFirst().players().stream()
                .mapToLong(com.poker.persistence.HandSettlement.PlayerResult::finalStack).sum());
        }
    }

    @Test void rejectsStacksWhoseCombinedPotWouldOverflow() {
        try (PokerTableRuntime table = new PokerTableRuntime("overflow", 1, 1, 2, 60,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper())) {
            table.join(1, "large", Long.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> table.join(2, "overflow", 1));
            assertFalse(table.hasSeat(2));
        }
    }

    @Test void dealsClockwiseOneCardAtATimeAndBurnsBeforeEveryStreet() {
        List<Card> cards = deck();
        try (PokerTableRuntime table = new PokerTableRuntime("deal", 1, 50, 3, 60,
            new FixedDeckProvider(cards), (id, message) -> {}, new ObjectMapper())) {
            table.join(1, "button", 10_000); table.join(2, "small", 10_000); table.join(3, "big", 10_000);
            table.ready(1, true); table.ready(2, true); table.ready(3, true);
            TableSnapshot initial = table.snapshot(99, Role.OBSERVER);
            assertEquals(List.of(cards.get(2), cards.get(5)), initial.seats().get(0).holeCards());
            assertEquals(List.of(cards.get(0), cards.get(3)), initial.seats().get(1).holeCards());
            assertEquals(List.of(cards.get(1), cards.get(4)), initial.seats().get(2).holeCards());
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            act(table, 2, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            act(table, 3, com.poker.game.PokerAction.of(com.poker.game.ActionType.CHECK));
            assertEquals(cards.subList(7, 10), table.snapshot(1, Role.PLAYER).board());
            for (int action = 0; action < 3; action++) {
                act(table, table.snapshot(1, Role.PLAYER).currentTurnUserId(), com.poker.game.PokerAction.of(com.poker.game.ActionType.CHECK));
            }
            assertEquals(List.of(cards.get(7), cards.get(8), cards.get(9), cards.get(11)), table.snapshot(1, Role.PLAYER).board());
            for (int action = 0; action < 3; action++) {
                act(table, table.snapshot(1, Role.PLAYER).currentTurnUserId(), com.poker.game.PokerAction.of(com.poker.game.ActionType.CHECK));
            }
            assertEquals(List.of(cards.get(7), cards.get(8), cards.get(9), cards.get(11), cards.get(13)), table.snapshot(1, Role.PLAYER).board());
        }
    }

    @Test void returnsUncalledBetEvenWhenBettorLeavesBeforeShortCalls() {
        List<Sent> sent = new CopyOnWriteArrayList<>();
        List<com.poker.persistence.HandSettlement> settlements = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("uncalled", 1, 50, 3, 60,
            new FixedDeckProvider(deck()), (id, message) -> sent.add(new Sent(id, message)), new ObjectMapper(), settlements::add)) {
            table.join(1, "bettor", 10_000); table.join(2, "short-a", 100); table.join(3, "short-b", 200);
            table.ready(1, true); table.ready(2, true); table.ready(3, true);
            act(table, 1, new com.poker.game.PokerAction(com.poker.game.ActionType.RAISE, 1_000));
            act(table, 2, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            table.leave(1);
            assertDoesNotThrow(() -> act(table, 3, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL)));
            TableSnapshot result = table.snapshot(3, Role.PLAYER);
            assertEquals(com.poker.model.TableStatus.BETWEEN_HANDS, result.status());
            assertEquals(1, settlements.size());
            assertEquals(10_300, settlements.getFirst().players().stream().mapToLong(com.poker.persistence.HandSettlement.PlayerResult::finalStack).sum());
            var bettor = settlements.getFirst().players().stream().filter(player -> player.userId() == 1).findFirst().orElseThrow();
            assertEquals(9_800, bettor.finalStack());
            assertEquals(200, bettor.contribution());
            var resultEvent = sent.stream().map(Sent::message)
                .filter(message -> message.type() == com.poker.protocol.MessageType.HAND_RESULT).findFirst().orElseThrow();
            assertEquals(800, resultEvent.payload().path("uncalledReturns").get(0).path("amount").asLong());
        }
    }

    @Test void shortAllInCannotBeBypassedUsingAllInButton() {
        try (PokerTableRuntime table = new PokerTableRuntime("closed-raise", 1, 10, 4, 60,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper())) {
            table.join(1, "short", 150); table.join(2, "caller-a", 1_000);
            table.join(3, "caller-b", 1_000); table.join(4, "opener", 1_000);
            table.ready(1, true); table.ready(2, true); table.ready(3, true); table.ready(4, true);
            act(table, 4, new com.poker.game.PokerAction(com.poker.game.ActionType.RAISE, 100));
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.ALL_IN));
            act(table, 2, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            act(table, 3, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            assertThrows(IllegalArgumentException.class,
                () -> act(table, 4, com.poker.game.PokerAction.of(com.poker.game.ActionType.ALL_IN)));
            assertDoesNotThrow(() -> act(table, 4, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL)));
        }
    }

    @Test void replacementSeatDoesNotInheritPreviousPlayersHand() {
        try (PokerTableRuntime table = new PokerTableRuntime("replacement", 1, 50, 2, 60,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper())) {
            table.join(1, "alice", 10_000); table.join(2, "bob", 10_000);
            table.ready(1, true); table.ready(2, true);
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.FOLD));
            table.leave(1); table.join(3, "new-player", 10_000);
            SeatSnapshot replacement = table.snapshot(3, Role.PLAYER).seats().stream()
                .filter(seat -> seat.userId() == 3).findFirst().orElseThrow();
            assertTrue(replacement.holeCards().isEmpty());
            assertEquals(0, replacement.totalContribution());
            assertEquals(com.poker.model.PlayerStatus.WAITING, replacement.handStatus());
        }
    }

    @Test void anotherPlayerDisconnectingDoesNotCancelTurnTimeout() throws Exception {
        try (PokerTableRuntime table = new PokerTableRuntime("timeout", 1, 50, 3, 1,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper())) {
            table.join(1, "alice", 10_000); table.join(2, "bob", 10_000); table.join(3, "carol", 10_000);
            table.ready(1, true); table.ready(2, true); table.ready(3, true);
            long actor = table.snapshot(1, Role.PLAYER).currentTurnUserId();
            table.setConnected(actor == 2 ? 3 : 2, false);
            Thread.sleep(1_250);
            assertNotEquals(actor, table.snapshot(1, Role.PLAYER).currentTurnUserId());
        }
    }

    @Test void reconnectPreservesDeadlineAndDisconnectedPlayerChecksWithoutFacingBet() throws Exception {
        List<Sent> sent = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("reconnect-clock", 1, 50, 2, 1,
            new FixedDeckProvider(deck()), (id, message) -> sent.add(new Sent(id, message)), new ObjectMapper())) {
            table.join(1, "alice", 10_000); table.join(2, "bob", 10_000);
            table.ready(1, true); table.ready(2, true);
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            act(table, 2, com.poker.game.PokerAction.of(com.poker.game.ActionType.CHECK));
            long before = sent.stream().map(Sent::message)
                .filter(message -> message.type() == com.poker.protocol.MessageType.ACTION_REQUIRED)
                .reduce((a, b) -> b).orElseThrow().payload().path("deadlineEpochMs").asLong();
            Thread.sleep(30);
            table.setConnected(2, false); table.setConnected(2, true); table.setConnected(2, false);
            long after = sent.stream().map(Sent::message)
                .filter(message -> message.type() == com.poker.protocol.MessageType.ACTION_REQUIRED)
                .reduce((a, b) -> b).orElseThrow().payload().path("deadlineEpochMs").asLong();
            assertEquals(before, after, "Connection changes must not buy additional turn time");
            Thread.sleep(1_200);
            TableSnapshot state = table.snapshot(1, Role.PLAYER);
            assertEquals(com.poker.model.TableStatus.IN_HAND, state.status());
            assertEquals(com.poker.model.PlayerStatus.DISCONNECTED,
                state.seats().stream().filter(seat -> seat.userId() == 2).findFirst().orElseThrow().handStatus());
            assertEquals(1L, state.currentTurnUserId());
        }
    }

    @Test void projectsHoleCardsByRoleAndRejectsStaleActions() {
        try (PokerTableRuntime table = new PokerTableRuntime("t", 1, 50, 6, 60,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper())) {
            table.join(1, "alice", 10_000);
            table.join(2, "bob", 10_000);
            table.ready(1, true); table.ready(2, true);

            TableSnapshot player = table.snapshot(1, Role.PLAYER);
            assertThrows(IllegalStateException.class, () -> table.ready(1, true));
            TableSnapshot observer = table.snapshot(99, Role.OBSERVER);
            assertEquals(1, player.seats().stream().filter(s -> !s.holeCards().isEmpty()).count());
            assertEquals(2, observer.seats().stream().filter(s -> s.holeCards().size() == 2).count());
            assertThrows(IllegalStateException.class, () -> table.action(player.currentTurnUserId(),
                com.poker.game.PokerAction.of(com.poker.game.ActionType.FOLD), player.stateVersion() - 1));
        }
    }

    @Test void waitsUntilEveryEligiblePlayerIsReady() {
        try (PokerTableRuntime table = new PokerTableRuntime("t", 1, 50, 6, 60,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper())) {
            table.join(1, "alice", 10_000);
            table.join(2, "bob", 10_000);
            table.join(3, "carol", 10_000);
            table.ready(1, true);
            table.ready(2, true);
            assertEquals(com.poker.model.TableStatus.WAITING, table.snapshot(1, Role.PLAYER).status());
            table.ready(3, true);
            assertEquals(com.poker.model.TableStatus.IN_HAND, table.snapshot(1, Role.PLAYER).status());
        }
    }

    @Test void sendsCurrentSnapshotBeforeRequestingAnAction() {
        List<Sent> sent = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("t", 1, 50, 6, 60,
            new FixedDeckProvider(deck()), (id, message) -> sent.add(new Sent(id, message)), new ObjectMapper())) {
            table.join(1, "alice", 10_000);
            table.join(2, "bob", 10_000);
            table.ready(1, true);
            table.ready(2, true);

            int actionIndex = -1;
            for (int i = 0; i < sent.size(); i++) if (sent.get(i).message.type() == com.poker.protocol.MessageType.ACTION_REQUIRED) actionIndex = i;
            assertTrue(actionIndex > 0);
            Sent action = sent.get(actionIndex);
            boolean hasFreshSnapshot = false;
            for (int i = 0; i < actionIndex; i++) {
                Sent event = sent.get(i);
                if (event.userId == action.userId && event.message.type() == com.poker.protocol.MessageType.TABLE_STATE &&
                    event.message.seq() == action.message.seq()) hasFreshSnapshot = true;
            }
            assertTrue(hasFreshSnapshot, "The acting client must receive the current stateVersion before ACTION_REQUIRED");
        }
    }

    @Test void nonCurrentPlayerLeavingDoesNotStealTheTurn() {
        try (PokerTableRuntime table = new PokerTableRuntime("t", 1, 50, 6, 60,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper())) {
            table.join(1, "alice", 10_000);
            table.join(2, "bob", 10_000);
            table.join(3, "carol", 10_000);
            table.ready(1, true); table.ready(2, true); table.ready(3, true);
            long current = table.snapshot(1, Role.PLAYER).currentTurnUserId();
            long leaving = current == 3 ? 2 : 3;
            table.leave(leaving);
            assertEquals(current, table.snapshot(1, Role.PLAYER).currentTurnUserId());
        }
    }

    @Test void cumulativeShortAllInsReopenRaising() {
        try (PokerTableRuntime table = new PokerTableRuntime("t", 1, 10, 6, 60,
            new FixedDeckProvider(deck()), (id, message) -> {}, new ObjectMapper())) {
            table.join(1, "short-one", 150);
            table.join(2, "short-two", 200);
            table.join(3, "caller", 1_000);
            table.join(4, "opener", 1_000);
            table.ready(1, true); table.ready(2, true); table.ready(3, true); table.ready(4, true);

            act(table, 4, new com.poker.game.PokerAction(com.poker.game.ActionType.RAISE, 100));
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.ALL_IN));
            act(table, 2, com.poker.game.PokerAction.of(com.poker.game.ActionType.ALL_IN));
            act(table, 3, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            assertEquals(4, table.snapshot(4, Role.PLAYER).currentTurnUserId());
            assertDoesNotThrow(() -> act(table, 4, new com.poker.game.PokerAction(com.poker.game.ActionType.RAISE, 280)));
        }
    }

    @Test void creditsWinnerAndCanStartTheNextHand() {
        List<Sent> sent = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("t", 1, 50, 6, 60,
            new FixedDeckProvider(deck()), (id, message) -> sent.add(new Sent(id, message)), new ObjectMapper())) {
            table.join(1, "alice", 10_000);
            table.join(2, "bob", 10_000);
            table.ready(1, true); table.ready(2, true);

            TableSnapshot active = table.snapshot(1, Role.PLAYER);
            assertEquals(1, active.pots().size(), "Unequal blinds are one live pot, not a side pot");
            assertEquals(150, active.pots().getFirst().amount());

            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.FOLD));

            TableSnapshot settled = table.snapshot(2, Role.PLAYER);
            assertEquals(com.poker.model.TableStatus.BETWEEN_HANDS, settled.status());
            assertEquals(9_950, settled.seats().stream().filter(s -> s.userId() == 1).findFirst().orElseThrow().stack());
            assertEquals(10_050, settled.seats().stream().filter(s -> s.userId() == 2).findFirst().orElseThrow().stack());
            var result = sent.stream().map(Sent::message)
                .filter(message -> message.type() == com.poker.protocol.MessageType.HAND_RESULT).findFirst().orElseThrow();
            assertEquals(100, result.payload().path("pots").get(0).path("amount").asLong());
            assertEquals(100, result.payload().path("pots").get(0).path("winners").get(0).path("amount").asLong());
            assertEquals(50, result.payload().path("uncalledReturns").get(0).path("amount").asLong());

            table.ready(1, true); table.ready(2, true);
            assertEquals(com.poker.model.TableStatus.IN_HAND, table.snapshot(1, Role.PLAYER).status());
        }
    }

    @Test void playsPreflopFlopTurnRiverAndShowdownWithoutLosingChips() {
        List<Sent> sent = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("streets", 1, 50, 2, 60,
            new FixedDeckProvider(deck()), (id, message) -> sent.add(new Sent(id, message)), new ObjectMapper())) {
            table.join(1, "alice", 10_000); table.join(2, "bob", 10_000);
            table.ready(1, true); table.ready(2, true);
            java.util.Set<com.poker.game.Street> seen = new java.util.LinkedHashSet<>();
            for (int action = 0; action < 12; action++) {
                TableSnapshot state = table.snapshot(1, Role.PLAYER);
                if (state.status() != com.poker.model.TableStatus.IN_HAND) break;
                seen.add(state.street());
                assertEquals(switch (state.street()) {
                    case PRE_FLOP -> 0; case FLOP -> 3; case TURN -> 4; case RIVER -> 5; case SHOWDOWN -> 5;
                }, state.board().size());
                long actor = state.currentTurnUserId();
                var prompt = sent.stream().filter(event -> event.userId() == actor &&
                    event.message().type() == com.poker.protocol.MessageType.ACTION_REQUIRED &&
                    event.message().seq() == state.stateVersion()).reduce((previous, next) -> next).orElseThrow();
                var move = prompt.message().payload().path("toCall").asLong() == 0
                    ? com.poker.game.ActionType.CHECK : com.poker.game.ActionType.CALL;
                table.action(actor, com.poker.game.PokerAction.of(move), state.stateVersion());
            }
            TableSnapshot result = table.snapshot(1, Role.PLAYER);
            assertEquals(com.poker.model.TableStatus.BETWEEN_HANDS, result.status());
            assertEquals(List.of(com.poker.game.Street.PRE_FLOP, com.poker.game.Street.FLOP,
                com.poker.game.Street.TURN, com.poker.game.Street.RIVER), List.copyOf(seen));
            assertEquals(20_000, result.seats().stream().mapToLong(SeatSnapshot::stack).sum());
            assertTrue(sent.stream().anyMatch(event -> event.message().type() == com.poker.protocol.MessageType.HAND_RESULT));
        }
    }

    @Test void shortAllInCreatesMainAndSidePotThroughShowdown() {
        List<Sent> sent = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("side", 1, 50, 3, 60,
            new FixedDeckProvider(deck()), (id, message) -> sent.add(new Sent(id, message)), new ObjectMapper())) {
            table.join(1, "short", 500); table.join(2, "deep-a", 10_000); table.join(3, "deep-b", 10_000);
            table.ready(1, true); table.ready(2, true); table.ready(3, true);
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.ALL_IN));
            act(table, 2, new com.poker.game.PokerAction(com.poker.game.ActionType.RAISE, 1_000));
            act(table, 3, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            TableSnapshot flop = table.snapshot(2, Role.PLAYER);
            assertEquals(com.poker.game.Street.FLOP, flop.street());
            assertEquals(List.of(1_500L, 1_000L), flop.pots().stream().map(com.poker.game.Pot::amount).toList());
            for (int action = 0; action < 6; action++) {
                TableSnapshot state = table.snapshot(2, Role.PLAYER);
                assertEquals(com.poker.model.TableStatus.IN_HAND, state.status());
                act(table, state.currentTurnUserId(), com.poker.game.PokerAction.of(com.poker.game.ActionType.CHECK));
            }
            TableSnapshot result = table.snapshot(2, Role.PLAYER);
            assertEquals(com.poker.model.TableStatus.BETWEEN_HANDS, result.status());
            assertEquals(20_500, result.seats().stream().mapToLong(SeatSnapshot::stack).sum());
            var payout = sent.stream().map(Sent::message)
                .filter(message -> message.type() == com.poker.protocol.MessageType.HAND_RESULT).findFirst().orElseThrow();
            assertEquals(2, payout.payload().path("pots").size());
            assertEquals(2_500, payout.payload().path("pots").get(0).path("amount").asLong() +
                payout.payload().path("pots").get(1).path("amount").asLong());
        }
    }

    @Test void awardsOddChipToFirstWinnerLeftOfDealer() {
        try (PokerTableRuntime table = new PokerTableRuntime("t", 1, 1, 3, 60,
            new FixedDeckProvider(tiedDeck()), (id, message) -> {}, new ObjectMapper())) {
            table.join(1, "dealer", 3);
            table.join(2, "left", 3);
            table.join(3, "loser", 3);
            table.ready(1, true); table.ready(2, true); table.ready(3, true);

            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            act(table, 2, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            act(table, 3, com.poker.game.PokerAction.of(com.poker.game.ActionType.CHECK));
            act(table, 2, com.poker.game.PokerAction.of(com.poker.game.ActionType.ALL_IN));
            act(table, 3, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.CALL));

            TableSnapshot settled = table.snapshot(1, Role.PLAYER);
            assertEquals(4, settled.seats().stream().filter(s -> s.userId() == 1).findFirst().orElseThrow().stack());
            assertEquals(5, settled.seats().stream().filter(s -> s.userId() == 2).findFirst().orElseThrow().stack());
            assertEquals(0, settled.seats().stream().filter(s -> s.userId() == 3).findFirst().orElseThrow().stack());
        }
    }

    @Test void restoresStacksAndDoesNotAnnouncePayoutWhenSettlementFails() {
        List<Sent> sent = new CopyOnWriteArrayList<>();
        try (PokerTableRuntime table = new PokerTableRuntime("t", 1, 50, 2, 60,
            new FixedDeckProvider(deck()), (id, message) -> sent.add(new Sent(id, message)), new ObjectMapper(),
            settlement -> { throw new IllegalStateException("database unavailable"); })) {
            table.join(1, "alice", 10_000);
            table.join(2, "bob", 10_000);
            table.ready(1, true); table.ready(2, true);
            act(table, 1, com.poker.game.PokerAction.of(com.poker.game.ActionType.FOLD));

            TableSnapshot settled = table.snapshot(1, Role.PLAYER);
            assertTrue(settled.seats().stream().allMatch(seat -> seat.stack() == 10_000));
            assertTrue(sent.stream().noneMatch(event -> event.message().type() == com.poker.protocol.MessageType.HAND_RESULT));
            assertTrue(sent.stream().anyMatch(event -> event.message().type() == com.poker.protocol.MessageType.ERROR));
        }
    }

    private void act(PokerTableRuntime table, long userId, com.poker.game.PokerAction action) {
        table.action(userId, action, table.snapshot(userId, Role.PLAYER).stateVersion());
    }

    private List<Card> deck() {
        List<Card> cards = new ArrayList<>();
        for (Suit suit : Suit.values()) for (Rank rank : Rank.values()) cards.add(new Card(rank, suit));
        return cards;
    }

    private List<Card> tiedDeck() {
        return List.of(new Card(Rank.ACE, Suit.HEARTS), new Card(Rank.NINE, Suit.CLUBS), new Card(Rank.ACE, Suit.SPADES),
            new Card(Rank.FOUR, Suit.DIAMONDS), new Card(Rank.EIGHT, Suit.CLUBS), new Card(Rank.THREE, Suit.DIAMONDS),
            new Card(Rank.FIVE, Suit.CLUBS),
            new Card(Rank.ACE, Suit.CLUBS), new Card(Rank.KING, Suit.CLUBS), new Card(Rank.QUEEN, Suit.DIAMONDS),
            new Card(Rank.SIX, Suit.CLUBS), new Card(Rank.SEVEN, Suit.SPADES),
            new Card(Rank.TEN, Suit.CLUBS), new Card(Rank.TWO, Suit.HEARTS));
    }

    private record Sent(long userId, com.poker.protocol.Message message) {}
}
