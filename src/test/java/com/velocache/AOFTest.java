package com.velocache;

import com.velocache.commands.CommandRegistry;
import com.velocache.expiry.ExpiryManager;
import com.velocache.persistence.AOFReplayer;
import com.velocache.persistence.AOFWriter;
import com.velocache.protocol.RespValue;
import com.velocache.store.LRUStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class AOFTest {
    private static final String TEST_AOF_PATH = "data/test_velocache.aof";

    @BeforeEach
    @AfterEach
    void cleanUp() throws IOException {
        Files.deleteIfExists(Path.of(TEST_AOF_PATH));
    }

    @Test
    void testAofWriteAndReplay() throws Exception {
        LRUStore originalStore = new LRUStore(100);
        ExpiryManager originalExpiry = new ExpiryManager();
        originalStore.setExpiryManager(originalExpiry);
        originalExpiry.setStore(originalStore);

        AOFWriter writer = new AOFWriter(TEST_AOF_PATH);
        CommandRegistry registry = new CommandRegistry();

        // 1. SET k1 v1
        registry.dispatch("SET", originalStore, List.of(
                new RespValue.BulkString("SET".getBytes(StandardCharsets.UTF_8)),
                new RespValue.BulkString("k1".getBytes(StandardCharsets.UTF_8)),
                new RespValue.BulkString("v1".getBytes(StandardCharsets.UTF_8))
        ), originalExpiry, writer);

        // 2. SET k2 v2 EX 10
        registry.dispatch("SET", originalStore, List.of(
                new RespValue.BulkString("SET".getBytes(StandardCharsets.UTF_8)),
                new RespValue.BulkString("k2".getBytes(StandardCharsets.UTF_8)),
                new RespValue.BulkString("v2".getBytes(StandardCharsets.UTF_8)),
                new RespValue.BulkString("EX".getBytes(StandardCharsets.UTF_8)),
                new RespValue.BulkString("10".getBytes(StandardCharsets.UTF_8))
        ), originalExpiry, writer);

        // 3. INCR myseq
        registry.dispatch("INCR", originalStore, List.of(
                new RespValue.BulkString("INCR".getBytes(StandardCharsets.UTF_8)),
                new RespValue.BulkString("myseq".getBytes(StandardCharsets.UTF_8))
        ), originalExpiry, writer);

        writer.close();

        // Check original store state
        assertEquals("v1", originalStore.get("k1"));
        assertEquals("v2", originalStore.get("k2"));
        assertEquals("1", originalStore.get("myseq"));
        assertNotNull(originalExpiry.getExpiry("k2"));

        // Now recreate store and replay
        LRUStore newStore = new LRUStore(100);
        ExpiryManager newExpiry = new ExpiryManager();
        newStore.setExpiryManager(newExpiry);
        newExpiry.setStore(newStore);

        AOFReplayer.replay(TEST_AOF_PATH, newStore, newExpiry, registry);

        // Verify state replayed correctly
        assertEquals("v1", newStore.get("k1"));
        assertEquals("v2", newStore.get("k2"));
        assertEquals("1", newStore.get("myseq"));
        assertNotNull(newExpiry.getExpiry("k2"));
    }
}
