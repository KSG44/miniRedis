package com.example.miniredis.network.command.impl;

import com.example.miniredis.network.command.Command;
import com.example.miniredis.storage.KeyValueStore;

public class IncrCommand implements Command {

    private final KeyValueStore store;

    public IncrCommand(KeyValueStore store) {
        this.store = store;
    }

    @Override
    public String execute(String[] args) {
        if (args.length != 2) {
            return "-ERR wrong number of arguments for 'incr'\r\n";
        }

        try {
            return ":" + store.increment(args[1]) + "\r\n";
        } catch (NumberFormatException | ArithmeticException e) {
            return "-ERR value is not an integer or out of range\r\n";
        }
    }
}
