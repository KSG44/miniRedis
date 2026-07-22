package com.example.miniredis.network.command.impl;

import com.example.miniredis.network.command.Command;
import com.example.miniredis.storage.KeyValueStore;

public class SetCommand implements Command {
    private final KeyValueStore store;

    public SetCommand(KeyValueStore store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length < 3) return "-ERR wrong number of arguments for 'set'\r\n";
        store.set(args[1], args[2]);
        return "+OK\r\n";
    }
}
