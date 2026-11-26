package com.poker.server.network;

import com.poker.protocol.Message;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectionRegistry {
    private final Map<Long, ClientHandler> byUser = new ConcurrentHashMap<>();
    private final Map<Long, String> usernames = new ConcurrentHashMap<>();

    public void attach(long userId, String username, ClientHandler handler) {
        ClientHandler previous = byUser.put(userId, handler);
        usernames.put(userId, username);
        if (previous != null && previous != handler) previous.close();
    }

    public boolean detach(long userId, ClientHandler handler) { return byUser.remove(userId, handler); }
    public boolean isCurrent(long userId, ClientHandler handler) { return byUser.get(userId) == handler; }

    public void send(long userId, Message message) {
        ClientHandler handler = byUser.get(userId);
        if (handler != null) try { handler.send(message); } catch (IOException e) { handler.close(); }
    }

    public void sendAndClose(long userId, Message message) {
        ClientHandler handler = byUser.get(userId);
        if (handler != null) try { handler.sendAndClose(message); } catch (IOException e) { handler.close(); }
    }

    public List<OnlineUser> onlineUsers() {
        return byUser.keySet().stream().sorted().map(id -> new OnlineUser(id, usernames.get(id))).toList();
    }

    public boolean isOnline(long userId) { return byUser.containsKey(userId); }
    public java.util.Set<Long> onlineUserIds() { return java.util.Set.copyOf(byUser.keySet()); }
    public void disconnect(long userId) { ClientHandler handler = byUser.get(userId); if (handler != null) handler.close(); }
    public record OnlineUser(long userId, String username) {}
}
