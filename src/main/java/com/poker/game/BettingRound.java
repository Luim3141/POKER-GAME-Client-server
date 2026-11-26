package com.poker.game;

public final class BettingRound {
    private final long minimumBet;
    private long currentBet;
    private long lastFullRaise;

    public BettingRound(long minimumBet) {
        if (minimumBet <= 0) throw new IllegalArgumentException("minimumBet");
        this.minimumBet = minimumBet;
        this.lastFullRaise = minimumBet;
    }

    public void postBlind(PlayerHandState player, long amount) {
        if (currentBet > minimumBet) throw new IllegalStateException("Blinds can only be posted before betting");
        if (amount <= 0 || amount > minimumBet) throw new IllegalArgumentException("Invalid blind");
        player.commitChips(amount);
        currentBet = Math.max(currentBet, amount);
    }

    public Result apply(PlayerHandState player, PokerAction action) {
        if (player.status() != com.poker.model.PlayerStatus.ACTIVE &&
            player.status() != com.poker.model.PlayerStatus.DISCONNECTED)
            throw new IllegalStateException("Player cannot act: " + player.status());
        long before = player.streetContribution();
        if ((action.type() == ActionType.BET || action.type() == ActionType.RAISE) && action.amount() > before + player.stack())
            throw new IllegalArgumentException("Wager exceeds available chips");
        long toCall = Math.max(0, currentBet - before);
        boolean fullRaise = false;
        switch (action.type()) {
            case FOLD -> player.fold();
            case CHECK -> { if (toCall != 0) throw new IllegalArgumentException("Cannot check while facing a bet"); }
            case CALL -> { if (toCall == 0) throw new IllegalArgumentException("Nothing to call"); player.commitChips(toCall); }
            case BET -> {
                if (currentBet != 0) throw new IllegalArgumentException("Use raise after a bet");
                if (action.amount() < minimumBet && action.amount() < player.stack()) throw new IllegalArgumentException("Bet below minimum");
                player.commitChips(action.amount());
                currentBet = player.streetContribution();
                fullRaise = currentBet >= minimumBet;
                if (fullRaise) lastFullRaise = currentBet;
            }
            case RAISE -> {
                if (currentBet == 0) throw new IllegalArgumentException("Use bet first");
                long target = action.amount();
                if (target <= currentBet) throw new IllegalArgumentException("Raise target must exceed current bet");
                long raiseSize = target - currentBet;
                boolean allIn = target >= before + player.stack();
                if (raiseSize < lastFullRaise && !allIn) throw new IllegalArgumentException("Raise below minimum");
                player.commitChips(target - before);
                long actualTarget = player.streetContribution();
                long actualRaise = actualTarget - currentBet;
                if (actualTarget > currentBet) {
                    fullRaise = actualRaise >= lastFullRaise;
                    if (fullRaise) lastFullRaise = actualRaise;
                    currentBet = actualTarget;
                }
            }
            case ALL_IN -> {
                player.commitChips(player.stack());
                long actualRaise = player.streetContribution() - currentBet;
                if (actualRaise > 0) {
                    fullRaise = actualRaise >= lastFullRaise;
                    if (fullRaise) lastFullRaise = actualRaise;
                    currentBet = player.streetContribution();
                }
            }
        }
        return new Result(player.streetContribution() - before, currentBet, fullRaise);
    }

    public long toCall(PlayerHandState player) { return Math.max(0, currentBet - player.streetContribution()); }
    public long currentBet() { return currentBet; }
    public long lastFullRaiseSize() { return lastFullRaise; }
    public long minimumRaiseTo() {
        return currentBet == 0 ? minimumBet : currentBet > Long.MAX_VALUE - lastFullRaise
            ? Long.MAX_VALUE : currentBet + lastFullRaise;
    }
    public record Result(long paid, long currentBet, boolean fullRaise) {}
}
