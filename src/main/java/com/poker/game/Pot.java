package com.poker.game;

import java.util.Set;

public record Pot(long amount, Set<String> eligibleUserIds) {
    public Pot { eligibleUserIds = Set.copyOf(eligibleUserIds); }
}

