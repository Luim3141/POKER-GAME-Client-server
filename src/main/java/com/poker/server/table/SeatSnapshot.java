package com.poker.server.table;

import com.poker.model.Card;
import com.poker.model.PlayerStatus;
import java.util.List;

public record SeatSnapshot(long userId, String username, int seat, long stack, long streetContribution,
                           long totalContribution, boolean ready, boolean connected,
                           PlayerStatus handStatus, List<Card> holeCards, com.poker.model.GlorySummary glory) {
    public SeatSnapshot { holeCards = List.copyOf(holeCards); }
}
