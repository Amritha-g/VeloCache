package com.velocache.commands;

import com.velocache.protocol.RespValue;
import com.velocache.store.CacheStore;
import com.velocache.expiry.ExpiryManager;
import com.velocache.persistence.AOFWriter;
import java.util.List;

@FunctionalInterface
public interface CommandHandler {
    RespValue handle(CacheStore store, List<RespValue> args, ExpiryManager expiry, AOFWriter aofWriter);
}
