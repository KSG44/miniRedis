package com.example.miniredis.storage.scheduler;

import com.example.miniredis.storage.KeyValueStore;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ActiveExpirationScheduler {

    private final ScheduledExecutorService scheduler;
    private final KeyValueStore store;
    private final long intervalMillis;

    // 실제 서버에서 사용하는 생성자
    public ActiveExpirationScheduler(KeyValueStore store) {
        this(store, 1000);
    }

    // 테스트용 생성자
    public ActiveExpirationScheduler(KeyValueStore store, long intervalMillis) {
        this.store = store;
        this.intervalMillis = intervalMillis;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {

        scheduler.scheduleAtFixedRate(() -> {

            for (String key : store.keys()) {
                store.removeExpired(key);
            }

        }, 0, intervalMillis, TimeUnit.MILLISECONDS);

    }

    public void stop() {
        scheduler.shutdown();
    }
}
