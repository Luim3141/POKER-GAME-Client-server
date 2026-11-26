package com.poker.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;

public final class JdbcHandRepository implements HandRepository {
    private final DatabaseManager database;
    public JdbcHandRepository(DatabaseManager database) { this.database = database; }

    @Override public void createTable(String id, long ownerUserId, long smallBlind, int maxPlayers) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO poker_tables(id,owner_user_id,small_blind,max_players,status) VALUES(?,?,?,?,?)")) {
            statement.setString(1, id); statement.setLong(2, ownerUserId); statement.setLong(3, smallBlind);
            statement.setInt(4, maxPlayers); statement.setString(5, "WAITING"); statement.executeUpdate();
        }
    }

    @Override public boolean settle(HandSettlement hand) throws SQLException {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                if (alreadySettled(connection, hand.handId())) { connection.rollback(); return false; }
                insertHand(connection, hand);
                for (var player : hand.players().stream().sorted(Comparator.comparingLong(HandSettlement.PlayerResult::userId)).toList()) {
                    lockUser(connection, player.userId()); insertPlayer(connection, hand.handId(), player);
                    updateWallet(connection, player); insertWalletTransaction(connection, hand.handId(), player);
                }
                for (var action : hand.actions()) insertAction(connection, hand.handId(), action);
                try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE hands SET status='SETTLED',settled_at=CURRENT_TIMESTAMP,ended_at=CURRENT_TIMESTAMP WHERE id=?")) {
                    statement.setString(1, hand.handId()); statement.executeUpdate();
                }
                connection.commit(); return true;
            } catch (SQLException | RuntimeException e) { connection.rollback(); throw e; }
            finally { connection.setAutoCommit(true); }
        }
    }

    private boolean alreadySettled(Connection connection, String handId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT settled_at FROM hands WHERE id=? FOR UPDATE")) {
            statement.setString(1, handId);
            try (ResultSet result = statement.executeQuery()) { return result.next() && result.getTimestamp(1) != null; }
        }
    }

    private void insertHand(Connection connection, HandSettlement hand) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT IGNORE INTO hands(id,table_id,dealer_seat,board_json,status) VALUES(?,?,?,?,?)")) {
            statement.setString(1, hand.handId()); statement.setString(2, hand.tableId()); statement.setInt(3, hand.dealerSeat());
            statement.setString(4, hand.boardJson()); statement.setString(5, "SETTLING"); statement.executeUpdate();
        }
    }

    private void lockUser(Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM users WHERE id=? FOR UPDATE")) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) { if (!result.next()) throw new SQLException("User no longer exists: " + userId); }
        }
    }

    private void insertPlayer(Connection connection, String handId, HandSettlement.PlayerResult player) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO hand_players(hand_id,user_id,seat_no,starting_stack,final_stack,contribution,result) VALUES(?,?,?,?,?,?,?)")) {
            statement.setString(1, handId); statement.setLong(2, player.userId()); statement.setInt(3, player.seat());
            statement.setLong(4, player.startingStack()); statement.setLong(5, player.finalStack());
            statement.setLong(6, player.contribution()); statement.setString(7, player.winner() ? "WIN" : "LOSS"); statement.executeUpdate();
        }
    }

    private void updateWallet(Connection connection, HandSettlement.PlayerResult player) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE users SET chips=?,wins=wins+?,games_played=games_played+1 WHERE id=?")) {
            statement.setLong(1, player.finalStack()); statement.setInt(2, player.winner() ? 1 : 0); statement.setLong(3, player.userId());
            if (statement.executeUpdate() != 1) throw new SQLException("Wallet update failed for user " + player.userId());
        }
    }

    private void insertWalletTransaction(Connection connection, String handId, HandSettlement.PlayerResult player) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO wallet_transactions(hand_id,user_id,transaction_type,amount,balance_after) VALUES(?,?,?,?,?)")) {
            statement.setString(1, handId); statement.setLong(2, player.userId()); statement.setString(3, "HAND_SETTLEMENT");
            statement.setLong(4, player.finalStack() - player.startingStack()); statement.setLong(5, player.finalStack()); statement.executeUpdate();
        }
    }

    private void insertAction(Connection connection, String handId, HandSettlement.ActionRecord action) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO hand_actions(hand_id,user_id,action_seq,street,action_type,amount) VALUES(?,?,?,?,?,?)")) {
            statement.setString(1, handId); statement.setLong(2, action.userId()); statement.setInt(3, action.sequence());
            statement.setString(4, action.street()); statement.setString(5, action.action()); statement.setLong(6, action.amount()); statement.executeUpdate();
        }
    }
}
