package com.poker.client.network;

import com.poker.protocol.Message;

public interface ClientEventListener {
    void onMessage(Message message);
    default void onDisconnected(Throwable cause) {}
}

