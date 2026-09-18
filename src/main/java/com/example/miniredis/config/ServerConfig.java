package com.example.miniredis.config;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public record ServerConfig(int port, Path aofPath) {

    public static final int DEFAULT_PORT = 6379;
    public static final Path DEFAULT_AOF_PATH = Path.of("appendonly.aof");

    public ServerConfig {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port는 1에서 65535 사이여야 합니다");
        }
        if (aofPath == null || aofPath.toString().isBlank()) {
            throw new IllegalArgumentException("aof 경로는 비어 있을 수 없습니다");
        }
    }

    public static ServerConfig parse(String[] args) {
        int port = DEFAULT_PORT;
        Path aofPath = DEFAULT_AOF_PATH;
        Set<String> specifiedOptions = new HashSet<>();

        for (int index = 0; index < args.length; index++) {
            String option = args[index];
            if (!"--port".equals(option) && !"--aof".equals(option)) {
                throw new IllegalArgumentException("알 수 없는 옵션입니다: " + option);
            }
            if (!specifiedOptions.add(option)) {
                throw new IllegalArgumentException("옵션을 중복해서 지정할 수 없습니다: " + option);
            }
            if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                throw new IllegalArgumentException("옵션 값이 필요합니다: " + option);
            }

            String value = args[++index];
            if ("--port".equals(option)) {
                port = parsePort(value);
            } else {
                aofPath = parseAofPath(value);
            }
        }

        return new ServerConfig(port, aofPath);
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("port는 정수여야 합니다: " + value, e);
        }
    }

    private static Path parseAofPath(String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("aof 경로는 비어 있을 수 없습니다");
        }
        try {
            return Path.of(value);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("올바르지 않은 aof 경로입니다: " + value, e);
        }
    }

    public static String usage() {
        return "Usage: java ... com.example.miniredis.Main [--port <1-65535>] [--aof <file-path>]";
    }
}
