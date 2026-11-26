package com.poker;

import com.poker.protocol.Message;
import com.poker.protocol.MessageType;
import com.poker.protocol.ProtocolCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PokerCliClientMainTest {
    @Test void consoleOmitsCredentialsWithoutChangingTheReceivedPayload() {
        var payload = new ProtocolCodec().mapper().createObjectNode().put("username", "player")
            .put("chips", 10000).put("sessionId", "fixture-token").put("password", "fixture-password")
            .put("temporaryPassword", "fixture-temporary-password");
        var visible = PokerCliClientMain.consolePayload(Message.of(MessageType.LOGIN_SUCCESS, payload));
        assertFalse(visible.has("sessionId")); assertFalse(visible.has("password"));
        assertFalse(visible.has("temporaryPassword"));
        assertEquals("player", visible.path("username").asText()); assertEquals(10000, visible.path("chips").asLong());
        assertEquals("fixture-token", payload.path("sessionId").asText());
    }
}
