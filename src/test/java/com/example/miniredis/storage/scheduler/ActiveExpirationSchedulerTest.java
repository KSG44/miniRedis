package com.example.miniredis.storage.scheduler;

import com.example.miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveExpirationSchedulerTest {

    @Test
    @DisplayName("만료된 Key를 Scheduler가 자동 삭제한다")
    void removeExpiredKey() throws Exception {
        AtomicLong now = new AtomicLong(1_000);
        CountDownLatch expirationChecked = new CountDownLatch(1);
        KeyValueStore store = new KeyValueStore(null, now::get) {
            @Override
            public void removeExpired(String key) {
                super.removeExpired(key);
                expirationChecked.countDown();
            }
        };
        store.setEx("name", "gon", 100);
        ActiveExpirationScheduler scheduler = new ActiveExpirationScheduler(store, 10);

        try {
            scheduler.start();
            now.addAndGet(100);
            assertThat(expirationChecked.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(store.get("name")).isNull();
        } finally {
            scheduler.stop();
        }
    }

    @Test
    @DisplayName("만료되지 않은 Key는 Scheduler가 검사해도 삭제되지 않는다")
    void keepAliveKey() throws Exception {
        CountDownLatch scanned = new CountDownLatch(1);
        KeyValueStore store = new KeyValueStore() {
            @Override
            public Set<String> keys() {
                Set<String> keys = super.keys();
                scanned.countDown();
                return keys;
            }
        };
        store.setEx("name", "gon", 1_000);
        ActiveExpirationScheduler scheduler = new ActiveExpirationScheduler(store, 10);

        try {
            scheduler.start();
            assertThat(scanned.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(store.get("name")).isEqualTo("gon");
        } finally {
            scheduler.stop();
        }
    }

    @Test
    @DisplayName("Scheduler를 두 번 시작해도 중복 작업을 만들지 않는다")
    void startIsIdempotent() throws Exception {
        AtomicLong now = new AtomicLong(1_000);
        CountDownLatch expirationChecked = new CountDownLatch(1);
        KeyValueStore store = new KeyValueStore(null, now::get) {
            @Override
            public void removeExpired(String key) {
                super.removeExpired(key);
                expirationChecked.countDown();
            }
        };
        store.setEx("name", "gon", 50);
        ActiveExpirationScheduler scheduler = new ActiveExpirationScheduler(store, 10);

        try {
            scheduler.start();
            scheduler.start();
            now.addAndGet(50);
            assertThat(expirationChecked.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(store.get("name")).isNull();
        } finally {
            scheduler.stop();
        }
    }
}
