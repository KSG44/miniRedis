package com.example.miniredis.network.command;

import com.example.miniredis.network.command.impl.*;
import com.example.miniredis.server.ServerStats;
import com.example.miniredis.storage.KeyValueStore;

import java.util.HashMap;
import java.util.Map;

public class CommandDispatcher {

    private final Map<String, Command> commandMap = new HashMap<>();
    private final ServerStats stats;

    public CommandDispatcher(KeyValueStore store, ServerStats stats) {
        this.stats = stats;
        registerCommand(store);
    }

    private void registerCommand(KeyValueStore store) {
        commandMap.put("PING", new PingCommand());
        commandMap.put("SET", new SetCommand(store));
        commandMap.put("SETEX", new SetExCommand(store));
        commandMap.put("GET", new GetCommand(store));
        commandMap.put("DEL", new DelCommand(store));
        commandMap.put("EXISTS", new ExistsCommand(store));
        commandMap.put("EXPIRE", new ExpireCommand(store));
        commandMap.put("TTL", new TtlCommand(store));
        commandMap.put("INCR", new IncrCommand(store));
        commandMap.put("REPLCONF", new ReplConfCommand());
        commandMap.put("INFO", new InfoCommand(store, stats));
    }

    public String execute(String[] args) {

        if (args.length == 0) {
            return "-ERR empty command\r\n";
        }

        String commandName = args[0].toUpperCase();

        Command command = commandMap.get(commandName);

        if (command == null) {
            return "-ERR unknown command '" + args[0] + "'\r\n";
        }

        return command.execute(args);
    }

}
