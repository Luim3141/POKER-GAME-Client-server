package com.poker.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.UUID;

public record Message(int version, MessageType type, String requestId, String sessionId,
                      String tableId, String handId, long seq, long timestamp, JsonNode payload) {
    public Message {
        if (version != 1) throw new IllegalArgumentException("Unsupported protocol version");
        if (type == null || requestId == null || requestId.isBlank()) throw new IllegalArgumentException("Missing message metadata");
        payload = payload == null ? JsonNodeFactory.instance.objectNode() : payload;
    }

    public static Message of(MessageType type, JsonNode payload) {
        return new Message(1, type, UUID.randomUUID().toString(), null, null, null, 0,
            Instant.now().toEpochMilli(), payload);
    }

    public Message withSession(String sessionId) {
        return new Message(version, type, requestId, sessionId, tableId, handId, seq, timestamp, payload);
    }

    public static Message event(MessageType type, String tableId, String handId, long seq, JsonNode payload) {
        return new Message(1, type, UUID.randomUUID().toString(), null, tableId, handId, seq,
            Instant.now().toEpochMilli(), payload);
    }

    public static Message reply(Message request, MessageType type, JsonNode payload) {
        return new Message(1, type, request.requestId(), null, request.tableId(), request.handId(), request.seq(),
            Instant.now().toEpochMilli(), payload);
    }

    public static Message request(MessageType type, String sessionId, String tableId, long seq, JsonNode payload) {
        return new Message(1, type, UUID.randomUUID().toString(), sessionId, tableId, null, seq,
            Instant.now().toEpochMilli(), payload);
    }
}
