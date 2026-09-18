package com.example.miniredis.network.command.impl;

import com.example.miniredis.network.command.Command;
import com.example.miniredis.storage.KeyValueStore;

public class DelCommand implements Command {

    private final KeyValueStore store;

    public DelCommand(KeyValueStore store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {

        if (args.length != 2)
            return "-ERR wrong number of arguments for 'del'\r\n";

        boolean deleted = store.delete(args[1]);

        return ":" + (deleted ? 1 : 0) + "\r\n";
    }
}
