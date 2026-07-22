package com.example.miniredis.network.command.impl;

import com.example.miniredis.network.command.Command;

public class ReplConfCommand implements Command {

    @Override
    public String execute(String[] args) {

        if (args.length >= 2 &&
                "SYNC".equalsIgnoreCase(args[1])) {

            return "+OK SLAVE SYNC STARTED\r\n";
        }

        return "+OK\r\n";
    }
}

