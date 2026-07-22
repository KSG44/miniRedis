package com.example.miniredis.network.command.impl;

import com.example.miniredis.network.command.Command;

public class PingCommand implements Command {

    @Override
    public String execute(String[] args) {
        return "+PONG\r\n";
    }
}
