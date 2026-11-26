package com.poker.persistence;

import java.util.List;

public record HandSettlement(String handId, String tableId, int dealerSeat, String boardJson,
                             List<PlayerResult> players, List<ActionRecord> actions) {
    public HandSettlement { players = List.copyOf(players); actions = List.copyOf(actions); }
    public record PlayerResult(long userId, int seat, long startingStack, long finalStack,
                               long contribution, boolean winner) {}
    public record ActionRecord(long userId, int sequence, String street, String action, long amount) {}
}

