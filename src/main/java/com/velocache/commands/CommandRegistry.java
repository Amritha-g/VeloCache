package com.velocache.commands;

import com.velocache.protocol.RespEncoder;
import com.velocache.protocol.RespValue;
import com.velocache.store.CacheStore;
import com.velocache.expiry.ExpiryManager;
import com.velocache.persistence.AOFWriter;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class CommandRegistry {
    private final Map<String, CommandHandler> registry = new HashMap<>();

    public CommandRegistry() {
        registerAll();
    }

    public void register(String name, CommandHandler handler) {
        registry.put(name.toUpperCase(), handler);
    }

    public RespValue dispatch(String name, CacheStore store, List<RespValue> args, ExpiryManager expiry, AOFWriter aofWriter) {
        CommandHandler handler = registry.get(name.toUpperCase());
        if (handler == null) {
            return new RespValue.ErrorResp("ERR unknown command '" + name + "'");
        }
        try {
            return handler.handle(store, args, expiry, aofWriter);
        } catch (IllegalArgumentException e) {
            return new RespValue.ErrorResp(e.getMessage());
        } catch (Exception e) {
            return new RespValue.ErrorResp("ERR execution error: " + e.getMessage());
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

    private void registerAll() {
        // PING
        register("PING", (store, args, expiry, aofWriter) -> {
            if (args.size() == 1) {
                return new RespValue.SimpleString("PONG");
            } else if (args.size() == 2) {
                return args.get(1);
            } else {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'ping' command");
            }
        });

        // SET key value [EX seconds | PX milliseconds] [NX | XX]
        register("SET", (store, args, expiry, aofWriter) -> {
            if (args.size() < 3) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'set' command");
            }
            String key = getArgString(args, 1);
            String value = getArgString(args, 2);
            long ttlMillis = -1;
            boolean nx = false;
            boolean xx = false;

            for (int i = 3; i < args.size(); i++) {
                String opt = getArgString(args, i).toUpperCase();
                if (opt.equals("EX")) {
                    if (i + 1 >= args.size()) {
                        return new RespValue.ErrorResp("ERR syntax error");
                    }
                    ttlMillis = Long.parseLong(getArgString(args, ++i)) * 1000L;
                } else if (opt.equals("PX")) {
                    if (i + 1 >= args.size()) {
                        return new RespValue.ErrorResp("ERR syntax error");
                    }
                    ttlMillis = Long.parseLong(getArgString(args, ++i));
                } else if (opt.equals("NX")) {
                    nx = true;
                } else if (opt.equals("XX")) {
                    xx = true;
                } else {
                    return new RespValue.ErrorResp("ERR syntax error");
                }
            }

            boolean exists = store.exists(key);
            if (nx && exists) {
                return new RespValue.NullBulk();
            }
            if (xx && !exists) {
                return new RespValue.NullBulk();
            }

            store.put(key, value);
            if (ttlMillis > 0) {
                expiry.setExpiry(key, System.currentTimeMillis() + ttlMillis);
            } else {
                expiry.removeExpiry(key);
            }

            if (aofWriter != null) {
                aofWriter.writeCommand(RespEncoder.encode(new RespValue.ArrayResp(args)));
            }
            return new RespValue.SimpleString("OK");
        });

        // GET key
        register("GET", (store, args, expiry, aofWriter) -> {
            if (args.size() != 2) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'get' command");
            }
            String key = getArgString(args, 1);
            Object val = store.get(key);
            if (val == null) {
                return new RespValue.NullBulk();
            }
            if (val instanceof List) {
                return new RespValue.ErrorResp("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            return new RespValue.BulkString(val.toString().getBytes(StandardCharsets.UTF_8));
        });

        // DEL key [key ...]
        register("DEL", (store, args, expiry, aofWriter) -> {
            if (args.size() < 2) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'del' command");
            }
            long deletedCount = 0;
            for (int i = 1; i < args.size(); i++) {
                String key = getArgString(args, i);
                if (store.remove(key)) {
                    deletedCount++;
                }
            }
            if (deletedCount > 0 && aofWriter != null) {
                aofWriter.writeCommand(RespEncoder.encode(new RespValue.ArrayResp(args)));
            }
            return new RespValue.IntegerResp(deletedCount);
        });

        // EXISTS key [key ...]
        register("EXISTS", (store, args, expiry, aofWriter) -> {
            if (args.size() < 2) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'exists' command");
            }
            long existsCount = 0;
            for (int i = 1; i < args.size(); i++) {
                String key = getArgString(args, i);
                if (store.exists(key)) {
                    existsCount++;
                }
            }
            return new RespValue.IntegerResp(existsCount);
        });

        // INCR key
        register("INCR", (store, args, expiry, aofWriter) -> {
            if (args.size() != 2) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'incr' command");
            }
            String key = getArgString(args, 1);
            try {
                long val = store.incr(key);
                if (aofWriter != null) {
                    aofWriter.writeCommand(RespEncoder.encode(new RespValue.ArrayResp(args)));
                }
                return new RespValue.IntegerResp(val);
            } catch (IllegalArgumentException e) {
                return new RespValue.ErrorResp(e.getMessage());
            }
        });

        // INCRBY key increment
        register("INCRBY", (store, args, expiry, aofWriter) -> {
            if (args.size() != 3) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'incrby' command");
            }
            String key = getArgString(args, 1);
            long increment = Long.parseLong(getArgString(args, 2));
            try {
                long val = store.incrBy(key, increment);
                if (aofWriter != null) {
                    aofWriter.writeCommand(RespEncoder.encode(new RespValue.ArrayResp(args)));
                }
                return new RespValue.IntegerResp(val);
            } catch (IllegalArgumentException e) {
                return new RespValue.ErrorResp(e.getMessage());
            }
        });

        // APPEND key value
        register("APPEND", (store, args, expiry, aofWriter) -> {
            if (args.size() != 3) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'append' command");
            }
            String key = getArgString(args, 1);
            String value = getArgString(args, 2);
            store.append(key, value);
            if (aofWriter != null) {
                aofWriter.writeCommand(RespEncoder.encode(new RespValue.ArrayResp(args)));
            }
            return new RespValue.IntegerResp(store.strlen(key));
        });

        // STRLEN key
        register("STRLEN", (store, args, expiry, aofWriter) -> {
            if (args.size() != 2) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'strlen' command");
            }
            String key = getArgString(args, 1);
            return new RespValue.IntegerResp(store.strlen(key));
        });

        // MGET key [key ...]
        register("MGET", (store, args, expiry, aofWriter) -> {
            if (args.size() < 2) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'mget' command");
            }
            List<RespValue> list = new ArrayList<>();
            for (int i = 1; i < args.size(); i++) {
                String key = getArgString(args, i);
                Object val = store.get(key);
                if (val == null || val instanceof List) {
                    list.add(new RespValue.NullBulk());
                } else {
                    list.add(new RespValue.BulkString(val.toString().getBytes(StandardCharsets.UTF_8)));
                }
            }
            return new RespValue.ArrayResp(list);
        });

        // MSET key value [key value ...]
        register("MSET", (store, args, expiry, aofWriter) -> {
            if (args.size() < 3 || (args.size() - 1) % 2 != 0) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'mset' command");
            }
            for (int i = 1; i < args.size(); i += 2) {
                String key = getArgString(args, i);
                String value = getArgString(args, i + 1);
                store.put(key, value);
                expiry.removeExpiry(key);
            }
            if (aofWriter != null) {
                aofWriter.writeCommand(RespEncoder.encode(new RespValue.ArrayResp(args)));
            }
            return new RespValue.SimpleString("OK");
        });

        // EXPIRE key seconds
        register("EXPIRE", (store, args, expiry, aofWriter) -> {
            if (args.size() != 3) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'expire' command");
            }
            String key = getArgString(args, 1);
            long seconds = Long.parseLong(getArgString(args, 2));
            if (store.exists(key)) {
                expiry.setExpiry(key, System.currentTimeMillis() + (seconds * 1000L));
                if (aofWriter != null) {
                    aofWriter.writeCommand(RespEncoder.encode(new RespValue.ArrayResp(args)));
                }
                return new RespValue.IntegerResp(1);
            }
            return new RespValue.IntegerResp(0);
        });

        // TTL key
        register("TTL", (store, args, expiry, aofWriter) -> {
            if (args.size() != 2) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'ttl' command");
            }
            String key = getArgString(args, 1);
            if (!store.exists(key)) {
                return new RespValue.IntegerResp(-2);
            }
            Long exp = expiry.getExpiry(key);
            if (exp == null) {
                return new RespValue.IntegerResp(-1);
            }
            long diff = exp - System.currentTimeMillis();
            return new RespValue.IntegerResp(Math.max(0L, diff / 1000L));
        });

        // PERSIST key
        register("PERSIST", (store, args, expiry, aofWriter) -> {
            if (args.size() != 2) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'persist' command");
            }
            String key = getArgString(args, 1);
            if (!store.exists(key)) {
                return new RespValue.IntegerResp(0);
            }
            if (expiry.getExpiry(key) != null) {
                expiry.removeExpiry(key);
                if (aofWriter != null) {
                    aofWriter.writeCommand(RespEncoder.encode(new RespValue.ArrayResp(args)));
                }
                return new RespValue.IntegerResp(1);
            }
            return new RespValue.IntegerResp(0);
        });

        // PTTL key
        register("PTTL", (store, args, expiry, aofWriter) -> {
            if (args.size() != 2) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'pttl' command");
            }
            String key = getArgString(args, 1);
            if (!store.exists(key)) {
                return new RespValue.IntegerResp(-2);
            }
            Long exp = expiry.getExpiry(key);
            if (exp == null) {
                return new RespValue.IntegerResp(-1);
            }
            long diff = exp - System.currentTimeMillis();
            return new RespValue.IntegerResp(Math.max(0L, diff));
        });

        // LPUSH key value [value ...]
        register("LPUSH", (store, args, expiry, aofWriter) -> {
            if (args.size() < 3) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'lpush' command");
            }
            String key = getArgString(args, 1);
            Object existing = store.get(key);
            List<String> list;
            if (existing == null) {
                list = new ArrayList<>();
            } else if (existing instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> tmp = (List<String>) existing;
                list = tmp;
            } else {
                return new RespValue.ErrorResp("WRONGTYPE Operation against a key holding the wrong kind of value");
            }

            for (int i = 2; i < args.size(); i++) {
                list.add(0, getArgString(args, i));
            }
            store.put(key, list);

            if (aofWriter != null) {
                aofWriter.writeCommand(RespEncoder.encode(new RespValue.ArrayResp(args)));
            }
            return new RespValue.IntegerResp(list.size());
        });

        // RPUSH key value [value ...]
        register("RPUSH", (store, args, expiry, aofWriter) -> {
            if (args.size() < 3) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'rpush' command");
            }
            String key = getArgString(args, 1);
            Object existing = store.get(key);
            List<String> list;
            if (existing == null) {
                list = new ArrayList<>();
            } else if (existing instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> tmp = (List<String>) existing;
                list = tmp;
            } else {
                return new RespValue.ErrorResp("WRONGTYPE Operation against a key holding the wrong kind of value");
            }

            for (int i = 2; i < args.size(); i++) {
                list.add(getArgString(args, i));
            }
            store.put(key, list);

            if (aofWriter != null) {
                aofWriter.writeCommand(RespEncoder.encode(new RespValue.ArrayResp(args)));
            }
            return new RespValue.IntegerResp(list.size());
        });

        // LPOP key [count]
        register("LPOP", (store, args, expiry, aofWriter) -> {
            if (args.size() < 2 || args.size() > 3) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'lpop' command");
            }
            String key = getArgString(args, 1);
            Object existing = store.get(key);
            if (existing == null) {
                return new RespValue.NullBulk();
            }
            if (!(existing instanceof List)) {
                return new RespValue.ErrorResp("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) existing;
            if (list.isEmpty()) {
                return new RespValue.NullBulk();
            }

            int count = 1;
            boolean multi = false;
            if (args.size() == 3) {
                count = Integer.parseInt(getArgString(args, 2));
                multi = true;
            }

            if (multi) {
                List<RespValue> elements = new ArrayList<>();
                int toPop = Math.min(count, list.size());
                for (int i = 0; i < toPop; i++) {
                    elements.add(new RespValue.BulkString(list.remove(0).getBytes(StandardCharsets.UTF_8)));
                }
                store.put(key, list);
                if (aofWriter != null) {
                    aofWriter.writeCommand(RespEncoder.encode(new RespValue.ArrayResp(args)));
                }
                return new RespValue.ArrayResp(elements);
            } else {
                String popped = list.remove(0);
                store.put(key, list);
                if (aofWriter != null) {
                    aofWriter.writeCommand(RespEncoder.encode(new RespValue.ArrayResp(args)));
                }
                return new RespValue.BulkString(popped.getBytes(StandardCharsets.UTF_8));
            }
        });

        // RPOP key [count]
        register("RPOP", (store, args, expiry, aofWriter) -> {
            if (args.size() < 2 || args.size() > 3) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'rpop' command");
            }
            String key = getArgString(args, 1);
            Object existing = store.get(key);
            if (existing == null) {
                return new RespValue.NullBulk();
            }
            if (!(existing instanceof List)) {
                return new RespValue.ErrorResp("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) existing;
            if (list.isEmpty()) {
                return new RespValue.NullBulk();
            }

            int count = 1;
            boolean multi = false;
            if (args.size() == 3) {
                count = Integer.parseInt(getArgString(args, 2));
                multi = true;
            }

            if (multi) {
                List<RespValue> elements = new ArrayList<>();
                int toPop = Math.min(count, list.size());
                for (int i = 0; i < toPop; i++) {
                    elements.add(new RespValue.BulkString(list.remove(list.size() - 1).getBytes(StandardCharsets.UTF_8)));
                }
                store.put(key, list);
                if (aofWriter != null) {
                    aofWriter.writeCommand(RespEncoder.encode(new RespValue.ArrayResp(args)));
                }
                return new RespValue.ArrayResp(elements);
            } else {
                String popped = list.remove(list.size() - 1);
                store.put(key, list);
                if (aofWriter != null) {
                    aofWriter.writeCommand(RespEncoder.encode(new RespValue.ArrayResp(args)));
                }
                return new RespValue.BulkString(popped.getBytes(StandardCharsets.UTF_8));
            }
        });

        // LRANGE key start stop
        register("LRANGE", (store, args, expiry, aofWriter) -> {
            if (args.size() != 4) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'lrange' command");
            }
            String key = getArgString(args, 1);
            Object existing = store.get(key);
            if (existing == null) {
                return new RespValue.ArrayResp(Collections.emptyList());
            }
            if (!(existing instanceof List)) {
                return new RespValue.ErrorResp("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) existing;
            int start = Integer.parseInt(getArgString(args, 2));
            int stop = Integer.parseInt(getArgString(args, 3));

            int len = list.size();
            if (start < 0) start = len + start;
            if (stop < 0) stop = len + stop;

            start = Math.max(0, start);
            stop = Math.min(len - 1, stop);

            if (start > stop || start >= len) {
                return new RespValue.ArrayResp(Collections.emptyList());
            }

            List<RespValue> result = new ArrayList<>();
            for (int i = start; i <= stop; i++) {
                result.add(new RespValue.BulkString(list.get(i).getBytes(StandardCharsets.UTF_8)));
            }
            return new RespValue.ArrayResp(result);
        });

        // LLEN key
        register("LLEN", (store, args, expiry, aofWriter) -> {
            if (args.size() != 2) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'llen' command");
            }
            String key = getArgString(args, 1);
            Object existing = store.get(key);
            if (existing == null) {
                return new RespValue.IntegerResp(0);
            }
            if (!(existing instanceof List)) {
                return new RespValue.ErrorResp("WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) existing;
            return new RespValue.IntegerResp(list.size());
        });

        // INFO
        register("INFO", (store, args, expiry, aofWriter) -> {
            StringBuilder info = new StringBuilder();
            info.append("# Server\r\n");
            info.append("redis_version:2.8.24-velocache\r\n");
            info.append("# Keyspace\r\n");
            info.append(String.format("db0:keys=%d,expires=%d,avg_ttl=0\r\n", store.size(), expiry.size()));
            return new RespValue.BulkString(info.toString().getBytes(StandardCharsets.UTF_8));
        });

        // FLUSHDB
        register("FLUSHDB", (store, args, expiry, aofWriter) -> {
            store.clear();
            expiry.clear();
            if (aofWriter != null) {
                aofWriter.writeCommand(RespEncoder.encode(new RespValue.ArrayResp(args)));
            }
            return new RespValue.SimpleString("OK");
        });

        // DBSIZE
        register("DBSIZE", (store, args, expiry, aofWriter) -> new RespValue.IntegerResp(store.size()));

        // KEYS pattern (supporting KEYS * only for now)
        register("KEYS", (store, args, expiry, aofWriter) -> {
            if (args.size() != 2) {
                return new RespValue.ErrorResp("ERR wrong number of arguments for 'keys' command");
            }
            String pattern = getArgString(args, 1);
            List<String> allKeys = store.keys();
            List<RespValue> matched = new ArrayList<>();
            for (String k : allKeys) {
                if (pattern.equals("*") || k.equals(pattern)) {
                    matched.add(new RespValue.BulkString(k.getBytes(StandardCharsets.UTF_8)));
                }
            }
            return new RespValue.ArrayResp(matched);
        });
    }
}
