package com.velocache.network;

import com.velocache.protocol.RespParser;
import com.velocache.protocol.RespValue;
import java.nio.ByteBuffer;

public class ClientSession {
    private ByteBuffer buffer = ByteBuffer.allocate(1024);

    public void addBytes(ByteBuffer incoming) {
        if (buffer.remaining() < incoming.remaining()) {
            // Grow the buffer
            int newCap = Math.max(buffer.capacity() * 2, buffer.position() + incoming.remaining());
            ByteBuffer newBuffer = ByteBuffer.allocate(newCap);
            buffer.flip();
            newBuffer.put(buffer);
            buffer = newBuffer;
        }
        buffer.put(incoming);
    }

    public RespValue nextMessage() {
        buffer.flip();
        try {
            RespValue msg = RespParser.parse(buffer);
            if (msg == null) {
                // Not enough bytes to parse a complete message.
                // Restore to write mode: position = limit, limit = capacity
                buffer.position(buffer.limit());
                buffer.limit(buffer.capacity());
                return null;
            }
            // Message parsed successfully. Compact remaining bytes in the buffer.
            buffer.compact();
            return msg;
        } catch (Exception e) {
            buffer.clear();
            throw e;
        }
    }
}
