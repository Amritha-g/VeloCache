package com.velocache.store;

import java.util.List;

public interface CacheStore {
    Object get(String key);
    Object put(String key, Object value);
    boolean remove(String key);
    boolean exists(String key);
    long incr(String key);
    long incrBy(String key, long amount);
    void append(String key, String value);
    int strlen(String key);
    void clear();
    int size();
    List<String> keys();
}
