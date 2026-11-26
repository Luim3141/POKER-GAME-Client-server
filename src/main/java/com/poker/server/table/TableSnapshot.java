package com.poker.server.table;

import com.poker.game.Pot;
import com.poker.game.Street;
import com.poker.model.Card;
import com.poker.model.TableStatus;
import com.poker.model.Role;
import java.util.List;

public record TableSnapshot(String tableId, Role viewerRole, TableStatus status, long smallBlind, long bigBlind,
                            int maxPlayers, int dealerSeat, Street street, List<Card> board,
                            List<SeatSnapshot> seats, List<Pot> pots, Long currentTurnUserId,
                            int turnSeconds, long stateVersion, String handId, int smallBlindSeat, int bigBlindSeat,
                            com.poker.game.HandPhase phase, long dealDurationMillis, long dealRemainingMillis,
                            int dealCardCount, List<TableAction> recentActions, java.util.Map<Long, Long> winnerPayouts) {
    public TableSnapshot {
        board = List.copyOf(board); seats = List.copyOf(seats); pots = List.copyOf(pots);
        recentActions = recentActions == null ? List.of() : List.copyOf(recentActions);
        winnerPayouts = winnerPayouts == null ? java.util.Map.of() : java.util.Map.copyOf(winnerPayouts);
    }
}
