package com.example.miniredis.network.command.impl;

import com.example.miniredis.network.command.Command;

public class PingCommand implements Command {

    @Override
    public String execute(String[] args) {
        if (args.length != 1) {
            return "-ERR wrong number of arguments for 'ping'\r\n";
        }
        return "+PONG\r\n";
    }
}
