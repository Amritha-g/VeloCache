package com.velocache;

import com.velocache.commands.CommandRegistry;
import com.velocache.expiry.ExpiryManager;
import com.velocache.network.NioServer;
import com.velocache.persistence.AOFReplayer;
import com.velocache.persistence.AOFWriter;
import com.velocache.store.CacheStore;
import com.velocache.store.LFUStore;
import com.velocache.store.LRUStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class VeloCacheServer {
    private static final Logger logger = LoggerFactory.getLogger(VeloCacheServer.class);

    public static void main(String[] args) {
        int port = 6380;
        int capacity = 10000;
        String policy = "lru";
        String aofPath = "data/velocache.aof";

        // Command line arguments parsing
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-p") && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-c") && i + 1 < args.length) {
                capacity = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-policy") && i + 1 < args.length) {
                policy = args[++i].toLowerCase();
            } else if (args[i].equals("-aof") && i + 1 < args.length) {
                aofPath = args[++i];
            }
        }

        logger.info("Starting VeloCache Server...");
        logger.info("Config - Port: {}, Capacity: {}, Policy: {}, AOF Path: {}", port, capacity, policy, aofPath);

        CacheStore store;
        ExpiryManager expiryManager = new ExpiryManager();

        if (policy.equals("lfu")) {
            LFUStore lfuStore = new LFUStore(capacity);
            lfuStore.setExpiryManager(expiryManager);
            store = lfuStore;
        } else {
            LRUStore lruStore = new LRUStore(capacity);
            lruStore.setExpiryManager(expiryManager);
            store = lruStore;
        }
        expiryManager.setStore(store);

        // Parse and replay AOF before starting server and writing new logs
        CommandRegistry registry = new CommandRegistry();
        AOFReplayer.replay(aofPath, store, expiryManager, registry);

        // Open live updates AOF writer
        AOFWriter aofWriter = null;
        try {
            aofWriter = new AOFWriter(aofPath);
        } catch (IOException e) {
            logger.error("Failed to initialize AOFWriter, running in memory-only mode.", e);
        }

        NioServer nioServer = new NioServer(port, store, expiryManager, registry, aofWriter);
        try {
            nioServer.start();
            expiryManager.startActiveScan();
        } catch (IOException e) {
            logger.error("Failed to start NIO server loop", e);
            System.exit(1);
        }

        // JVM Shutdown hook
        AOFWriter finalAofWriter = aofWriter;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook invoked. Terminating VeloCache processes...");
            nioServer.stop();
            expiryManager.stop();
            if (finalAofWriter != null) {
                finalAofWriter.close();
            }
            logger.info("VeloCache shutdown finalized.");
        }, "velocache-shutdown-thread"));

        try {
            // Keep main thread alive
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.info("Main thread interrupted, exiting.");
        }
    }
}
