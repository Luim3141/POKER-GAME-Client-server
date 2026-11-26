package com.poker.server.table;

final class TableSeat {
    final long userId;
    final String username;
    final int seat;
    long stack;
    com.poker.model.GlorySummary glory;
    boolean ready;
    boolean connected = true;
    boolean leaveAfterHand;

    TableSeat(long userId, String username, int seat, long stack) {
        this.userId = userId; this.username = username; this.seat = seat; this.stack = stack;
    }
}
