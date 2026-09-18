package com.example.miniredis;

import com.example.miniredis.config.ServerConfig;
import com.example.miniredis.network.RedisServer;
import com.example.miniredis.persistence.AofManager;
import com.example.miniredis.server.ServerStats;
import com.example.miniredis.storage.KeyValueStore;
import com.example.miniredis.storage.scheduler.ActiveExpirationScheduler;

import java.util.concurrent.atomic.AtomicBoolean;

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

        try (AofManager aofManager = new AofManager(config.aofPath().toString())) {
            KeyValueStore store = new KeyValueStore(aofManager);
            aofManager.load(store);

            ActiveExpirationScheduler scheduler = new ActiveExpirationScheduler(store);
            RedisServer server = new RedisServer(config.port(), store, aofManager, new ServerStats());
            AtomicBoolean stopped = new AtomicBoolean();
            Runnable stopResources = () -> {
                if (stopped.compareAndSet(false, true)) {
                    server.stop();
                    scheduler.stop();
                    aofManager.close();
                }
            };
            Thread shutdownHook = new Thread(stopResources, "mini-redis-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            try {
                scheduler.start();
                server.start();
            } finally {
                stopResources.run();
                removeShutdownHook(shutdownHook);
            }
        }
    }

    private static void removeShutdownHook(Thread shutdownHook) {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM 종료가 이미 시작되어 shutdown hook이 실행 중인 경우
        }
    }
}
