package com.poker.server.network;

import com.poker.protocol.Message;
import com.poker.protocol.ProtocolCodec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientHandler implements Runnable, AutoCloseable {
    private final Socket socket;
    private final ServerController controller;
    private final ProtocolCodec codec;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final BlockingQueue<OutboundFrame> outbound = new ArrayBlockingQueue<>(128);
    private final Object outboundLock = new Object();
    private final Thread writer;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Long userId;
    private volatile String username;
    private volatile long lastPong = System.currentTimeMillis();

    public ClientHandler(Socket socket, ServerController controller, ProtocolCodec codec) throws IOException {
        this.socket = socket; this.controller = controller; this.codec = codec;
        socket.setTcpNoDelay(true); socket.setKeepAlive(true);
        input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        writer = Thread.ofVirtual().name("poker-client-writer").start(this::writeLoop);
    }

    @Override public void run() {
        try { while (!closed.get()) controller.handle(this, codec.read(input)); }
        catch (EOFException | SocketException ignored) { }
        catch (IOException ignored) { }
        finally { close(); controller.disconnected(this); }
    }

    public void send(Message message) throws IOException {
        enqueue(new OutboundFrame(message, null));
    }

    /** Control-path only: allow a final lock/error notification to flush before closing. */
    public void sendAndClose(Message message) throws IOException {
        CompletableFuture<Void> written = new CompletableFuture<>();
        try {
            enqueue(new OutboundFrame(message, written));
            written.get(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IOException("Final message interrupted", error);
        } catch (ExecutionException | TimeoutException error) {
            throw new IOException("Final message could not be delivered", error);
        } finally { close(); }
    }

    private void enqueue(OutboundFrame frame) throws IOException {
        synchronized (outboundLock) {
            if (closed.get()) throw new SocketException("Connection closed");
            if (!outbound.offer(frame)) {
                close(); throw new SocketException("Client is not reading server messages");
            }
        }
    }

    private void writeLoop() {
        try {
            while (!closed.get()) {
                OutboundFrame frame = outbound.take();
                try {
                    codec.write(output, frame.message());
                    if (frame.written() != null) frame.written().complete(null);
                } catch (IOException error) {
                    if (frame.written() != null) frame.written().completeExceptionally(error);
                    throw error;
                }
            }
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        catch (IOException ignored) { }
        finally { close(); }
    }
    public void authenticate(long id, String name) { userId = id; username = name; }
    public long requireUserId() { if (userId == null) throw new IllegalStateException("Authentication required"); return userId; }
    public Long userId() { return userId; }
    public String username() { return username; }
    public void markPong() { lastPong = System.currentTimeMillis(); }
    public long lastPong() { return lastPong; }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { socket.close(); } catch (IOException ignored) { }
        if (writer != null) writer.interrupt();
        synchronized (outboundLock) {
            OutboundFrame frame;
            while ((frame = outbound.poll()) != null)
                if (frame.written() != null) frame.written().completeExceptionally(new SocketException("Connection closed"));
        }
    }

    private record OutboundFrame(Message message, CompletableFuture<Void> written) { }
}

