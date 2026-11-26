package com.poker.persistence;

import java.sql.SQLException;

public interface HandRepository {
    void createTable(String id, long ownerUserId, long smallBlind, int maxPlayers) throws SQLException;
    boolean settle(HandSettlement settlement) throws SQLException;
}

