package com.velocache.store;

import com.velocache.expiry.ExpiryManager;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LFUStore implements CacheStore {
    private final int maxSize;
    private final Map<String, Object> dataMap;
    private final Map<String, Integer> counts;
    private final Map<Integer, LinkedHashSet<String>> frequencies;
    private int minFrequency = -1;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private ExpiryManager expiryManager;

    public LFUStore(int maxSize) {
        this.maxSize = maxSize;
        this.dataMap = new HashMap<>();
        this.counts = new HashMap<>();
        this.frequencies = new HashMap<>();
    }

    public void setExpiryManager(ExpiryManager expiryManager) {
        this.lock.writeLock().lock();
        try {
            this.expiryManager = expiryManager;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private boolean checkExpiry(String key) {
        if (expiryManager != null && expiryManager.isExpired(key)) {
            removeInternal(key);
            return true;
        }
        return false;
    }

    private void removeInternal(String key) {
        dataMap.remove(key);
        Integer frequency = counts.remove(key);
        if (frequency != null) {
            LinkedHashSet<String> bucket = frequencies.get(frequency);
            if (bucket != null) {
                bucket.remove(key);
                if (bucket.isEmpty()) {
                    frequencies.remove(frequency);
                    if (frequency == minFrequency) {
                        // We will recalculate minFrequency when evicting if needed, 
                        // or just leave it since it will be updated on the next put.
                    }
                }
            }
        }
        if (expiryManager != null) {
            expiryManager.removeExpiry(key);
        }
    }

    private void updateFrequency(String key) {
        int count = counts.get(key);
        counts.put(key, count + 1);
        
        LinkedHashSet<String> currentBucket = frequencies.get(count);
        if (currentBucket != null) {
            currentBucket.remove(key);
            if (currentBucket.isEmpty()) {
                frequencies.remove(count);
                if (count == minFrequency) {
                    minFrequency++;
                }
            }
        }
        
        frequencies.computeIfAbsent(count + 1, k -> new LinkedHashSet<>()).add(key);
    }

    @Override
    public Object get(String key) {
        lock.writeLock().lock(); // Using write lock because it updates counts and frequencies maps
        try {
            if (checkExpiry(key)) {
                return null;
            }
            if (!dataMap.containsKey(key)) {
                return null;
            }
            updateFrequency(key);
            return dataMap.get(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Object put(String key, Object value) {
        lock.writeLock().lock();
        try {
            if (maxSize <= 0) {
                return null;
            }

            if (dataMap.containsKey(key)) {
                Object old = dataMap.put(key, value);
                updateFrequency(key);
                return old;
            }

            if (dataMap.size() >= maxSize) {
                evict();
            }

            dataMap.put(key, value);
            counts.put(key, 1);
            frequencies.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(key);
            minFrequency = 1;
            return null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void evict() {
        if (frequencies.isEmpty()) {
            return;
        }
        
        // Find correct minFrequency if current is invalid
        while (minFrequency > 0 && !frequencies.containsKey(minFrequency)) {
            minFrequency++;
        }
        if (minFrequency <= 0 || !frequencies.containsKey(minFrequency)) {
            // scan for the actual minimum
            minFrequency = frequencies.keySet().stream().min(Integer::compare).orElse(-1);
        }

        if (minFrequency != -1) {
            LinkedHashSet<String> bucket = frequencies.get(minFrequency);
            if (bucket != null && !bucket.isEmpty()) {
                String evictKey = bucket.iterator().next();
                removeInternal(evictKey);
            }
        }
    }

    @Override
    public boolean remove(String key) {
        lock.writeLock().lock();
        try {
            boolean existed = dataMap.containsKey(key);
            removeInternal(key);
            return existed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean exists(String key) {
        lock.writeLock().lock();
        try {
            if (checkExpiry(key)) {
                return false;
            }
            return dataMap.containsKey(key);
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
            checkExpiry(key);
            Object current = dataMap.get(key);
            long val = 0;
            if (current != null) {
                try {
                    val = Long.parseLong(current.toString());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("ERR value is not an integer or out of range");
                }
            }
            long newVal = val + amount;
            put(key, String.valueOf(newVal));
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
            Object current = dataMap.get(key);
            String currentStr = current == null ? "" : current.toString();
            put(key, currentStr + value);
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
            Object current = dataMap.get(key);
            return current == null ? 0 : current.toString().length();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            dataMap.clear();
            counts.clear();
            frequencies.clear();
            minFrequency = -1;
            if (expiryManager != null) {
                expiryManager.clear();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return dataMap.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<String> keys() {
        lock.writeLock().lock();
        try {
            List<String> allKeys = new ArrayList<>(dataMap.keySet());
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
