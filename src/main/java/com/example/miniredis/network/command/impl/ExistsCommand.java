package com.example.miniredis.network.command.impl;

import com.example.miniredis.network.command.Command;
import com.example.miniredis.storage.KeyValueStore;

public class ExistsCommand implements Command {

    private final KeyValueStore store;

    public ExistsCommand(KeyValueStore store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length < 2) {
            return "-ERR wrong number of arguments for 'exists'\r\n";
        }

        int count = 0;
        for (int index = 1; index < args.length; index++) {
            if (store.exists(args[index])) count++;
        }
        return ":" + count + "\r\n";
    }
}
