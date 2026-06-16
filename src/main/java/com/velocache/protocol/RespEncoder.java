package com.velocache.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class RespEncoder {

    private static final byte[] CRLF = new byte[]{'\r', '\n'};

    public static byte[] encode(Object obj) {
        if (obj == null) {
            return encodeNullBulk();
        }

        if (obj instanceof RespValue val) {
            return encodeValue(val);
        }

        if (obj instanceof String s) {
            return encodeBulkString(s.getBytes(StandardCharsets.UTF_8));
        }

        if (obj instanceof byte[] bytes) {
            return encodeBulkString(bytes);
        }

        if (obj instanceof Number n) {
            return encodeInteger(n.longValue());
        }

        if (obj instanceof Boolean b) {
            return encodeInteger(b ? 1 : 0);
        }

        if (obj instanceof List<?> list) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                out.write(('*' + String.valueOf(list.size()) + "\r\n").getBytes(StandardCharsets.UTF_8));
                for (Object item : list) {
                    out.write(encode(item));
                }
            } catch (IOException ignored) {}
            return out.toByteArray();
        }

        return encodeError("ERR unknown object serialization type: " + obj.getClass().getName());
    }

    private static byte[] encodeValue(RespValue val) {
        if (val instanceof RespValue.SimpleString s) {
            return ("+" + s.value() + "\r\n").getBytes(StandardCharsets.UTF_8);
        } else if (val instanceof RespValue.ErrorResp e) {
            return ("-" + e.message() + "\r\n").getBytes(StandardCharsets.UTF_8);
        } else if (val instanceof RespValue.IntegerResp i) {
            return (":" + i.value() + "\r\n").getBytes(StandardCharsets.UTF_8);
        } else if (val instanceof RespValue.BulkString b) {
            return encodeBulkString(b.value());
        } else if (val instanceof RespValue.NullBulk) {
            return encodeNullBulk();
        } else if (val instanceof RespValue.ArrayResp a) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                out.write(('*' + String.valueOf(a.elements().size()) + "\r\n").getBytes(StandardCharsets.UTF_8));
                for (RespValue item : a.elements()) {
                    out.write(encodeValue(item));
                }
            } catch (IOException ignored) {}
            return out.toByteArray();
        }
        return encodeError("ERR unknown RespValue permit type");
    }

    public static byte[] encodeSimpleString(String s) {
        return ("+" + s + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] encodeError(String err) {
        return ("-" + err + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] encodeInteger(long val) {
        return (":" + val + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] encodeBulkString(byte[] data) {
        if (data == null) {
            return encodeNullBulk();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(('$' + String.valueOf(data.length) + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(data);
            out.write(CRLF);
        } catch (IOException ignored) {}
        return out.toByteArray();
    }

    public static byte[] encodeNullBulk() {
        return "$-1\r\n".getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Helper to encode commands dynamically.
     * e.g. command = "SET", args = ["mykey", "myval"]
     * becomes *3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$5\r\nmyval\r\n
     */
    public static byte[] encodeCommand(String cmd, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            int total = 1 + (args != null ? args.length : 0);
            out.write(('*' + String.valueOf(total) + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(encodeBulkString(cmd.getBytes(StandardCharsets.UTF_8)));
            if (args != null) {
                for (String arg : args) {
                    out.write(encodeBulkString(arg.getBytes(StandardCharsets.UTF_8)));
                }
            }
        } catch (IOException ignored) {}
        return out.toByteArray();
    }
}
