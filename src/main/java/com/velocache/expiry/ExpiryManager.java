package com.velocache.expiry;

import com.velocache.store.CacheStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public class ExpiryManager {
    private static final Logger logger = LoggerFactory.getLogger(ExpiryManager.class);

    private final ConcurrentHashMap<String, Long> expiryMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "velocache-expiry-scanner");
        thread.setDaemon(true);
        return thread;
    });
    private CacheStore store;

    public ExpiryManager() {}

    public void setStore(CacheStore store) {
        this.store = store;
    }

    public void setExpiry(String key, long expiryMillis) {
        expiryMap.put(key, expiryMillis);
    }

    public Long getExpiry(String key) {
        return expiryMap.get(key);
    }

    public void removeExpiry(String key) {
        expiryMap.remove(key);
    }

    public boolean isExpired(String key) {
        Long expiry = expiryMap.get(key);
        if (expiry == null) {
            return false;
        }
        return System.currentTimeMillis() > expiry;
    }

    public void startActiveScan() {
        scheduler.scheduleAtFixedRate(this::scanExpiredKeys, 100, 100, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public int size() {
        return expiryMap.size();
    }

    public void clear() {
        expiryMap.clear();
    }

    private void scanExpiredKeys() {
        if (store == null || expiryMap.isEmpty()) {
            return;
        }

        int maxScan = 20;
        int expiredCount = 0;

        List<String> keys = new ArrayList<>(expiryMap.keySet());
        if (keys.isEmpty()) {
            return;
        }

        Collections.shuffle(keys);
        int sampleSize = Math.min(maxScan, keys.size());

        long now = System.currentTimeMillis();
        for (int i = 0; i < sampleSize; i++) {
            String key = keys.get(i);
            Long exp = expiryMap.get(key);
            if (exp != null && now > exp) {
                if (store.remove(key)) {
                    expiredCount++;
                }
            }
        }

        if (sampleSize > 0 && (double) expiredCount / sampleSize > 0.25) {
            CompletableFuture.runAsync(this::scanExpiredKeys);
        }
    }
}
