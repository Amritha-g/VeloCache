package com.velocache.network;

import com.velocache.commands.CommandRegistry;
import com.velocache.expiry.ExpiryManager;
import com.velocache.persistence.AOFWriter;
import com.velocache.protocol.RespEncoder;
import com.velocache.protocol.RespValue;
import com.velocache.store.CacheStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

public class NioServer {
    private static final Logger logger = LoggerFactory.getLogger(NioServer.class);

    private final int port;
    private final CacheStore store;
    private final ExpiryManager expiryManager;
    private final CommandRegistry registry;
    private final AOFWriter aofWriter;

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private volatile boolean running = false;
    private Thread serverThread;

    public NioServer(int port, CacheStore store, ExpiryManager expiryManager, CommandRegistry registry, AOFWriter aofWriter) {
        this.port = port;
        this.store = store;
        this.expiryManager = expiryManager;
        this.registry = registry;
        this.aofWriter = aofWriter;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        running = true;
        serverThread = new Thread(this::runLoop, "velocache-nio-server-" + port);
        serverThread.setDaemon(true);
        serverThread.start();
        logger.info("VeloCache server started on port {}", port);
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (selector != null) {
            selector.wakeup();
        }
        try {
            if (serverChannel != null) {
                serverChannel.close();
            }
            if (selector != null) {
                selector.close();
            }
        } catch (IOException e) {
            logger.error("Error closing server socket or selector", e);
        }
        if (serverThread != null) {
            try {
                serverThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        logger.info("VeloCache server stopped.");
    }

    private void runLoop() {
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        while (running) {
            try {
                int selectCount = selector.select(100);
                if (selectCount == 0) {
                    continue;
                }

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        acceptConnection();
                    } else if (key.isReadable()) {
                        readFromClient(key, readBuffer);
                    }
                }
            } catch (ClosedSelectorException cse) {
                break;
            } catch (IOException e) {
                logger.error("Error in selector loop", e);
            }
        }
    }

    private void acceptConnection() {
        try {
            SocketChannel clientChannel = serverChannel.accept();
            if (clientChannel != null) {
                clientChannel.configureBlocking(false);
                ClientSession session = new ClientSession();
                clientChannel.register(selector, SelectionKey.OP_READ, session);
            }
        } catch (IOException e) {
            logger.error("Failed to accept client connection", e);
        }
    }

    private void readFromClient(SelectionKey key, ByteBuffer readBuffer) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientSession session = (ClientSession) key.attachment();

        readBuffer.clear();
        int bytesRead;
        try {
            bytesRead = clientChannel.read(readBuffer);
        } catch (IOException e) {
            // Force close
            closeKey(key);
            return;
        }

        if (bytesRead == -1) {
            // Client closed connection
            closeKey(key);
            return;
        }

        if (bytesRead > 0) {
            readBuffer.flip();
            session.addBytes(readBuffer);

            // Parse and process all complete messages
            try {
                RespValue msg;
                while ((msg = session.nextMessage()) != null) {
                    processMessage(clientChannel, msg);
                }
            } catch (Exception e) {
                logger.error("Error processing client messages", e);
                // Send error back and close
                try {
                    writeRaw(clientChannel, RespEncoder.encodeError("ERR " + e.getMessage()));
                } catch (IOException ignored) {}
                closeKey(key);
            }
        }
    }

    private void processMessage(SocketChannel clientChannel, RespValue msg) throws IOException {
        if (msg instanceof RespValue.ArrayResp arr) {
            List<RespValue> elements = arr.elements();
            if (elements.isEmpty()) {
                return;
            }
            String commandName = getArgString(elements, 0);
            RespValue response = registry.dispatch(commandName, store, elements, expiryManager, aofWriter);
            writeRaw(clientChannel, RespEncoder.encode(response));
        } else {
            // E.g. inline fallback or other non-array input
            writeRaw(clientChannel, RespEncoder.encodeError("ERR command must be an Array"));
        }
    }

    private void writeRaw(SocketChannel clientChannel, byte[] data) throws IOException {
        ByteBuffer writeBuffer = ByteBuffer.wrap(data);
        while (writeBuffer.hasRemaining()) {
            clientChannel.write(writeBuffer);
        }
    }

    private void closeKey(SelectionKey key) {
        try {
            key.channel().close();
        } catch (IOException ignored) {}
        key.cancel();
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
