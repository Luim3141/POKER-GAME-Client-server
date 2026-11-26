package com.poker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.common.AppConfig;
import com.poker.common.PasswordHasher;
import com.poker.persistence.DatabaseManager;
import com.poker.persistence.JdbcAccountAdministration;
import java.io.Console;
import java.util.Locale;

public final class PokerAdminSetupMain {
    private PokerAdminSetupMain() {}
    public static void main(String[] args) throws Exception {
        Console console = System.console();
        if (console == null) throw new IllegalStateException("Run this tool from run-admin-setup.cmd");
        String username = console.readLine("Admin username: ").trim().toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9_]{3,24}")) throw new IllegalArgumentException("Username must contain 3-24 letters, digits or underscores");
        char[] password = console.readPassword("Admin password: ");
        char[] repeated = console.readPassword("Repeat password: ");
        try {
            if (!java.util.Arrays.equals(password, repeated)) throw new IllegalArgumentException("Passwords do not match");
            var hashed = new PasswordHasher().hash(password);
            var database = new DatabaseManager(AppConfig.fromEnvironment()); database.verify();
            new JdbcAccountAdministration(database, new ObjectMapper()).createAdmin(username, hashed.hash(), hashed.salt(), hashed.iterations());
            console.printf("Admin '%s' created.%n", username);
        } finally { java.util.Arrays.fill(password, '\0'); java.util.Arrays.fill(repeated, '\0'); }
    }
}
