package com.velocache.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public class ReplicationLeader {
    private static final Logger logger = LoggerFactory.getLogger(ReplicationLeader.class);

    private final List<SocketChannel> followers = new ArrayList<>();

    public synchronized void registerFollower(String host, int port) {
        try {
            SocketChannel channel = SocketChannel.open();
            channel.connect(new InetSocketAddress(host, port));
            channel.configureBlocking(false);
            followers.add(channel);
            logger.info("Registered replication follower: {}:{}", host, port);
        } catch (IOException e) {
            logger.error("Failed to connect to replication follower at {}:{}", host, port, e);
        }
    }

    public synchronized void replicate(byte[] encodedCommand) {
        List<SocketChannel> toRemove = new ArrayList<>();
        for (SocketChannel follower : followers) {
            try {
                ByteBuffer buf = ByteBuffer.wrap(encodedCommand);
                while (buf.hasRemaining()) {
                    follower.write(buf);
                }
            } catch (IOException e) {
                logger.error("Failed to replicate command to follower, removing it", e);
                toRemove.add(follower);
            }
        }
        followers.removeAll(toRemove);
    }

    public synchronized void close() {
        for (SocketChannel follower : followers) {
            try {
                follower.close();
            } catch (IOException ignored) {}
        }
        followers.clear();
    }
}
