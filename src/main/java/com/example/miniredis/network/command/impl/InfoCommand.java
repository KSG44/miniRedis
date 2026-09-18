package com.example.miniredis.network.command.impl;

import com.example.miniredis.network.command.Command;
import com.example.miniredis.server.ServerStats;
import com.example.miniredis.storage.KeyValueStore;

import java.nio.charset.StandardCharsets;

public class InfoCommand implements Command {

    private final KeyValueStore store;
    private final ServerStats stats;


    public InfoCommand(KeyValueStore store, ServerStats stats) {
        this.store = store;
        this.stats = stats;
    }

    @Override
    public String execute(String[] args) {

        if (args.length != 1) {
            return "-ERR wrong number of arguments for 'info'\r\n";
        }

        StringBuilder info = new StringBuilder();

        info.append("# Server\r\n");
        info.append("miniRedis_version:")
                .append(stats.getVersion())
                .append("\r\n");

        info.append("\r\n");

        info.append("# Stats\r\n");
        info.append("uptime_in_seconds:")
                .append(stats.getUptimeSeconds())
                .append("\r\n");

        info.append("keys:")
                .append(store.size())
                .append("\r\n");

        byte[] bytes = info.toString().getBytes(StandardCharsets.UTF_8);

        return "$" + bytes.length + "\r\n"
                + info
                + "\r\n";
    }
}

