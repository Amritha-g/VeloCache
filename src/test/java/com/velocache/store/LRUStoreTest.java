package com.velocache.store;

import com.velocache.expiry.ExpiryManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LRUStoreTest {

    @Test
    void testBasicLruEviction() {
        LRUStore store = new LRUStore(3);
        store.put("k1", "v1");
        store.put("k2", "v2");
        store.put("k3", "v3");

        assertEquals(3, store.size());

        // Add 4th item, k1 should be evicted (eldest)
        store.put("k4", "v4");
        assertFalse(store.exists("k1"));
        assertTrue(store.exists("k2"));
        assertTrue(store.exists("k3"));
        assertTrue(store.exists("k4"));
    }

    @Test
    void testLruAccessOrder() {
        LRUStore store = new LRUStore(3);
        store.put("k1", "v1");
        store.put("k2", "v2");
        store.put("k3", "v3");

        // Access k1, making it most recently used
        assertEquals("v1", store.get("k1"));

        // Put k4, k2 (which is now eldest) should be evicted
        store.put("k4", "v4");
        assertTrue(store.exists("k1"));
        assertFalse(store.exists("k2"));
        assertTrue(store.exists("k3"));
        assertTrue(store.exists("k4"));
    }

    @Test
    void testLazyExpiration() throws InterruptedException {
        LRUStore store = new LRUStore(10);
        ExpiryManager expiryManager = new ExpiryManager();
        expiryManager.setStore(store);
        store.setExpiryManager(expiryManager);

        store.put("k1", "v1");
        // Expire in 50ms
        expiryManager.setExpiry("k1", System.currentTimeMillis() + 50);

        assertTrue(store.exists("k1"));
        assertEquals("v1", store.get("k1"));

        Thread.sleep(100);

        // Should return null and evict key lazily
        assertNull(store.get("k1"));
        assertFalse(store.exists("k1"));
    }
}
