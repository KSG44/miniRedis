package com.example.miniredis.cli;

import com.example.miniredis.persistence.AofManager;
import com.example.miniredis.storage.KeyValueStore;

import java.util.Scanner;

public class ConsoleRunner {

    private final KeyValueStore store;
    private final AofManager aofManager;

    public ConsoleRunner(KeyValueStore store, AofManager aofManager) {
        this.store = store;
        this.aofManager = aofManager;
    }

    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== Mini Redis CLI Started ===");
        System.out.println("Type 'EXIT' or 'QUIT' to stop.\n");

        while (true) {
            System.out.print("127.0.0.1:6379> ");
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            }

            if ("EXIT".equalsIgnoreCase(line) || "QUIT".equalsIgnoreCase(line)) {
                System.out.println("Bye!");
                break;
            }

            processCommand(line);
        }

        if (aofManager != null) {
            aofManager.close();
        }
    }

    private void processCommand(String input) {
        String[] parts = input.split("\\s+");
        String command = parts[0].toUpperCase();

        switch (command) {
            case "SET":
                if (parts.length < 3) {
                    System.out.println("(error) ERR wrong number of arguments for 'set' command");
                    return;
                }
                store.set(parts[1], parts[2]);
                System.out.println("OK");
                break;

            case "SETEX":
                if (parts.length < 4) {
                    System.out.println("(error) ERR wrong number of arguments for 'setex' command");
                    return;
                }
                try {
                    long ttlMillis = Long.parseLong(parts[3]);
                    store.setEx(parts[1], parts[2], ttlMillis);
                    System.out.println("OK");
                } catch (NumberFormatException e) {
                    System.out.println("(error) ERR value is not an integer or out of range");
                }
                break;

            case "GET":
                if (parts.length < 2) {
                    System.out.println("(error) ERR wrong number of arguments for 'get' command");
                    return;
                }
                String value = store.get(parts[1]);
                if (value == null) {
                    System.out.println("(nil)");
                } else {
                    System.out.println("\"" + value + "\"");
                }
                break;

            case "DEL":
                if (parts.length < 2) {
                    System.out.println("(error) ERR wrong number of arguments for 'del' command");
                    return;
                }
                boolean deleted = store.delete(parts[1]);
                System.out.println("(integer) " + (deleted ? 1 : 0));
                break;

            default:
                System.out.println("(error) ERR unknown command '" + parts[0] + "'");
                break;
        }
    }
}
