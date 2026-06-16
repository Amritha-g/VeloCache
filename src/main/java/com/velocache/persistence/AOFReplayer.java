package com.velocache.persistence;

import com.velocache.commands.CommandRegistry;
import com.velocache.protocol.RespParser;
import com.velocache.protocol.RespValue;
import com.velocache.store.CacheStore;
import com.velocache.expiry.ExpiryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class AOFReplayer {
    private static final Logger logger = LoggerFactory.getLogger(AOFReplayer.class);

    public static void replay(String filePath, CacheStore store, ExpiryManager expiry, CommandRegistry registry) {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            logger.info("AOF file does not exist, skipping replay.");
            return;
        }

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size == 0) {
                return;
            }
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            channel.read(buffer);
            buffer.flip();

            int count = 0;
            while (buffer.hasRemaining()) {
                RespValue val = RespParser.parse(buffer);
                if (val == null) {
                    logger.warn("Incomplete AOF command detected at position {}", buffer.position());
                    break;
                }

                if (val instanceof RespValue.ArrayResp arr) {
                    List<RespValue> elements = arr.elements();
                    if (!elements.isEmpty()) {
                        String cmd = getArgString(elements, 0);
                        // Pass null AOFWriter during replay to prevent duplicate logging
                        registry.dispatch(cmd, store, elements, expiry, null);
                        count++;
                    }
                }
            }
            logger.info("AOF replay complete. Executed {} commands.", count);
        } catch (IOException e) {
            logger.error("Failed to replay AOF file", e);
        }
    }

    private static String getArgString(List<RespValue> args, int index) {
        RespValue val = args.get(index);
        if (val instanceof RespValue.BulkString bs) {
            return bs.asString();
        } else if (val instanceof RespValue.SimpleString ss) {
            return ss.value();
        }
        return val.toString();
    }
}
