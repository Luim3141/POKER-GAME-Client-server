package com.poker.game;

import com.poker.model.Card;
import com.poker.model.PlayerStatus;
import java.util.List;
import java.util.Objects;

public final class PlayerHandState {
    private final String userId;
    private final int seat;
    private long stack;
    private long streetContribution;
    private long totalContribution;
    private PlayerStatus status = PlayerStatus.ACTIVE;
    private List<Card> holeCards = List.of();

    public PlayerHandState(String userId, int seat, long stack) {
        this.userId = Objects.requireNonNull(userId, "userId");
        if (seat < 0 || stack < 0) throw new IllegalArgumentException("Invalid seat or stack");
        this.seat = seat;
        this.stack = stack;
    }

    public long commitChips(long requested) {
        if (requested < 0) throw new IllegalArgumentException("requested");
        long paid = Math.min(requested, stack);
        stack -= paid;
        streetContribution += paid;
        totalContribution += paid;
        if (stack == 0) status = PlayerStatus.ALL_IN;
        return paid;
    }

    public void refundUncalledChips(long amount) {
        if (amount <= 0 || amount > streetContribution) throw new IllegalArgumentException("Invalid uncalled refund");
        stack += amount;
        streetContribution -= amount;
        totalContribution -= amount;
        if (status == PlayerStatus.ALL_IN) status = PlayerStatus.ACTIVE;
    }

    public void fold() { status = PlayerStatus.FOLDED; }
    public void disconnect() { if (status == PlayerStatus.ACTIVE) status = PlayerStatus.DISCONNECTED; }
    public void reconnect() { if (status == PlayerStatus.DISCONNECTED) status = PlayerStatus.ACTIVE; }
    public void resetStreet() { streetContribution = 0; }
    public void setHoleCards(List<Card> cards) {
        if (cards.size() != 2) throw new IllegalArgumentException("A player needs two hole cards");
        holeCards = List.copyOf(cards);
    }
    public String userId() { return userId; }
    public int seat() { return seat; }
    public long stack() { return stack; }
    public long streetContribution() { return streetContribution; }
    public long totalContribution() { return totalContribution; }
    public PlayerStatus status() { return status; }
    public List<Card> holeCards() { return holeCards; }
    public boolean eligibleForPot() { return status != PlayerStatus.FOLDED; }
}

