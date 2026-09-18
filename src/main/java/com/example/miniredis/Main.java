package com.example.miniredis;

import com.example.miniredis.config.ServerConfig;
import com.example.miniredis.network.RedisServer;
import com.example.miniredis.persistence.AofManager;
import com.example.miniredis.server.ServerStats;
import com.example.miniredis.storage.KeyValueStore;
import com.example.miniredis.storage.scheduler.ActiveExpirationScheduler;

public class Main {

    public static void main(String[] args) {
        ServerConfig config;
        try {
            config = ServerConfig.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("설정 오류: " + e.getMessage());
            System.err.println(ServerConfig.usage());
            return;
        }

        // 1. AOF 및 Store 초기화
        AofManager aofManager = new AofManager(config.aofPath().toString());
        KeyValueStore store = new KeyValueStore(aofManager);

        // 2. 기존 AOF 로드
        aofManager.load(store);

        ServerStats stats = new ServerStats();

        ActiveExpirationScheduler scheduler = new ActiveExpirationScheduler(store);
        scheduler.start();

        RedisServer server = new RedisServer(config.port(), store, aofManager, stats);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            scheduler.stop();
            aofManager.close();
        }));

        server.start();

    }
}
