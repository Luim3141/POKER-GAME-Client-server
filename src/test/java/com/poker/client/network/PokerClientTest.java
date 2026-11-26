package com.poker.client.network;

import com.poker.protocol.*;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PokerClientTest {
    @Test void oldReceiverCannotCloseAReplacementConnectionOnTheSameClient() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var oldReceiver = new AtomicReference<Thread>();
        var codec = new ProtocolCodec();
        try (ServerSocket server = new ServerSocket(0); PokerClient client = new PokerClient(message -> {
            if (message.payload().path("first").asBoolean()) {
                oldReceiver.set(Thread.currentThread()); entered.countDown();
                try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
        })) {
            client.connect("127.0.0.1", server.getLocalPort());
            try (var first = server.accept()) {
                codec.write(new DataOutputStream(first.getOutputStream()),
                    Message.of(MessageType.PONG, codec.mapper().createObjectNode().put("first", true)));
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                client.close();
                client.connect("127.0.0.1", server.getLocalPort());
                try (var second = server.accept()) {
                    release.countDown(); oldReceiver.get().join(2000);
                    assertFalse(oldReceiver.get().isAlive());
                    assertTrue(client.isConnected(), "Old receiver cleanup must not close the new socket");
                    second.setSoTimeout(2000);
                    codec.write(new DataOutputStream(second.getOutputStream()), Message.of(MessageType.PING, codec.mapper().createObjectNode()));
                    assertEquals(MessageType.PONG, codec.read(new java.io.DataInputStream(second.getInputStream())).type());
                }
            } finally { release.countDown(); }
        }
    }
}
