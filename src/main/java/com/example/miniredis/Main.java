package com.example.miniredis;

import com.example.miniredis.network.RedisServer;
import com.example.miniredis.persistence.AofManager;
import com.example.miniredis.server.ServerStats;
import com.example.miniredis.storage.KeyValueStore;
import com.example.miniredis.storage.scheduler.ActiveExpirationScheduler;

public class Main {

    private static final String AOF_FILE_PATH = "appendonly.aof";
    private static final int PORT = 6379;

    public static void main(String[] args) {
        // 1. AOF 및 Store 초기화
        AofManager aofManager = new AofManager(AOF_FILE_PATH);
        KeyValueStore store = new KeyValueStore(aofManager);

        // 2. 기존 AOF 로드
        aofManager.load(store);

        ServerStats stats = new ServerStats();

        ActiveExpirationScheduler scheduler = new ActiveExpirationScheduler(store);
        scheduler.start();

        RedisServer server = new RedisServer( PORT, store, aofManager, stats);
        server.start();


    }
}
