package com.velocache;

import com.velocache.store.LRUStore;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConcurrencyTest {

    @Test
    void concurrentIncr() throws Exception {
        LRUStore store = new LRUStore(10000);
        int threads = 50, incrsEach = 1000;
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < incrsEach; j++) {
                        store.incr("seq");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        pool.shutdown();
        
        Object finalVal = store.get("seq");
        assert finalVal != null;
        assertEquals(50000L, Long.parseLong(finalVal.toString()));
    }
}
