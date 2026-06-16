package com.velocache.cluster;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

public class HashRing {
    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final int virtualNodes;

    public HashRing() {
        this(150); // Default virtual nodes
    }

    public HashRing(int virtualNodes) {
        this.virtualNodes = virtualNodes;
    }

    public synchronized void addNode(String nodeId) {
        for (int i = 0; i < virtualNodes; i++) {
            long hash = md5Long(nodeId + "-" + i);
            ring.put(hash, nodeId);
        }
    }

    public synchronized void removeNode(String nodeId) {
        for (int i = 0; i < virtualNodes; i++) {
            long hash = md5Long(nodeId + "-" + i);
            ring.remove(hash);
        }
    }

    public synchronized String getNode(String key) {
        if (ring.isEmpty()) {
            return null;
        }
        long hash = md5Long(key);
        Map.Entry<Long, String> e = ring.ceilingEntry(hash);
        return e != null ? e.getValue() : ring.firstEntry().getValue();
    }

    private long md5Long(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes());
            long val = 0;
            for (int i = 0; i < 8; i++) {
                val = (val << 8) | (bytes[i] & 0xFF);
            }
            return val;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
