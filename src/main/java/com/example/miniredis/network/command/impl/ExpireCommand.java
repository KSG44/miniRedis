package com.example.miniredis.network.command.impl;

import com.example.miniredis.network.command.Command;
import com.example.miniredis.storage.KeyValueStore;

public class ExpireCommand implements Command {

    private final KeyValueStore store;

    public ExpireCommand(KeyValueStore store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length != 3) {
            return "-ERR wrong number of arguments for 'expire'\r\n";
        }

        try {
            long seconds = Long.parseLong(args[2]);
            long ttlMillis = seconds > 0 ? Math.multiplyExact(seconds, 1_000L) : seconds;
            return ":" + (store.expire(args[1], ttlMillis) ? 1 : 0) + "\r\n";
        } catch (IllegalArgumentException | ArithmeticException e) {
            return "-ERR value is not an integer or out of range\r\n";
        }
    }
}
