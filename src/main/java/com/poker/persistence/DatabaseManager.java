package com.poker.persistence;

import com.poker.common.AppConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseManager {
    private final AppConfig config;
    public DatabaseManager(AppConfig config) { this.config = config; }
    public Connection open() throws SQLException {
        return DriverManager.getConnection(config.databaseUrl(), config.databaseUser(), config.databasePassword());
    }
    public void verify() throws SQLException { try (Connection ignored = open()) { } }
}

