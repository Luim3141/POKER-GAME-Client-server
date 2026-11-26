package com.poker.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class ProtocolCodec {
    public static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    private final ObjectMapper mapper;

    public ProtocolCodec() { this(new ObjectMapper()); }
    public ProtocolCodec(ObjectMapper mapper) { this.mapper = mapper; }

    public Message read(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > MAX_PAYLOAD_BYTES) throw new ProtocolException("Invalid frame length: " + length);
        byte[] payload = new byte[length];
        input.readFully(payload);
        try {
            Message message = mapper.readValue(payload, Message.class);
            if (message == null) throw new ProtocolException("Message cannot be JSON null");
            return message;
        }
        catch (RuntimeException | com.fasterxml.jackson.core.JacksonException e) { throw new ProtocolException("Invalid JSON message", e); }
    }

    public void write(DataOutputStream output, Message message) throws IOException {
        byte[] payload = mapper.writeValueAsBytes(message);
        if (payload.length > MAX_PAYLOAD_BYTES) throw new ProtocolException("Message exceeds maximum frame size");
        output.writeInt(payload.length);
        output.write(payload);
        output.flush();
    }

    public ObjectMapper mapper() { return mapper; }
}
