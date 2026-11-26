package com.poker.server.session;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class UserOperationLocks {
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T call(long userId, CheckedSupplier<T> action) throws Exception {
        ReentrantLock lock = locks.computeIfAbsent(userId, ignored -> new ReentrantLock());
        lock.lock();
        try { return action.get(); }
        finally { lock.unlock(); }
    }

    @FunctionalInterface public interface CheckedSupplier<T> { T get() throws Exception; }
}
