package com.velocache;

import com.velocache.store.LRUStore;
import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Threads(8)
public class CacheForgeVsRedisBenchmark {

    private LRUStore store;

    @Setup(Level.Trial)
    public void setup() {
        store = new LRUStore(10000);
        // Pre-populate keys to read
        for (int i = 0; i < 5000; i++) {
            store.put("key-" + i, "value-" + i);
        }
    }

    @Benchmark
    public void testGet(org.openjdk.jmh.infra.Blackhole blackhole) {
        blackhole.consume(store.get("key-100"));
    }

    @Benchmark
    public void testPut(org.openjdk.jmh.infra.Blackhole blackhole) {
        blackhole.consume(store.put("key-write", "some-value"));
    }
}
