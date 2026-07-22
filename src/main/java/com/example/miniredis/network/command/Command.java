package com.example.miniredis.network.command;

public interface Command {
    String execute(String[] args);
}
