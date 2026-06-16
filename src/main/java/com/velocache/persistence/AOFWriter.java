package com.velocache.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class AOFWriter {
    private static final Logger logger = LoggerFactory.getLogger(AOFWriter.class);

    public enum FsyncPolicy {
        ALWAYS,
        EVERYSEC,
        NO
    }

    private final FileChannel aofChannel;
    private final Path aofPath;
    private final FsyncPolicy fsyncPolicy;

    public AOFWriter(String filePath) throws IOException {
        this(filePath, FsyncPolicy.ALWAYS);
    }

    public AOFWriter(String filePath, FsyncPolicy fsyncPolicy) throws IOException {
        this.fsyncPolicy = fsyncPolicy;
        this.aofPath = Path.of(filePath);
        Path parent = aofPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.aofChannel = FileChannel.open(aofPath, 
                StandardOpenOption.CREATE, 
                StandardOpenOption.WRITE, 
                StandardOpenOption.APPEND);
    }

    public synchronized void writeCommand(byte[] encodedCommand) {
        if (aofChannel == null) {
            return;
        }
        try {
            aofChannel.write(ByteBuffer.wrap(encodedCommand));
            
            // Apply fsync policy
            switch (fsyncPolicy) {
                case ALWAYS:
                    aofChannel.force(false); // fsync data immediately to disk
                    break;
                case EVERYSEC:
                    // STUB FOR FUTURE WORK:
                    // Under EVERYSEC, a background daemon scheduler would execute force(false)
                    // once per second to reduce disk I/O bottlenecks.
                    break;
                case NO:
                    // STUB FOR FUTURE WORK:
                    // Under NO, we defer flushing entirely to the OS filesystem cache manager,
                    // relying on the kernel's dirty pages flush algorithm.
                    break;
            }
        } catch (IOException e) {
            logger.error("Failed to write command to AOF", e);
        }
    }

    public synchronized void close() {
        try {
            if (aofChannel != null && aofChannel.isOpen()) {
                aofChannel.close();
            }
        } catch (IOException e) {
            logger.error("Failed to close AOF channel", e);
        }
    }
}
