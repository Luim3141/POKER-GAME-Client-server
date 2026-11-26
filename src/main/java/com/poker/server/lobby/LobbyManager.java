package com.poker.server.lobby;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.common.AppConfig;
import com.poker.game.PokerAction;
import com.poker.model.Role;
import com.poker.persistence.UserAccount;
import com.poker.persistence.HandRepository;
import com.poker.server.network.ConnectionRegistry;
import com.poker.server.table.PokerTableRuntime;
import com.poker.server.table.TableSnapshot;
import com.poker.server.session.UserOperationLocks;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LobbyManager implements AutoCloseable {
    private final Map<String, PokerTableRuntime> tables = new ConcurrentHashMap<>();
    private final Map<Long, String> playerTable = new ConcurrentHashMap<>();
    private final Map<Long, String> observerTable = new ConcurrentHashMap<>();
    private final ConnectionRegistry connections;
    private final ObjectMapper mapper;
    private final AppConfig config;
    private final HandRepository hands;
    private final UserOperationLocks userLocks;
    // ponytail: one admission monitor; use per-table gates if many simultaneous lobby mutations become a bottleneck.
    private final Object registryLock = new Object();

    public LobbyManager(ConnectionRegistry connections, ObjectMapper mapper, AppConfig config, HandRepository hands, UserOperationLocks userLocks) {
        this.connections = connections; this.mapper = mapper; this.config = config; this.hands = hands; this.userLocks = userLocks;
    }

    public TableSnapshot create(UserAccount owner, long smallBlind, int maxPlayers) {
        return locked(owner.id(), () -> createLocked(owner, smallBlind, maxPlayers));
    }
    private TableSnapshot createLocked(UserAccount owner, long smallBlind, int maxPlayers) {
        synchronized (registryLock) {
            if (activePlayerTable(owner.id()) != null) throw new IllegalStateException("Already seated");
            if (smallBlind <= 0 || smallBlind > Long.MAX_VALUE / 2 || maxPlayers < 2 || maxPlayers > 8)
                throw new IllegalArgumentException("Invalid table settings");
            String id = UUID.randomUUID().toString();
            try { hands.createTable(id, owner.id(), smallBlind, maxPlayers); }
            catch (java.sql.SQLException e) { throw new IllegalStateException("Could not persist table", e); }
            PokerTableRuntime table = new PokerTableRuntime(id, owner.id(), smallBlind, maxPlayers, config.turnSeconds(), connections::send, mapper,
                settlement -> { try { hands.settle(settlement); } catch (java.sql.SQLException e) { throw new IllegalStateException(e); } });
            tables.put(id, table); playerTable.put(owner.id(), id);
            TableSnapshot snapshot = table.join(owner.id(), owner.username(), owner.chips(),
                com.poker.model.GlorySummary.from(owner.chips(), owner.wins(), owner.gamesPlayed()));
            stopObserving(owner.id());
            return snapshot;
        }
    }

    public TableSnapshot join(UserAccount user, String tableId) {
        return locked(user.id(), () -> joinLocked(user, tableId));
    }
    private TableSnapshot joinLocked(UserAccount user, String tableId) {
        synchronized (registryLock) {
            if (activePlayerTable(user.id()) != null || playerTable.putIfAbsent(user.id(), tableId) != null)
                throw new IllegalStateException("Already seated");
            try {
                TableSnapshot snapshot = table(tableId).join(user.id(), user.username(), user.chips(),
                    com.poker.model.GlorySummary.from(user.chips(), user.wins(), user.gamesPlayed()));
                stopObserving(user.id());
                return snapshot;
            }
            catch (RuntimeException e) { playerTable.remove(user.id(), tableId); throw e; }
        }
    }

    public void leave(long userId) {
        locked(userId, () -> {
            synchronized (registryLock) {
                String tableId = activePlayerTable(userId);
                if (tableId != null) {
                    PokerTableRuntime current = table(tableId);
                    current.leave(userId);
                    if (!current.hasSeat(userId)) playerTable.remove(userId, tableId);
                    retireIfUnused(tableId);
                }
            }
            return null;
        });
    }

    public void ready(long userId, boolean ready) { playerTable(userId).ready(userId, ready); }
    public void action(long userId, PokerAction action, long seq) {
        if (observerTable.containsKey(userId)) throw new IllegalStateException("Observer cannot act");
        playerTable(userId).action(userId, action, seq);
    }

    public TableSnapshot spectate(long userId, String tableId) {
        return locked(userId, () -> {
            synchronized (registryLock) {
                if (activePlayerTable(userId) != null) throw new IllegalStateException("A seated player cannot spectate");
                PokerTableRuntime table = table(tableId);
                String previous = observerTable.get(userId);
                table.spectate(userId);
                if (previous != null && !previous.equals(tableId)) stopObserving(userId);
                observerTable.put(userId, tableId);
                return table.snapshot(userId, Role.OBSERVER);
            }
        });
    }

    public void stopSpectating(long userId) { locked(userId, () -> { synchronized (registryLock) { stopObserving(userId); } return null; }); }
    public boolean isSeated(long userId) { return activePlayerTable(userId) != null; }
    public boolean isObserving(long userId) { return observerTable.containsKey(userId); }
    public void forceLeave(long userId) { leave(userId); stopSpectating(userId); }
    public void disconnected(long userId) {
        synchronized (registryLock) {
            String id = activePlayerTable(userId);
            if (id != null) {
                table(id).setConnected(userId, false);
                activePlayerTable(userId);
                retireIfUnused(id);
            }
            String observed = observerTable.get(userId);
            if (observed != null) retireIfUnused(observed);
        }
    }
    public void reconnected(long userId) { synchronized (registryLock) { String id = activePlayerTable(userId); if (id != null) table(id).setConnected(userId, true); } }
    public void retireEmptyTables() {
        synchronized (registryLock) {
            // Reconcile missed/failed disconnect callbacks, then retire rooms emptied by settlement.
            for (var entry : playerTable.entrySet()) if (!connections.isOnline(entry.getKey())) {
                PokerTableRuntime current = tables.get(entry.getValue());
                if (current != null) current.setConnected(entry.getKey(), false);
            }
            for (String id : List.copyOf(tables.keySet())) retireIfUnused(id);
        }
    }
    public TableSnapshot currentSnapshot(long userId) {
        synchronized (registryLock) {
            String player = activePlayerTable(userId); if (player != null) return table(player).snapshot(userId, Role.PLAYER);
            String observer = observerTable.get(userId); return observer == null ? null : table(observer).snapshot(userId, Role.OBSERVER);
        }
    }

    public List<TableSummary> summaries() {
        synchronized (registryLock) {
            return tables.values().stream().map(table -> {
                TableSnapshot snapshot = table.snapshot(0, Role.OBSERVER);
                return new TableSummary(snapshot.tableId(), snapshot.smallBlind(), snapshot.maxPlayers(), snapshot.seats().size(), snapshot.status().name());
            }).toList();
        }
    }

    private PokerTableRuntime playerTable(long userId) {
        String id = activePlayerTable(userId); if (id == null) throw new IllegalStateException("Not seated"); return table(id);
    }
    private String activePlayerTable(long userId) {
        synchronized (registryLock) {
            String id = playerTable.get(userId);
            if (id != null && !table(id).hasSeat(userId)) { playerTable.remove(userId, id); return null; }
            return id;
        }
    }
    private void stopObserving(long userId) {
        String id = observerTable.remove(userId);
        if (id != null) { table(id).stopSpectating(userId); retireIfUnused(id); }
    }
    private void retireIfUnused(String id) {
        PokerTableRuntime current = tables.get(id);
        if (current == null || !current.snapshot(0, Role.OBSERVER).seats().isEmpty()) return;
        for (var entry : observerTable.entrySet()) {
            if (id.equals(entry.getValue()) && !connections.isOnline(entry.getKey())) {
                current.stopSpectating(entry.getKey());
                observerTable.remove(entry.getKey(), id);
            }
        }
        if (current.isUnused()) {
            tables.remove(id, current);
            playerTable.entrySet().removeIf(entry -> id.equals(entry.getValue()));
            current.close();
        }
    }
    private PokerTableRuntime table(String id) {
        PokerTableRuntime table = tables.get(id); if (table == null) throw new IllegalArgumentException("Table not found"); return table;
    }
    private <T> T locked(long userId, java.util.function.Supplier<T> action) {
        try { return userLocks.call(userId, action::get); }
        catch (RuntimeException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    @Override public void close() { synchronized (registryLock) { tables.values().forEach(PokerTableRuntime::close); tables.clear(); playerTable.clear(); observerTable.clear(); } }
    public record TableSummary(String tableId, long smallBlind, int maxPlayers, int occupiedSeats, String status) {}
}
