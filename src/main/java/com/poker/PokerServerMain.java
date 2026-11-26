package com.poker;

import com.poker.common.AppConfig;
import com.poker.persistence.DatabaseManager;
import com.poker.server.network.PokerServer;

public final class PokerServerMain {
    private PokerServerMain() {}
    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.fromEnvironment();
        DatabaseManager database = new DatabaseManager(config);
        database.verify();
        try (PokerServer server = new PokerServer(config, database)) { server.start(); }
    }
}
