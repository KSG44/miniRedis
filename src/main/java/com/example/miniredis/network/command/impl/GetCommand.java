package com.example.miniredis.network.command.impl;

import com.example.miniredis.network.command.Command;
import com.example.miniredis.storage.KeyValueStore;

public class GetCommand implements Command {

    private final KeyValueStore store;

    public GetCommand(KeyValueStore store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {

        if (args.length < 2)
            return "-ERR wrong number of arguments for 'get'\r\n";

        String value = store.get(args[1]);

        if (value == null)
            return "$-1\r\n";

        return "$" + value.getBytes().length + "\r\n" + value + "\r\n";
    }
}
