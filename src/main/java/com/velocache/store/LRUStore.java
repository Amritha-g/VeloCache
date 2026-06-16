package com.velocache.store;

import com.velocache.expiry.ExpiryManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LRUStore implements a thread-safe, size-bounded LRU cache store extending {@link LinkedHashMap}.
 *
 * <p><strong>Concurrency Note:</strong>
 * This store is configured with {@code accessOrder = true}. In {@link LinkedHashMap}, calling
 * {@code get()} moves the accessed node to the tail of the internal doubly-linked list. Consequently,
 * read-like get operations modify the internal pointer structure of the collection. Because of this,
 * we must acquire a exclusive write-lock ({@link ReentrantReadWriteLock#writeLock()}) for both
 * read-like operations ({@code get()}) and write-like operations ({@code put()}) to avoid race conditions
 * or memory corruption on the internal pointers when multiple threads access the store concurrently.
 *
 * <p><strong>Future Work:</strong>
 * To support true concurrent reads without blocking, we could implement a design using
 * a {@link java.util.concurrent.ConcurrentHashMap} coupled with a custom, thread-safe intrusive
 * doubly-linked-list to track LRU ordering. This would allow concurrent gets to execute lock-free
 * (or with highly localized locks) at the cost of higher implementation complexity.
 */
public class LRUStore extends LinkedHashMap<String, Object> implements CacheStore {
    private final int maxSize;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private ExpiryManager expiryManager;

    public LRUStore(int maxSize) {
        super(maxSize, 0.75f, true); // accessOrder=true is critical for LRU
        this.maxSize = maxSize;
    }

    public void setExpiryManager(ExpiryManager expiryManager) {
        this.lock.writeLock().lock();
        try {
            this.expiryManager = expiryManager;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
        boolean shouldEvict = size() > maxSize;
        if (shouldEvict && expiryManager != null) {
            expiryManager.removeExpiry(eldest.getKey());
        }
        return shouldEvict;
    }

    private boolean checkExpiry(String key) {
        if (expiryManager != null && expiryManager.isExpired(key)) {
            // Remove from LinkedHashMap
            super.remove(key);
            // Remove from ExpiryManager
            expiryManager.removeExpiry(key);
            return true;
        }
        return false;
    }

    @Override
    public Object get(Object key) {
        // Safe check for key type
        if (!(key instanceof String)) {
            return null;
        }
        return get((String) key);
    }

    @Override
    public Object get(String key) {
        lock.writeLock().lock(); // MUST be a write lock because LinkedHashMap.get() modifies access-order list pointers!
        try {
            if (checkExpiry(key)) {
                return null;
            }
            return super.get(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Object put(String key, Object value) {
        lock.writeLock().lock();
        try {
            return super.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean remove(String key) {
        lock.writeLock().lock();
        try {
            if (expiryManager != null) {
                expiryManager.removeExpiry(key);
            }
            return super.remove(key) != null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean exists(String key) {
        lock.writeLock().lock(); // Uses write lock because checkExpiry may delete the key!
        try {
            if (checkExpiry(key)) {
                return false;
            }
            return super.containsKey(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public long incr(String key) {
        return incrBy(key, 1);
    }

    @Override
    public long incrBy(String key, long amount) {
        lock.writeLock().lock();
        try {
            if (checkExpiry(key)) {
                // Key expired, behaves like it doesn't exist
            }
            Object current = super.get(key);
            long val = 0;
            if (current != null) {
                try {
                    val = Long.parseLong(current.toString());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("ERR value is not an integer or out of range");
                }
            }
            long newVal = val + amount;
            super.put(key, String.valueOf(newVal));
            return newVal;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void append(String key, String value) {
        lock.writeLock().lock();
        try {
            checkExpiry(key);
            Object current = super.get(key);
            String currentStr = current == null ? "" : current.toString();
            super.put(key, currentStr + value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int strlen(String key) {
        lock.writeLock().lock();
        try {
            if (checkExpiry(key)) {
                return 0;
            }
            Object current = super.get(key);
            return current == null ? 0 : current.toString().length();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            super.clear();
            if (expiryManager != null) {
                expiryManager.clear();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.writeLock().lock(); // Read/write consistency
        try {
            return super.size();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<String> keys() {
        lock.writeLock().lock();
        try {
            // Clean up any expired keys before returning key list
            List<String> allKeys = new ArrayList<>(super.keySet());
            List<String> activeKeys = new ArrayList<>();
            for (String key : allKeys) {
                if (!checkExpiry(key)) {
                    activeKeys.add(key);
                }
            }
            return activeKeys;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
