# VeloCache

VeloCache is a Redis-compatible distributed in-memory cache written in pure Java 17, using Java NIO for connection management, custom storage engines (LRU / LFU), active/lazy expiration routines, AOF transaction logs, and TreeMap-based consistent hashing.

## Quick Start

### Compile and Run Tests
```bash
mvn clean test
```

### Launch VeloCache Server
Run the default server (starts on port `6380` with LRU eviction and AOF enabled):
```bash
mvn exec:java -Dexec.mainClass="com.velocache.VeloCacheServer"
```

Configure custom options via command-line arguments:
```bash
mvn exec:java -Dexec.mainClass="com.velocache.VeloCacheServer" -Dexec.args="-p 6380 -c 10000 -policy lru -aof data/velocache.aof"
```
* **Supported Arguments**:
  - `-p <port>`: Port to bind the server (default: `6380`)
  - `-c <capacity>`: Cache capacity (default: `10000`)
  - `-policy <lru|lfu>`: Cache eviction strategy (default: `lru`)
  - `-aof <path>`: Append-only file path (default: `data/velocache.aof`)

### Connect and Test
Verify the connection using `nc` (netcat) or `redis-cli`:
```bash
# Using netcat
printf "PING\r\n" | nc 127.0.0.1 6380

# Using redis-cli
redis-cli -p 6380 PING
```

### Run Benchmarks
To execute the JMH performance benchmark suite programmatically:
```bash
mvn test -DrunBenchmarks=true
```

---

## Scope & Limitations

VeloCache is a high-performance in-memory key-value server but is not yet fully production-hardened. Below are the key scope limitations and future development paths:

1. **No TLS/SSL Encrypted Network Traffic**
   - **Current state**: Socket connections communicate using plaintext RESP commands.
   - **Risk**: Susceptible to eavesdropping and packet sniffing on untrusted networks.
   - **Future work**: Integration of `SSLEngine` into the Java NIO server selector loop for TLS termination.

2. **No Authentication & Access Control (AUTH/ACL)**
   - **Current state**: Any client that can reach port 6380 has unrestricted root read/write privileges.
   - **Risk**: Lack of authentication or command-level authorization.
   - **Future work**: Addition of standard Redis `AUTH` command parsing and command validation hooks.

3. **Hardwired Durability Policy (AOF fsyncPolicy)**
   - **Current state**: The persistence engine is configured to execute fsync (`aofChannel.force(false)`) after **every single modifying command** (the equivalent of Redis' `appendfsync always`).
   - **Performance impact**: Ensures data survival across sudden crashes, but introduces disk I/O bottlenecks.
   - **Future work**: The system exposes config stubs for `everysec` (flush via background scheduler every second) and `no` (defer flushing to the operating system), but these are currently inactive.

4. **Crash Consistency & Recovery Gaps**
   - **Current state**: On startup, VeloCache replays mutations sequentially from `data/velocache.aof`. There is no snapshot checkpointing (RDB), transactional rollback, or checksum integrity verification.
   - **Risk**: A corrupt AOF entry at the end of the file stops replay at that point, potentially leading to lost edits.
   - **Future work**: Add AOF file verification tools (equivalent to `redis-check-aof`) and background AOF rewriting (BGREWRITEAOF) to compress the log size.

5. **No Docker Deployment Dependency**
   - **Current state**: Built and run directly as a local Java 17 process to operate within restricted local sandbox developer constraints.
