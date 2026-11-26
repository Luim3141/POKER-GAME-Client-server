package com.poker.game;

import java.util.List;
import java.util.Objects;

public record HandValue(HandCategory category, List<Integer> tieBreakRanks) implements Comparable<HandValue> {
    public HandValue {
        Objects.requireNonNull(category, "category");
        tieBreakRanks = List.copyOf(tieBreakRanks);
    }

    @Override public int compareTo(HandValue other) {
        int categoryComparison = Integer.compare(category.ordinal(), other.category.ordinal());
        if (categoryComparison != 0) return categoryComparison;
        for (int i = 0; i < Math.min(tieBreakRanks.size(), other.tieBreakRanks.size()); i++) {
            int comparison = Integer.compare(tieBreakRanks.get(i), other.tieBreakRanks.get(i));
            if (comparison != 0) return comparison;
        }
        return Integer.compare(tieBreakRanks.size(), other.tieBreakRanks.size());
    }
}

