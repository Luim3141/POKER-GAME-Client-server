package com.poker.client.network;

import com.poker.protocol.Message;
import com.poker.protocol.ProtocolCodec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class ServerConnection implements AutoCloseable {
    private final ProtocolCodec codec;
    private final Socket socket = new Socket();
    private DataInputStream input;
    private DataOutputStream output;

    public ServerConnection(ProtocolCodec codec) { this.codec = codec; }
    public void connect(String host, int port) throws IOException {
        socket.connect(new InetSocketAddress(host, port), 5_000); socket.setTcpNoDelay(true); socket.setKeepAlive(true);
        input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }
    public Message receive() throws IOException { return codec.read(input); }
    public synchronized void send(Message message) throws IOException { codec.write(output, message); }
    public boolean isConnected() { return socket.isConnected() && !socket.isClosed(); }
    @Override public void close() throws IOException { socket.close(); }
}

