package com.poker.game;

import java.util.Objects;

public record PokerAction(ActionType type, long amount) {
    public PokerAction { Objects.requireNonNull(type, "type"); if (amount < 0) throw new IllegalArgumentException("amount"); }
    public static PokerAction of(ActionType type) { return new PokerAction(type, 0); }
}

