package com.example.miniredis.network.command.impl;

import com.example.miniredis.network.command.Command;
import com.example.miniredis.storage.KeyValueStore;

public class SetExCommand implements Command {

    private final KeyValueStore store;

    public SetExCommand(KeyValueStore store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {

        if (args.length < 4)
            return "-ERR wrong number of arguments for 'setex'\r\n";

        try {

            long ttl = Long.parseLong(args[3]);

            store.setEx(args[1], args[2], ttl);

            return "+OK\r\n";

        } catch (NumberFormatException e) {

            return "-ERR value is not an integer or out of range\r\n";

        }
    }
}


