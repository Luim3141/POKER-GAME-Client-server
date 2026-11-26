package com.poker.server.network;

import com.poker.common.AppConfig;
import com.poker.persistence.DatabaseManager;
import com.poker.protocol.Message;
import com.poker.protocol.MessageType;
import com.poker.protocol.ProtocolCodec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientHandlerTransportTest {
    @Test void concurrentSendersKeepCompleteFramesAndEachSendersOrder() throws Exception {
        ProtocolCodec codec = new ProtocolCodec();
        try (ServerSocket listener = new ServerSocket(0);
             Socket peer = new Socket("127.0.0.1", listener.getLocalPort());
             Socket accepted = listener.accept(); ClientHandler handler = new ClientHandler(accepted, null, codec);
             var senders = Executors.newFixedThreadPool(2)) {
            peer.setSoTimeout(3_000);
            List<java.util.concurrent.Future<?>> tasks = new ArrayList<>();
            for (int sender = 0; sender < 2; sender++) {
                int source = sender;
                tasks.add(senders.submit(() -> {
                    try {
                        for (int index = 0; index < 40; index++) handler.send(Message.of(MessageType.TABLE_STATE,
                            codec.mapper().createObjectNode().put("source", source).put("index", index)));
                    } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
                }));
            }
            int[] expectedNext = {0, 0};
            var input = new DataInputStream(peer.getInputStream());
            for (int index = 0; index < 80; index++) {
                Message frame = codec.read(input);
                int source = frame.payload().path("source").asInt();
                assertEquals(expectedNext[source]++, frame.payload().path("index").asInt());
            }
            assertArrayEquals(new int[] {40, 40}, expectedNext);
            for (var task : tasks) task.get(3, TimeUnit.SECONDS);
        }
    }

    @Test void stalledReaderCannotBlockTableSenderAndOverflowClosesSocket() throws Exception {
        ProtocolCodec codec = new ProtocolCodec();
        try (ServerSocket listener = new ServerSocket(0); Socket peer = new Socket(); var sender = Executors.newSingleThreadExecutor()) {
            peer.setReceiveBufferSize(1_024); peer.connect(new java.net.InetSocketAddress("127.0.0.1", listener.getLocalPort()));
            try (Socket accepted = listener.accept(); ClientHandler handler = new ClientHandler(accepted, null, codec)) {
                accepted.setSendBufferSize(1_024);
                Message largeFrame = Message.of(MessageType.TABLE_STATE, codec.mapper().createObjectNode().put("data", "x".repeat(256 * 1024)));
                var task = sender.submit(() -> {
                    for (int index = 0; index < 500; index++) {
                        try { handler.send(largeFrame); }
                        catch (IOException expected) { return true; }
                    }
                    return false;
                });
                assertTrue(task.get(3, TimeUnit.SECONDS), "Backpressure must close an overflowing connection promptly");
                assertTrue(accepted.isClosed());
            }
        }
    }

    @Test void finalAccountLockedMessageFlushesAfterEarlierFramesBeforeClose() throws Exception {
        ProtocolCodec codec = new ProtocolCodec();
        try (ServerSocket listener = new ServerSocket(0);
             Socket peer = new Socket("127.0.0.1", listener.getLocalPort());
             Socket accepted = listener.accept(); ClientHandler handler = new ClientHandler(accepted, null, codec)) {
            peer.setSoTimeout(3_000);
            handler.send(Message.of(MessageType.TABLE_STATE, codec.mapper().createObjectNode()));
            handler.sendAndClose(Message.of(MessageType.ACCOUNT_LOCKED, codec.mapper().createObjectNode().put("message", "locked")));
            var input = new DataInputStream(peer.getInputStream());
            assertEquals(MessageType.TABLE_STATE, codec.read(input).type());
            assertEquals(MessageType.ACCOUNT_LOCKED, codec.read(input).type());
            assertThrows(EOFException.class, () -> codec.read(input));
        }
    }

    @Test void serverRejectsConnectionBeyondReaderLimitInsteadOfQueueingIt() throws Exception {
        ProtocolCodec codec = new ProtocolCodec();
        AppConfig config = new AppConfig("127.0.0.1", 0, "jdbc:mariadb://127.0.0.1:1/unused", "unused", "", 20);
        try (PokerServer server = new PokerServer(config, new DatabaseManager(config))) {
            var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            Thread serverThread = Thread.ofPlatform().start(() -> {
                try { server.start(); } catch (Throwable error) { failure.set(error); }
            });
            List<Socket> peers = new ArrayList<>();
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (server.localPort() < 0 && System.nanoTime() < deadline) Thread.sleep(10);
                assertTrue(server.localPort() > 0);
                for (int index = 0; index < 64; index++) {
                    Socket peer = new Socket("127.0.0.1", server.localPort()); peers.add(peer); peer.setSoTimeout(3_000);
                    codec.write(new DataOutputStream(peer.getOutputStream()), Message.of(MessageType.PING, codec.mapper().createObjectNode()));
                    assertEquals(MessageType.PONG, codec.read(new DataInputStream(peer.getInputStream())).type());
                }
                try (Socket rejected = new Socket("127.0.0.1", server.localPort())) {
                    rejected.setSoTimeout(3_000);
                    try { assertEquals(-1, rejected.getInputStream().read()); }
                    catch (java.net.SocketException closed) { /* Windows may send reset instead of EOF. */ }
                }
            } finally {
                for (Socket peer : peers) peer.close();
                server.close(); serverThread.join(3_000);
            }
            assertNull(failure.get());
        }
    }
}
