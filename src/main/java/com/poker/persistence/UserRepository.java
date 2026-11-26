package com.poker.persistence;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    UserAccount create(String username, byte[] hash, byte[] salt, int iterations, long chips) throws SQLException;
    Optional<UserAccount> findByUsername(String username) throws SQLException;
    Optional<UserAccount> findById(long id) throws SQLException;
    List<RankingEntry> ranking(int limit) throws SQLException;
    List<HistoryEntry> history(long userId, int limit) throws SQLException;
    record RankingEntry(String username, long chips, long wins, long gamesPlayed, double winRate, com.poker.model.GlorySummary glory) {
        public RankingEntry(String username, long chips, long wins, long gamesPlayed, double winRate) {
            this(username, chips, wins, gamesPlayed, winRate, com.poker.model.GlorySummary.from(chips, wins, gamesPlayed));
        }
    }
    record HistoryEntry(String handId, String tableId, String result, long contribution,
                        long startingStack, long finalStack, java.time.Instant startedAt, java.time.Instant endedAt) {}
}
