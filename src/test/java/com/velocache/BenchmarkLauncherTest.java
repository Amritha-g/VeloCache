package com.velocache;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

public class BenchmarkLauncherTest {

    @Test
    void runBenchmarks() throws Exception {
        // Only run if the system property 'runBenchmarks' is set to prevent standard test phases from slowing down
        if (System.getProperty("runBenchmarks") == null) {
            return;
        }

        Options opt = new OptionsBuilder()
                .include(CacheForgeVsRedisBenchmark.class.getSimpleName())
                .forks(1)
                .build();

        Collection<RunResult> results = new Runner(opt).run();
        assert !results.isEmpty();
    }
}
