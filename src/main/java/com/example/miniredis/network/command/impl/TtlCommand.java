package com.example.miniredis.network.command.impl;

import com.example.miniredis.network.command.Command;
import com.example.miniredis.storage.KeyValueStore;

public class TtlCommand implements Command {

    private final KeyValueStore store;

    public TtlCommand(KeyValueStore store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length != 2) {
            return "-ERR wrong number of arguments for 'ttl'\r\n";
        }
        return ":" + store.ttlSeconds(args[1]) + "\r\n";
    }
}
