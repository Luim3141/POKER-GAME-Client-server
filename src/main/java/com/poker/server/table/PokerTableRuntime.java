package com.poker.server.table;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.game.ActionType;
import com.poker.game.BettingRound;
import com.poker.game.Deck;
import com.poker.game.DeckProvider;
import com.poker.game.HandEvaluator;
import com.poker.game.HandValue;
import com.poker.game.PlayerHandState;
import com.poker.game.PokerAction;
import com.poker.game.Pot;
import com.poker.game.PotManager;
import com.poker.game.RandomDeckProvider;
import com.poker.game.Street;
import com.poker.model.Card;
import com.poker.model.PlayerStatus;
import com.poker.model.Role;
import com.poker.model.TableStatus;
import com.poker.protocol.Message;
import com.poker.protocol.MessageType;
import com.poker.persistence.HandSettlement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PokerTableRuntime implements AutoCloseable {
    private final String id;
    private final long ownerUserId;
    private final long smallBlind;
    private final long bigBlind;
    private final int maxPlayers;
    private final int turnSeconds;
    private final DeckProvider deckProvider;
    private final BiConsumer<Long, Message> sender;
    private final ObjectMapper mapper;
    private final Consumer<HandSettlement> settlementSink;
    private final BlockingQueue<Runnable> commands = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Thread gameThread;
    private final Map<Integer, TableSeat> seats = new LinkedHashMap<>();
    private final Set<Long> observers = new HashSet<>();
    private final Map<Integer, PlayerHandState> handPlayers = new LinkedHashMap<>();
    private final List<Card> board = new ArrayList<>();
    private final Set<Integer> pendingToAct = new LinkedHashSet<>();
    private final Set<Integer> raiseAllowed = new HashSet<>();
    private final Map<Integer, Long> lastActedBet = new HashMap<>();
    private volatile boolean running = true;
    private TableStatus status = TableStatus.WAITING;
    private Street street;
    private Deck deck;
    private BettingRound betting;
    private String handId;
    private int dealerSeat = -1;
    private int smallBlindSeat = -1;
    private int bigBlindSeat = -1;
    private long previousBigBlindUserId = -1;
    private int previousHandPlayers;
    private int currentSeat = -1;
    private long stateVersion;
    private long turnDeadlineEpochMs;
    private long turnGeneration;
    private ScheduledFuture<?> turnTimeout;
    private final Map<Integer, Long> startingStacks = new HashMap<>();
    private final Set<Long> winners = new LinkedHashSet<>();
    private final Map<Long, Long> payouts = new LinkedHashMap<>();
    private final Map<Long, Long> uncalledReturns = new LinkedHashMap<>();
    private final List<PotAward> potAwards = new ArrayList<>();
    private final List<HandSettlement.ActionRecord> actionLog = new ArrayList<>();
    private boolean reachedShowdown;
    private com.poker.game.HandPhase phase;
    private final boolean pacedDeals;
    private ScheduledFuture<?> dealTimeout;
    private long dealGeneration, dealEndsNanos, dealDurationMillis, publicActionSequence;
    private int dealCardCount;
    private final List<TableAction> recentActions = new ArrayList<>();

    public PokerTableRuntime(String id, long ownerUserId, long smallBlind, int maxPlayers, int turnSeconds,
                             BiConsumer<Long, Message> sender, ObjectMapper mapper) {
        this(id, ownerUserId, smallBlind, maxPlayers, turnSeconds, new RandomDeckProvider(), sender, mapper, ignored -> {}, true);
    }

    public PokerTableRuntime(String id, long ownerUserId, long smallBlind, int maxPlayers, int turnSeconds,
                             BiConsumer<Long, Message> sender, ObjectMapper mapper, Consumer<HandSettlement> settlementSink) {
        this(id, ownerUserId, smallBlind, maxPlayers, turnSeconds, new RandomDeckProvider(), sender, mapper, settlementSink, true);
    }

    PokerTableRuntime(String id, long ownerUserId, long smallBlind, int maxPlayers, int turnSeconds,
                      DeckProvider deckProvider, BiConsumer<Long, Message> sender, ObjectMapper mapper) {
        this(id, ownerUserId, smallBlind, maxPlayers, turnSeconds, deckProvider, sender, mapper, ignored -> {});
    }

    PokerTableRuntime(String id, long ownerUserId, long smallBlind, int maxPlayers, int turnSeconds,
                      DeckProvider deckProvider, BiConsumer<Long, Message> sender, ObjectMapper mapper,
                      Consumer<HandSettlement> settlementSink) {
        // Existing deterministic engine fixtures omit presentation waits; production always paces deals.
        this(id, ownerUserId, smallBlind, maxPlayers, turnSeconds, deckProvider, sender, mapper, settlementSink, false);
    }

    PokerTableRuntime(String id, long ownerUserId, long smallBlind, int maxPlayers, int turnSeconds,
                      DeckProvider deckProvider, BiConsumer<Long, Message> sender, ObjectMapper mapper,
                      Consumer<HandSettlement> settlementSink, boolean pacedDeals) {
        if (smallBlind <= 0 || smallBlind > Long.MAX_VALUE / 2 || maxPlayers < 2 || maxPlayers > 8)
            throw new IllegalArgumentException("Invalid table settings");
        this.id = id; this.ownerUserId = ownerUserId; this.smallBlind = smallBlind; this.bigBlind = smallBlind * 2;
        this.maxPlayers = maxPlayers; this.turnSeconds = turnSeconds; this.deckProvider = deckProvider;
        this.sender = sender; this.mapper = mapper; this.settlementSink = settlementSink;
        this.pacedDeals = pacedDeals;
        gameThread = Thread.ofPlatform().name("poker-table-" + id).start(this::runLoop);
    }

    public String id() { return id; }
    public long ownerUserId() { return ownerUserId; }

    public TableSnapshot join(long userId, String username, long stack) {
        return join(userId, username, stack, null);
    }

    public TableSnapshot join(long userId, String username, long stack, com.poker.model.GlorySummary glory) {
        return call(() -> {
            if (findSeat(userId) != null) throw new IllegalStateException("Already seated");
            if (status == TableStatus.IN_HAND) throw new IllegalStateException("Hand already running");
            if (stack < 0) throw new IllegalArgumentException("Invalid stack");
            long totalStacks = stack;
            for (TableSeat existing : seats.values()) {
                if (existing.stack > Long.MAX_VALUE - totalStacks)
                    throw new IllegalArgumentException("Combined table stacks exceed chip limit");
                totalStacks += existing.stack;
            }
            int seat = firstFreeSeat();
            if (seat < 0) throw new IllegalStateException("Table full");
            TableSeat joined = new TableSeat(userId, username, seat, stack);
            joined.glory = glory;
            seats.put(seat, joined); changed(); broadcastState();
            return snapshotFor(userId, Role.PLAYER);
        });
    }

    public boolean leave(long userId) {
        return call(() -> {
            TableSeat seat = findSeat(userId);
            if (seat == null) return true;
            if (status == TableStatus.IN_HAND) {
                PlayerHandState player = handPlayers.get(seat.seat);
                if (player != null && player.status() != PlayerStatus.FOLDED) {
                    player.fold(); changed(); publishAction(seat.seat, "FOLD", 0, true);
                    actionLog.add(new HandSettlement.ActionRecord(userId, actionLog.size() + 1, street.name(), "FOLD", 0));
                }
                boolean wasCurrent = currentSeat == seat.seat;
                seat.leaveAfterHand = true; pendingToAct.remove(seat.seat); changed();
                if (phase == com.poker.game.HandPhase.DEALING) {
                    var contenders = handPlayers.values().stream().filter(PlayerHandState::eligibleForPot).toList();
                    if (contenders.size() == 1) awardUncontested(contenders.getFirst());
                    else broadcastState();
                    return false;
                }
                if (wasCurrent) advanceAfterAction(seat.seat);
                else {
                    List<PlayerHandState> contenders = handPlayers.values().stream().filter(PlayerHandState::eligibleForPot).toList();
                    if (contenders.size() == 1) awardUncontested(contenders.getFirst());
                    else if (pendingToAct.isEmpty()) advanceStreet();
                    else { broadcastState(); sendActionRequired(); }
                }
                return false;
            }
            seats.remove(seat.seat); changed(); broadcastState();
            return true;
        });
    }

    public boolean hasSeat(long userId) { return call(() -> findSeat(userId) != null); }
    public boolean isUnused() { return call(() -> seats.isEmpty() && observers.isEmpty()); }

    public void setConnected(long userId, boolean connected) {
        call(() -> {
            TableSeat seat = findSeat(userId);
            if (seat != null) {
                if (seat.connected == connected) return null;
                if (!connected && status != TableStatus.IN_HAND) {
                    seats.remove(seat.seat); changed(); broadcastState(); return null;
                }
                seat.connected = connected;
                PlayerHandState player = handPlayers.get(seat.seat);
                if (player != null) { if (connected) player.reconnect(); else player.disconnect(); }
                changed();
                broadcastState();
                if (status == TableStatus.IN_HAND) sendActionRequired();
            }
            return null;
        });
    }

    public void ready(long userId, boolean ready) {
        call(() -> {
            if (status == TableStatus.IN_HAND) throw new IllegalStateException("Cannot change Ready during a hand");
            TableSeat seat = requireSeat(userId); seat.ready = ready; changed();
            if (status != TableStatus.IN_HAND && readyToStart()) startHand();
            else broadcastState();
            return null;
        });
    }

    public void spectate(long userId) { call(() -> { observers.add(userId); sendSnapshot(userId, Role.OBSERVER); return null; }); }
    public void stopSpectating(long userId) { call(() -> { observers.remove(userId); return null; }); }

    public void action(long userId, PokerAction action, long expectedVersion) {
        call(() -> {
            if (status != TableStatus.IN_HAND) throw new IllegalStateException("No active hand");
            if (phase == com.poker.game.HandPhase.DEALING) throw new IllegalStateException("Dealing cards; betting is not ready");
            TableSeat seat = requireSeat(userId);
            if (seat.seat != currentSeat) throw new IllegalStateException("Not your turn");
            if (!seat.connected) throw new IllegalStateException("Disconnected player cannot act");
            if (expectedVersion != stateVersion) throw new IllegalStateException("Stale table state");
            PlayerHandState player = handPlayers.get(currentSeat);
            boolean raises = action.type() == ActionType.RAISE ||
                (action.type() == ActionType.ALL_IN && player.stack() > betting.toCall(player));
            if (raises && !raiseAllowed.contains(currentSeat))
                throw new IllegalArgumentException("A short all-in did not reopen raising");
            applyAction(seat.seat, action);
            return null;
        });
    }

    public TableSnapshot snapshot(long userId, Role role) { return call(() -> snapshotFor(userId, role)); }

    private void startHand() {
        status = TableStatus.IN_HAND; street = Street.PRE_FLOP; handId = UUID.randomUUID().toString(); board.clear(); handPlayers.clear();
        startingStacks.clear(); winners.clear(); payouts.clear(); uncalledReturns.clear(); potAwards.clear(); actionLog.clear(); reachedShowdown = false;
        recentActions.clear(); publicActionSequence = 0;
        phase = com.poker.game.HandPhase.DEALING; currentSeat = -1;
        seats.values().forEach(s -> s.ready = false);
        List<Integer> activeSeats = seats.values().stream().filter(s -> s.stack > 0 && s.connected).map(s -> s.seat).sorted().toList();
        dealerSeat = nextIn(activeSeats, dealerSeat);
        // On entering heads-up, adjust the button so the previous BB does not pay it twice.
        if (activeSeats.size() == 2 && previousHandPlayers > 2) {
            int prospectiveBig = nextIn(activeSeats, dealerSeat);
            if (seats.get(prospectiveBig).userId == previousBigBlindUserId) dealerSeat = prospectiveBig;
        }
        smallBlindSeat = activeSeats.size() == 2 ? dealerSeat : nextIn(activeSeats, dealerSeat);
        bigBlindSeat = nextIn(activeSeats, smallBlindSeat);
        previousBigBlindUserId = seats.get(bigBlindSeat).userId;
        previousHandPlayers = activeSeats.size();
        deck = new Deck(deckProvider);
        List<Integer> dealOrder = activeSeats.stream().sorted(Comparator.comparingInt(seat -> clockwiseDistance(dealerSeat, seat))).toList();
        Map<Integer, Card> firstCards = new HashMap<>();
        for (int seatNo : dealOrder) firstCards.put(seatNo, deck.deal());
        for (int seatNo : dealOrder) {
            TableSeat seat = seats.get(seatNo);
            startingStacks.put(seatNo, seat.stack);
            PlayerHandState player = new PlayerHandState(Long.toString(seat.userId), seatNo, seat.stack);
            player.setHoleCards(List.of(firstCards.get(seatNo), deck.deal())); handPlayers.put(seatNo, player); seat.stack = 0;
        }
        betting = new BettingRound(bigBlind);
        betting.postBlind(handPlayers.get(smallBlindSeat), smallBlind);
        betting.postBlind(handPlayers.get(bigBlindSeat), bigBlind);
        resetPending(); changed(); broadcast(MessageType.HAND_STARTED);
        // Build seats/cards before public blind animations are delivered.
        beginDeal(activeSeats.size() * 2, bigBlindSeat, false);
    }

    private void applyAction(int seatNo, PokerAction action) {
        applyAction(seatNo, action, false);
    }

    private void applyAction(int seatNo, PokerAction action, boolean automatic) {
        long beforeBet = betting.currentBet();
        var result = betting.apply(handPlayers.get(seatNo), action);
        actionLog.add(new HandSettlement.ActionRecord(seats.get(seatNo).userId, actionLog.size() + 1,
            street.name(), action.type().name(), result.paid()));
        pendingToAct.remove(seatNo); raiseAllowed.remove(seatNo);
        if (result.currentBet() > beforeBet) {
            handPlayers.forEach((number, player) -> {
                if (number != seatNo && canAct(player) && player.streetContribution() < result.currentBet()) pendingToAct.add(number);
            });
            if (result.fullRaise()) {
                raiseAllowed.clear();
                handPlayers.forEach((number, player) -> { if (number != seatNo && canAct(player)) raiseAllowed.add(number); });
            } else {
                lastActedBet.forEach((number, facedBet) -> {
                    if (number != seatNo && canAct(handPlayers.get(number)) &&
                        result.currentBet() - facedBet >= betting.lastFullRaiseSize()) raiseAllowed.add(number);
                });
            }
        }
        lastActedBet.put(seatNo, result.currentBet());
        changed(); publishAction(seatNo, action.type().name(), result.paid(), automatic); advanceAfterAction(seatNo);
    }

    private void advanceAfterAction(int previousSeat) {
        List<PlayerHandState> contenders = handPlayers.values().stream().filter(PlayerHandState::eligibleForPot).toList();
        if (contenders.size() == 1) { awardUncontested(contenders.getFirst()); return; }
        pendingToAct.removeIf(seat -> !canAct(handPlayers.get(seat)));
        if (pendingToAct.isEmpty()) { advanceStreet(); return; }
        currentSeat = nextPending(previousSeat); scheduleTurn(); broadcastState(); sendActionRequired();
    }

    private void advanceStreet() {
        returnUncalledBet();
        handPlayers.values().forEach(PlayerHandState::resetStreet);
        betting = new BettingRound(bigBlind);
        switch (street) {
            case PRE_FLOP -> { street = Street.FLOP; deck.deal(); board.addAll(deck.deal(3)); }
            case FLOP -> { street = Street.TURN; deck.deal(); board.add(deck.deal()); }
            case TURN -> { street = Street.RIVER; deck.deal(); board.add(deck.deal()); }
            case RIVER -> { street = Street.SHOWDOWN; showdown(); return; }
            case SHOWDOWN -> { showdown(); return; }
        }
        resetPending(); changed();
        beginDeal(street == Street.FLOP ? 3 : 1, dealerSeat, true);
    }

    private void beginDeal(int cards, int afterSeat, boolean community) {
        cancelDeal();
        if (turnTimeout != null) turnTimeout.cancel(false);
        turnGeneration++; currentSeat = -1; turnDeadlineEpochMs = 0;
        phase = com.poker.game.HandPhase.DEALING; dealCardCount = cards;
        dealDurationMillis = pacedDeals ? com.poker.common.DealTiming.duration(cards) : 0;
        dealEndsNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(dealDurationMillis);
        long generation = dealGeneration; String expectedHand = handId;
        if (community) broadcast(MessageType.COMMUNITY_UPDATE);
        broadcastState();
        if (!community) {
            publishAction(smallBlindSeat, "SMALL_BLIND", handPlayers.get(smallBlindSeat).streetContribution(), true);
            publishAction(bigBlindSeat, "BIG_BLIND", handPlayers.get(bigBlindSeat).streetContribution(), true);
            broadcastState();
        }
        Runnable finish = () -> {
            if (!running || status != TableStatus.IN_HAND || generation != dealGeneration ||
                !expectedHand.equals(handId) || phase != com.poker.game.HandPhase.DEALING) return;
            dealTimeout = null; phase = com.poker.game.HandPhase.BETTING; changed();
            resetPending(); // Leaves during DEALING may remove the last opponent who could still bet.
            if (pendingToAct.isEmpty()) advanceStreet();
            else { currentSeat = nextPending(afterSeat); scheduleTurn(); broadcastState(); sendActionRequired(); }
        };
        if (pacedDeals) dealTimeout = scheduler.schedule(() -> commands.offer(finish),
            Math.max(0, dealEndsNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
        else finish.run();
    }

    private void cancelDeal() {
        if (dealTimeout != null) dealTimeout.cancel(false);
        dealTimeout = null; dealGeneration++;
    }

    private void publishAction(int seatNo, String action, long paid, boolean automatic) {
        TableAction event = new TableAction(++publicActionSequence, seats.get(seatNo).userId, street, action,
            paid, handPlayers.get(seatNo).streetContribution(), automatic);
        recentActions.add(event); if (recentActions.size() > 16) recentActions.removeFirst();
        Message message = Message.event(MessageType.TABLE_ACTION, id, handId, stateVersion, mapper.valueToTree(event));
        seats.values().stream().filter(seat -> !seat.leaveAfterHand).forEach(seat -> sender.accept(seat.userId, message));
        observers.forEach(user -> sender.accept(user, message));
    }

    private void showdown() {
        reachedShowdown = true;
        HandEvaluator evaluator = new HandEvaluator();
        Map<String, HandValue> values = new HashMap<>();
        handPlayers.values().stream().filter(PlayerHandState::eligibleForPot).forEach(player -> {
            List<Card> cards = new ArrayList<>(player.holeCards()); cards.addAll(board);
            values.put(player.userId(), evaluator.evaluate(cards));
        });
        for (Pot pot : new PotManager().buildPots(new ArrayList<>(handPlayers.values()))) {
            HandValue best = pot.eligibleUserIds().stream().map(values::get).max(Comparator.naturalOrder()).orElseThrow();
            List<String> potWinners = pot.eligibleUserIds().stream().filter(id -> values.get(id).equals(best))
                .sorted(Comparator.comparingInt(id -> clockwiseDistance(dealerSeat, handPlayers.values().stream().filter(p -> p.userId().equals(id)).findFirst().orElseThrow().seat()))).toList();
            awardPot(pot, potWinners);
        }
        finishHand();
    }

    private void awardUncontested(PlayerHandState winner) {
        returnUncalledBet();
        long total = handPlayers.values().stream().mapToLong(PlayerHandState::totalContribution).sum();
        awardPot(new Pot(total, Set.of(winner.userId())), List.of(winner.userId()));
        finishHand();
    }

    private void returnUncalledBet() {
        List<PlayerHandState> contributors = handPlayers.values().stream()
            .sorted(Comparator.comparingLong(PlayerHandState::streetContribution).reversed()).toList();
        if (contributors.isEmpty()) return;
        PlayerHandState highest = contributors.getFirst();
        long matched = contributors.size() > 1 ? contributors.get(1).streetContribution() : 0;
        long refund = highest.streetContribution() - matched;
        if (refund > 0) {
            highest.refundUncalledChips(refund);
            uncalledReturns.merge(Long.parseLong(highest.userId()), refund, Long::sum);
        }
    }

    private void awardPot(Pot pot, List<String> potWinners) {
        long share = pot.amount() / potWinners.size(), remainder = pot.amount() % potWinners.size();
        Map<Long, Long> awards = new LinkedHashMap<>();
        for (int i = 0; i < potWinners.size(); i++) {
            long amount = share + (i < remainder ? 1 : 0);
            long userId = Long.parseLong(potWinners.get(i));
            addWinnings(potWinners.get(i), amount);
            awards.put(userId, amount);
        }
        potAwards.add(new PotAward(pot.amount(), pot.eligibleUserIds().stream().map(Long::parseLong).toList(), awards));
    }

    private void addWinnings(String userId, long amount) {
        TableSeat seat = seats.values().stream().filter(s -> Long.toString(s.userId).equals(userId)).findFirst().orElseThrow();
        seat.stack += amount; winners.add(seat.userId); payouts.merge(seat.userId, amount, Long::sum);
    }

    private void finishHand() {
        cancelDeal(); phase = null;
        for (var entry : handPlayers.entrySet()) seats.get(entry.getKey()).stack += entry.getValue().stack();
        boolean settled = true;
        try {
            List<HandSettlement.PlayerResult> results = handPlayers.entrySet().stream().map(entry -> {
                TableSeat seat = seats.get(entry.getKey()); PlayerHandState player = entry.getValue();
                return new HandSettlement.PlayerResult(seat.userId, seat.seat, startingStacks.get(seat.seat), seat.stack,
                    player.totalContribution(), winners.contains(seat.userId));
            }).toList();
            settlementSink.accept(new HandSettlement(handId, id, dealerSeat, mapper.writeValueAsString(board), results, actionLog));
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException error) {
            settled = false;
            payouts.clear(); winners.clear(); potAwards.clear();
            seats.values().forEach(seat -> { Long original = startingStacks.get(seat.seat); if (original != null) seat.stack = original; });
            broadcastError("Hand settlement failed; chip balances were restored");
        }
        if (turnTimeout != null) turnTimeout.cancel(false);
        turnGeneration++;
        currentSeat = -1; pendingToAct.clear(); status = TableStatus.BETWEEN_HANDS; changed();
        broadcast(MessageType.SHOWDOWN);
        if (settled) {
            // Per-seat public cache seeded from DB on admission; no JDBC calls per snapshot/action.
            for (TableSeat seat : seats.values()) if (startingStacks.containsKey(seat.seat) && seat.glory != null)
                seat.glory = com.poker.model.GlorySummary.from(seat.stack,
                    seat.glory.wins() + (winners.contains(seat.userId) ? 1 : 0), seat.glory.gamesPlayed() + 1);
            broadcastResult();
        }
        // Preserve offline hands through settlement, not as permanent reservations for the next hand.
        if (seats.entrySet().removeIf(entry -> entry.getValue().leaveAfterHand || !entry.getValue().connected)) changed();
        broadcastState();
    }

    private void resetPending() {
        pendingToAct.clear(); raiseAllowed.clear(); lastActedBet.clear();
        handPlayers.forEach((seat, player) -> { if (canAct(player)) { pendingToAct.add(seat); raiseAllowed.add(seat); } });
        if (pendingToAct.size() == 1) {
            int onlySeat = pendingToAct.iterator().next();
            boolean opponentAllIn = handPlayers.values().stream().anyMatch(player -> player.status() == PlayerStatus.ALL_IN);
            if (opponentAllIn && betting.toCall(handPlayers.get(onlySeat)) == 0) {
                pendingToAct.clear(); raiseAllowed.clear();
            }
        }
    }

    private void scheduleTurn() {
        if (turnTimeout != null) turnTimeout.cancel(false);
        long expectedGeneration = ++turnGeneration;
        int expectedSeat = currentSeat;
        String expectedHand = handId;
        turnDeadlineEpochMs = Instant.now().plusSeconds(turnSeconds).toEpochMilli();
        turnTimeout = scheduler.schedule(() -> commands.offer(() -> {
            if (running && status == TableStatus.IN_HAND && currentSeat == expectedSeat &&
                turnGeneration == expectedGeneration && expectedHand.equals(handId)) {
                PlayerHandState player = handPlayers.get(currentSeat);
                applyAction(currentSeat, PokerAction.of(betting.toCall(player) == 0 ? ActionType.CHECK : ActionType.FOLD), true);
            }
        }), turnSeconds, TimeUnit.SECONDS);
    }

    private void sendActionRequired() {
        if (phase == com.poker.game.HandPhase.BETTING && seats.containsKey(currentSeat)) {
            PlayerHandState player = handPlayers.get(currentSeat);
            sender.accept(seats.get(currentSeat).userId, Message.event(MessageType.ACTION_REQUIRED, id, handId, stateVersion,
                mapper.createObjectNode().put("toCall", betting.toCall(player)).put("minimumRaiseTo", betting.minimumRaiseTo())
                    .put("currentBet", betting.currentBet()).put("canRaise", raiseAllowed.contains(currentSeat))
                    .put("stack", player.stack()).put("streetContribution", player.streetContribution())
                    .put("deadlineEpochMs", turnDeadlineEpochMs)));
        }
    }

    private void broadcastState() {
        seats.values().stream().filter(seat -> !seat.leaveAfterHand).forEach(seat -> sendSnapshot(seat.userId, Role.PLAYER));
        observers.forEach(userId -> sendSnapshot(userId, Role.OBSERVER));
    }

    private void sendSnapshot(long userId, Role role) {
        sender.accept(userId, Message.event(MessageType.TABLE_STATE, id, handId, stateVersion, mapper.valueToTree(snapshotFor(userId, role))));
    }

    private void broadcast(MessageType type) {
        Message event = Message.event(type, id, handId, stateVersion, mapper.createObjectNode());
        seats.values().stream().filter(seat -> !seat.leaveAfterHand).forEach(seat -> sender.accept(seat.userId, event));
        observers.forEach(id -> sender.accept(id, event));
    }

    private void broadcastResult() {
        var payload = mapper.createObjectNode();
        payload.put("reason", reachedShowdown ? "SHOWDOWN" : "UNCONTESTED_FOLD");
        var returnedList = payload.putArray("uncalledReturns");
        uncalledReturns.forEach((userId, amount) -> returnedList.addObject()
            .put("userId", userId).put("username", findSeat(userId).username).put("amount", amount));
        var winnerList = payload.putArray("winners");
        payouts.forEach((userId, amount) -> {
            TableSeat seat = findSeat(userId);
            winnerList.addObject().put("userId", userId).put("username", seat.username)
                .put("amount", amount).put("finalStack", seat.stack);
        });
        var potList = payload.putArray("pots");
        for (int i = 0; i < potAwards.size(); i++) {
            PotAward award = potAwards.get(i);
            var item = potList.addObject().put("name", i == 0 ? "MAIN POT" : "SIDE POT " + i).put("amount", award.amount());
            item.set("eligibleUserIds", mapper.valueToTree(award.eligibleUserIds()));
            var potWinners = item.putArray("winners");
            award.payouts().forEach((userId, amount) -> {
                TableSeat seat = findSeat(userId);
                potWinners.addObject().put("userId", userId).put("username", seat.username)
                    .put("amount", amount).put("finalStack", seat.stack);
            });
        }
        var revealed = payload.putArray("showdown");
        if (reachedShowdown) handPlayers.forEach((seatNo, player) -> {
            if (player.eligibleForPot()) {
                TableSeat seat = seats.get(seatNo);
                var item = revealed.addObject().put("userId", seat.userId).put("username", seat.username);
                item.set("holeCards", mapper.valueToTree(player.holeCards()));
            }
        });
        Message event = Message.event(MessageType.HAND_RESULT, id, handId, stateVersion, payload);
        seats.values().stream().filter(seat -> !seat.leaveAfterHand).forEach(seat -> sender.accept(seat.userId, event));
        observers.forEach(userId -> sender.accept(userId, event));
    }

    private void broadcastError(String detail) {
        Message event = Message.event(MessageType.ERROR, id, handId, stateVersion,
            mapper.createObjectNode().put("code", "SERVER_ERROR").put("message", detail));
        seats.values().stream().filter(seat -> !seat.leaveAfterHand).forEach(seat -> sender.accept(seat.userId, event));
        observers.forEach(userId -> sender.accept(userId, event));
    }

    private TableSnapshot snapshotFor(long viewerId, Role role) {
        List<SeatSnapshot> snapshots = seats.values().stream().map(seat -> {
            PlayerHandState hand = handPlayers.get(seat.seat);
            if (hand != null && !hand.userId().equals(Long.toString(seat.userId))) hand = null;
            boolean showCards = hand != null && (role == Role.OBSERVER || seat.userId == viewerId);
            return new SeatSnapshot(seat.userId, seat.username, seat.seat,
                hand == null || status != TableStatus.IN_HAND ? seat.stack : hand.stack(),
                hand == null ? 0 : hand.streetContribution(), hand == null ? 0 : hand.totalContribution(), seat.ready,
                seat.connected, hand == null ? PlayerStatus.WAITING : hand.status(), showCards ? hand.holeCards() : List.of(), seat.glory);
        }).toList();
        List<Pot> pots = snapshotPots();
        Long currentUser = currentSeat < 0 || !seats.containsKey(currentSeat) ? null : seats.get(currentSeat).userId;
        int remaining = currentUser == null ? 0 : Math.max(0, (int) ((turnDeadlineEpochMs - System.currentTimeMillis() + 999) / 1000));
        long dealRemaining = phase == com.poker.game.HandPhase.DEALING ?
            Math.max(0, TimeUnit.NANOSECONDS.toMillis(dealEndsNanos - System.nanoTime())) : 0;
        return new TableSnapshot(id, role, status, smallBlind, bigBlind, maxPlayers, dealerSeat, street, board, snapshots,
            pots, currentUser, remaining, stateVersion, handId, smallBlindSeat, bigBlindSeat,
            phase, dealDurationMillis, dealRemaining, dealCardCount, recentActions,
            status == TableStatus.BETWEEN_HANDS ? payouts : Map.of());
    }

    private int firstFreeSeat() { for (int i = 0; i < maxPlayers; i++) if (!seats.containsKey(i)) return i; return -1; }
    private List<Pot> snapshotPots() {
        if (handPlayers.isEmpty()) return List.of();
        if (handPlayers.values().stream().anyMatch(player -> player.status() == PlayerStatus.ALL_IN))
            return new PotManager().buildPots(new ArrayList<>(handPlayers.values()));
        long total = handPlayers.values().stream().mapToLong(PlayerHandState::totalContribution).sum();
        Set<String> eligible = new LinkedHashSet<>();
        handPlayers.values().stream().filter(PlayerHandState::eligibleForPot).forEach(player -> eligible.add(player.userId()));
        return total == 0 ? List.of() : List.of(new Pot(total, eligible));
    }
    private boolean readyToStart() {
        List<TableSeat> eligible = seats.values().stream().filter(seat -> seat.connected && seat.stack > 0).toList();
        return eligible.size() >= 2 && eligible.stream().allMatch(seat -> seat.ready);
    }
    private TableSeat findSeat(long userId) { return seats.values().stream().filter(s -> s.userId == userId).findFirst().orElse(null); }
    private TableSeat requireSeat(long userId) { TableSeat seat = findSeat(userId); if (seat == null) throw new IllegalStateException("Not seated"); return seat; }
    private int nextPending(int after) { return clockwiseAfter(new ArrayList<>(pendingToAct), after); }
    private int nextIn(List<Integer> choices, int after) { return clockwiseAfter(choices, after); }
    private int clockwiseAfter(List<Integer> choices, int after) {
        return choices.stream().filter(s -> s > after).min(Integer::compareTo).orElseGet(() -> choices.stream().min(Integer::compareTo).orElseThrow());
    }
    private int clockwiseDistance(int from, int to) {
        int distance = (to - from + maxPlayers) % maxPlayers;
        return distance == 0 ? maxPlayers : distance;
    }
    private boolean canAct(PlayerHandState player) { return player.status() == PlayerStatus.ACTIVE || player.status() == PlayerStatus.DISCONNECTED; }
    private void changed() { stateVersion++; }

    private record PotAward(long amount, List<Long> eligibleUserIds, Map<Long, Long> payouts) {
        private PotAward { eligibleUserIds = List.copyOf(eligibleUserIds); payouts = Map.copyOf(payouts); }
    }

    private <T> T call(Callable<T> task) {
        if (!running) throw new IllegalStateException("Table is closed");
        if (Thread.currentThread() == gameThread) {
            try { return task.call(); }
            catch (RuntimeException error) { throw error; }
            catch (Exception error) { throw new IllegalStateException(error); }
        }
        QueuedCall<T> command = new QueuedCall<>(task);
        commands.offer(command);
        if (!running) command.cancelBeforeStart("Table is closed");
        try { return command.result.get(5, TimeUnit.SECONDS); }
        catch (TimeoutException error) {
            if (command.cancelBeforeStart("Table command timed out before starting")) {
                commands.remove(command);
                throw new IllegalStateException("Table command timed out before starting", error);
            }
            // A started mutation may commit; report its actual outcome instead of a false timeout.
            return awaitStartedCommand(command);
        } catch (InterruptedException error) {
            try {
                if (command.cancelBeforeStart("Table command interrupted before starting")) {
                    commands.remove(command);
                    throw new IllegalStateException("Table command interrupted before starting", error);
                }
                return awaitStartedCommand(command);
            } finally { Thread.currentThread().interrupt(); }
        } catch (ExecutionException error) { throw commandFailure(error.getCause()); }
    }

    private <T> T awaitStartedCommand(QueuedCall<T> command) {
        try { return command.result.join(); }
        catch (CompletionException error) { throw commandFailure(error.getCause()); }
    }

    private RuntimeException commandFailure(Throwable error) {
        return error instanceof RuntimeException runtime ? runtime : new IllegalStateException(error);
    }

    private final class QueuedCall<T> implements Runnable {
        private final AtomicReference<Callable<T>> pending;
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private QueuedCall(Callable<T> task) { pending = new AtomicReference<>(task); }
        private boolean cancelBeforeStart(String reason) {
            if (pending.getAndSet(null) == null) return false;
            result.completeExceptionally(new IllegalStateException(reason));
            return true;
        }
        @Override public void run() {
            if (!running) { cancelBeforeStart("Table is closed"); return; }
            Callable<T> task = pending.getAndSet(null);
            if (task == null) return;
            try { result.complete(task.call()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        }
    }

    private void runLoop() {
        while (running) try { commands.take().run(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        catch (RuntimeException ignored) { /* command futures carry caller-visible errors */ }
    }

    @Override public void close() {
        running = false;
        scheduler.shutdownNow();
        Runnable command;
        while ((command = commands.poll()) != null) {
            if (command instanceof PokerTableRuntime.QueuedCall<?> queued) queued.cancelBeforeStart("Table is closed");
        }
        gameThread.interrupt();
    }
}
