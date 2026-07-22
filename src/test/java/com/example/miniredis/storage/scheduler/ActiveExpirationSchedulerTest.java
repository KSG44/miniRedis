package com.example.miniredis.storage.scheduler;

import com.example.miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveExpirationSchedulerTest {

    @Test
    @DisplayName("만료된 Key를 Scheduler가 자동 삭제한다.")
    void removeExpiredKey() throws Exception {

        KeyValueStore store = new KeyValueStore();

        // TTL 100ms
        store.setEx("name", "gon", 100);

        ActiveExpirationScheduler scheduler =
                new ActiveExpirationScheduler(store, 50);

        scheduler.start();

        Thread.sleep(250);

        assertThat(store.get("name")).isNull();

        scheduler.stop();
    }

    @Test
    @DisplayName("만료되지 않은 Key는 삭제되지 않는다.")
    void keepAliveKey() throws Exception {

        KeyValueStore store = new KeyValueStore();

        // TTL 1000ms
        store.setEx("name", "gon", 1000);

        ActiveExpirationScheduler scheduler =
                new ActiveExpirationScheduler(store, 50);

        scheduler.start();

        Thread.sleep(200);

        assertThat(store.get("name")).isEqualTo("gon");

        scheduler.stop();
    }
}
