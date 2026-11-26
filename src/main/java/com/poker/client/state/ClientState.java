package com.poker.client.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.poker.protocol.Message;
import com.poker.protocol.MessageType;
import com.poker.server.table.TableSnapshot;

public final class ClientState {
    private volatile String sessionId;
    private volatile long userId;
    private volatile String username;
    private volatile long chips;
    private volatile com.poker.model.GlorySummary glory;
    private volatile com.poker.model.AccountRole accountRole;
    private volatile com.poker.model.AccountStatus accountStatus;
    private volatile JsonNode lobby;
    private volatile TableSnapshot table;
    private volatile JsonNode lastError;

    public void apply(Message message, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        if (message.type() == MessageType.LOGIN_SUCCESS) {
            sessionId = message.payload().path("sessionId").asText(); userId = message.payload().path("userId").asLong();
            username = message.payload().path("username").asText(); chips = message.payload().path("chips").asLong();
            accountRole = com.poker.model.AccountRole.valueOf(message.payload().path("accountRole").asText("PLAYER"));
            accountStatus = com.poker.model.AccountStatus.valueOf(message.payload().path("accountStatus").asText("ACTIVE"));
            glory = message.payload().hasNonNull("glory") ? mapper.convertValue(message.payload().path("glory"), com.poker.model.GlorySummary.class) : null;
        } else if (message.type() == MessageType.WALLET_UPDATED) {
            chips = message.payload().path("chips").asLong(chips);
            if (message.payload().hasNonNull("glory")) glory = mapper.convertValue(message.payload().path("glory"), com.poker.model.GlorySummary.class);
        } else if (message.type() == MessageType.ACCOUNT_LOCKED) {
            accountStatus = com.poker.model.AccountStatus.LOCKED;
        } else if (message.type() == MessageType.LOBBY_SNAPSHOT) lobby = message.payload();
        else if (message.type() == MessageType.TABLE_STATE || message.type() == MessageType.STATE_SNAPSHOT) {
            table = mapper.convertValue(message.payload(), TableSnapshot.class);
            table.seats().stream().filter(seat -> seat.userId() == userId).findFirst().ifPresent(seat -> glory = seat.glory());
            if (table.status() != com.poker.model.TableStatus.IN_HAND) table.seats().stream()
                .filter(seat -> seat.userId() == userId).findFirst().ifPresent(seat -> chips = seat.stack());
        }
        else if (message.type() == MessageType.ERROR) lastError = message.payload();
    }

    public String sessionId() { return sessionId; }
    public long userId() { return userId; }
    public String username() { return username; }
    public long chips() { return chips; }
    public com.poker.model.GlorySummary glory() { return glory; }
    public com.poker.model.AccountRole accountRole() { return accountRole; }
    public com.poker.model.AccountStatus accountStatus() { return accountStatus; }
    public JsonNode lobby() { return lobby; }
    public TableSnapshot table() { return table; }
    public JsonNode lastError() { return lastError; }
    public boolean authenticated() { return sessionId != null && !sessionId.isBlank(); }
    public void clearTable() { table = null; }
    public void reset() {
        sessionId = null; userId = 0; username = null; chips = 0; accountRole = null; accountStatus = null;
        lobby = null; table = null; lastError = null; glory = null;
    }
}
