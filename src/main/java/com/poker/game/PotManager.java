package com.poker.game;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PotManager {
    public List<Pot> buildPots(List<PlayerHandState> players) {
        List<Long> levels = players.stream().map(PlayerHandState::totalContribution).filter(v -> v > 0).distinct().sorted().toList();
        List<Pot> pots = new ArrayList<>();
        long previous = 0;
        for (long level : levels) {
            List<PlayerHandState> contributors = players.stream().filter(p -> p.totalContribution() >= level).toList();
            long amount = (level - previous) * contributors.size();
            Set<String> eligible = new LinkedHashSet<>();
            contributors.stream().filter(PlayerHandState::eligibleForPot).forEach(p -> eligible.add(p.userId()));
            if (amount > 0) {
                // House rule: leaving folds a hand; a side pot abandoned by every contributor
                // becomes dead money in the nearest lower live pot instead of stranding chips.
                if (eligible.isEmpty() && !pots.isEmpty()) {
                    Pot lower = pots.removeLast();
                    pots.add(new Pot(Math.addExact(lower.amount(), amount), lower.eligibleUserIds()));
                } else pots.add(new Pot(amount, eligible));
            }
            previous = level;
        }
        return pots;
    }
}

