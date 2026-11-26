package com.poker.game;

import com.poker.model.Card;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HandEvaluator {
    public HandValue evaluate(List<Card> cards) {
        if (cards.size() < 5 || cards.size() > 7) throw new IllegalArgumentException("Expected 5 to 7 cards");
        HandValue best = null;
        int n = cards.size();
        for (int a = 0; a < n - 4; a++) for (int b = a + 1; b < n - 3; b++)
            for (int c = b + 1; c < n - 2; c++) for (int d = c + 1; d < n - 1; d++)
                for (int e = d + 1; e < n; e++) {
                    HandValue value = evaluateFive(List.of(cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e)));
                    if (best == null || value.compareTo(best) > 0) best = value;
                }
        return best;
    }

    HandValue evaluateFive(List<Card> cards) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Card card : cards) counts.merge(card.rank().value(), 1, Integer::sum);
        List<Integer> ranks = counts.keySet().stream().sorted(Comparator.reverseOrder()).toList();
        boolean flush = cards.stream().map(Card::suit).distinct().count() == 1;
        int straightHigh = straightHigh(ranks);
        if (flush && straightHigh == 14) return new HandValue(HandCategory.ROYAL_FLUSH, List.of(14));
        if (flush && straightHigh > 0) return new HandValue(HandCategory.STRAIGHT_FLUSH, List.of(straightHigh));

        List<Integer> groups = counts.entrySet().stream()
            .sorted(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry.<Integer, Integer>comparingByKey().reversed()))
            .map(Map.Entry::getKey).toList();
        if (counts.get(groups.get(0)) == 4) return new HandValue(HandCategory.FOUR_OF_A_KIND, groups);
        if (counts.get(groups.get(0)) == 3 && counts.get(groups.get(1)) == 2)
            return new HandValue(HandCategory.FULL_HOUSE, groups);
        if (flush) return new HandValue(HandCategory.FLUSH, ranks);
        if (straightHigh > 0) return new HandValue(HandCategory.STRAIGHT, List.of(straightHigh));
        if (counts.get(groups.get(0)) == 3) return new HandValue(HandCategory.THREE_OF_A_KIND, groups);
        if (counts.get(groups.get(0)) == 2 && counts.get(groups.get(1)) == 2)
            return new HandValue(HandCategory.TWO_PAIR, groups);
        if (counts.get(groups.get(0)) == 2) return new HandValue(HandCategory.ONE_PAIR, groups);
        return new HandValue(HandCategory.HIGH_CARD, ranks);
    }

    private int straightHigh(List<Integer> ranks) {
        List<Integer> sorted = new ArrayList<>(ranks);
        sorted.sort(Comparator.reverseOrder());
        if (sorted.contains(14)) sorted.add(1);
        int run = 1;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1) - sorted.get(i) == 1) {
                if (++run == 5) return sorted.get(i - 4);
            } else if (!sorted.get(i - 1).equals(sorted.get(i))) run = 1;
        }
        return 0;
    }
}

