package com.poker.common;

public record AppConfig(String host, int port, String databaseUrl, String databaseUser,
                        String databasePassword, int turnSeconds) {
    public static AppConfig fromEnvironment() {
        return new AppConfig(
            env("POKER_HOST", "0.0.0.0"),
            integer("POKER_PORT", 19090),
            env("POKER_DB_URL", "jdbc:mariadb://127.0.0.1:3306/poker_game"),
            env("POKER_DB_USER", "root"),
            env("POKER_DB_PASSWORD", ""),
            integer("POKER_TURN_SECONDS", 20));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int integer(String name, int fallback) {
        try { return Integer.parseInt(env(name, Integer.toString(fallback))); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(name + " must be an integer", e); }
    }
}
