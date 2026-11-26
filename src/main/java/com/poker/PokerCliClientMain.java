package com.poker;

import com.poker.client.network.ClientEventListener;
import com.poker.client.network.PokerClient;
import com.poker.common.AppConfig;
import com.poker.protocol.Message;
import java.util.Scanner;

public final class PokerCliClientMain {
    private PokerCliClientMain() {}
    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.fromEnvironment();
        try (PokerClient client = new PokerClient(new ClientEventListener() {
            @Override public void onMessage(Message message) { System.out.println("\n< " + message.type() + " " + consolePayload(message)); System.out.print("> "); }
            @Override public void onDisconnected(Throwable cause) { System.out.println("\nDisconnected" + (cause == null ? "" : ": " + cause.getMessage())); }
        }); Scanner scanner = new Scanner(System.in)) {
            String host = "0.0.0.0".equals(config.host()) ? "127.0.0.1" : config.host();
            client.connect(host, config.port());
            System.out.println("Connected to " + host + ":" + config.port());
            System.out.println("Commands: register/login <user> <password>, lobby, history, create <sb> <seats>, join/spectate <id>, invite <user>, accept <tableId>, ready, fold/check/call/allin, bet/raise <amount>, leave, quit");
            while (client.isConnected()) {
                System.out.print("> "); if (!scanner.hasNextLine()) break;
                String[] command = scanner.nextLine().trim().split("\\s+");
                if (command.length == 0 || command[0].isBlank()) continue;
                try {
                    switch (command[0].toLowerCase()) {
                        case "register" -> client.register(command[1], command[2]);
                        case "login" -> client.login(command[1], command[2]);
                        case "lobby" -> client.requestLobby();
                        case "history" -> client.requestHistory();
                        case "create" -> client.createTable(Long.parseLong(command[1]), Integer.parseInt(command[2]));
                        case "join" -> client.joinTable(command[1]);
                        case "spectate" -> client.spectate(command[1]);
                        case "invite" -> client.invite(command[1]);
                        case "accept" -> client.answerInvitation(command[1], true);
                        case "ready" -> client.ready(true);
                        case "fold", "check", "call" -> client.action(command[0].toUpperCase(), 0);
                        case "allin" -> client.action("ALL_IN", 0);
                        case "bet", "raise" -> client.action(command[0].toUpperCase(), Long.parseLong(command[1]));
                        case "leave" -> client.leaveTable();
                        case "quit", "exit" -> { return; }
                        default -> System.out.println("Unknown command");
                    }
                } catch (RuntimeException | java.io.IOException e) { System.out.println("Command failed: " + e.getMessage()); }
            }
        }
    }

    static com.fasterxml.jackson.databind.JsonNode consolePayload(Message message) {
        com.fasterxml.jackson.databind.JsonNode visible = message.payload().deepCopy();
        if (visible instanceof com.fasterxml.jackson.databind.node.ObjectNode object)
            object.remove(java.util.List.of("sessionId", "password", "temporaryPassword"));
        return visible;
    }
}
