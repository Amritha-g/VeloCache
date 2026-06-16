package com.velocache.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RespParser {

    public static RespValue parse(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            return null;
        }

        int startPos = buffer.position();
        byte typeByte = buffer.get(startPos);

        switch (typeByte) {
            case '+': {
                buffer.get(); // consume '+'
                String line = readLine(buffer);
                if (line == null) {
                    buffer.position(startPos);
                    return null;
                }
                return new RespValue.SimpleString(line);
            }
            case '-': {
                buffer.get(); // consume '-'
                String line = readLine(buffer);
                if (line == null) {
                    buffer.position(startPos);
                    return null;
                }
                return new RespValue.ErrorResp(line);
            }
            case ':': {
                buffer.get(); // consume ':'
                String line = readLine(buffer);
                if (line == null) {
                    buffer.position(startPos);
                    return null;
                }
                try {
                    long val = Long.parseLong(line);
                    return new RespValue.IntegerResp(val);
                } catch (NumberFormatException e) {
                    buffer.position(startPos);
                    return null;
                }
            }
            case '$': {
                buffer.get(); // consume '$'
                String line = readLine(buffer);
                if (line == null) {
                    buffer.position(startPos);
                    return null;
                }
                try {
                    int len = Integer.parseInt(line);
                    if (len == -1) {
                        return new RespValue.NullBulk();
                    }
                    if (buffer.remaining() < len + 2) {
                        buffer.position(startPos);
                        return null;
                    }
                    byte[] data = new byte[len];
                    buffer.get(data);
                    buffer.get(); // consume '\r'
                    buffer.get(); // consume '\n'
                    return new RespValue.BulkString(data);
                } catch (NumberFormatException e) {
                    buffer.position(startPos);
                    return null;
                }
            }
            case '*': {
                buffer.get(); // consume '*'
                String line = readLine(buffer);
                if (line == null) {
                    buffer.position(startPos);
                    return null;
                }
                try {
                    int count = Integer.parseInt(line);
                    if (count == -1) {
                        return new RespValue.NullBulk();
                    }
                    List<RespValue> elements = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        RespValue element = parse(buffer);
                        if (element == null) {
                            buffer.position(startPos);
                            return null;
                        }
                        elements.add(element);
                    }
                    return new RespValue.ArrayResp(elements);
                } catch (NumberFormatException e) {
                    buffer.position(startPos);
                    return null;
                }
            }
            default: {
                // Parse inline command
                String line = readLine(buffer);
                if (line == null) {
                    buffer.position(startPos);
                    return null;
                }
                String[] parts = line.trim().split("\\s+");
                List<RespValue> elements = new ArrayList<>();
                for (String part : parts) {
                    if (!part.isEmpty()) {
                        elements.add(new RespValue.BulkString(part.getBytes(StandardCharsets.UTF_8)));
                    }
                }
                return new RespValue.ArrayResp(elements);
            }
        }
    }

    private static String readLine(ByteBuffer buffer) {
        int start = buffer.position();
        boolean foundCr = false;
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == '\r') {
                foundCr = true;
            } else if (b == '\n' && foundCr) {
                int end = buffer.position() - 2;
                int len = end - start;
                byte[] lineBytes = new byte[len];
                int currentPos = buffer.position();
                buffer.position(start);
                buffer.get(lineBytes);
                buffer.position(currentPos); // restore position after reading line contents
                return new String(lineBytes, StandardCharsets.UTF_8);
            } else {
                foundCr = false;
            }
        }
        buffer.position(start); // reset
        return null;
    }
}
