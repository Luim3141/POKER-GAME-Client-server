package com.poker.protocol;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolCodecTest {
    private final ProtocolCodec codec = new ProtocolCodec();

    @Test void rejectsJsonNullInsteadOfPassingANullMessageToTheDispatcher() throws Exception {
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        out.writeInt(4); out.write("null".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(ProtocolException.class, () -> codec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test void readsPartialInputAndMultipleFrames() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        codec.write(out, Message.of(MessageType.PING, JsonNodeFactory.instance.objectNode().put("n", 1)));
        codec.write(out, Message.of(MessageType.PONG, JsonNodeFactory.instance.objectNode().put("n", 2)));

        var partial = new FilterInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override public int read(byte[] b, int off, int len) throws IOException { return super.read(b, off, Math.min(len, 2)); }
        };
        var input = new DataInputStream(partial);
        assertEquals(MessageType.PING, codec.read(input).type());
        assertEquals(MessageType.PONG, codec.read(input).type());
    }

    @Test void rejectsInvalidFrameLengthAndJson() throws Exception {
        var tooLarge = new ByteArrayOutputStream();
        new DataOutputStream(tooLarge).writeInt(ProtocolCodec.MAX_PAYLOAD_BYTES + 1);
        assertThrows(ProtocolException.class, () -> codec.read(new DataInputStream(new ByteArrayInputStream(tooLarge.toByteArray()))));

        var invalidJson = new ByteArrayOutputStream();
        var out = new DataOutputStream(invalidJson);
        out.writeInt(1); out.writeByte('{');
        assertThrows(ProtocolException.class, () -> codec.read(new DataInputStream(new ByteArrayInputStream(invalidJson.toByteArray()))));
    }

    @Test void rejectsEmptyNegativeAndTruncatedFramesWithoutConsumingANextFrame() throws Exception {
        for (int length : new int[]{0, -1}) {
            var bytes = new ByteArrayOutputStream(); new DataOutputStream(bytes).writeInt(length);
            assertThrows(ProtocolException.class, () -> codec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        }
        assertThrows(java.io.EOFException.class, () -> codec.read(new DataInputStream(new ByteArrayInputStream(new byte[]{0, 0}))));
        var truncated = new ByteArrayOutputStream(); var out = new DataOutputStream(truncated);
        out.writeInt(5); out.write(new byte[]{'{', '}'});
        assertThrows(java.io.EOFException.class, () -> codec.read(new DataInputStream(new ByteArrayInputStream(truncated.toByteArray()))));
    }
}
