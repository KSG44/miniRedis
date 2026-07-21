package com.example.miniredis.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeyValueStoreTest {

    private KeyValueStore store;

    @BeforeEach
    void setUp() {
        store = new KeyValueStore();
    }

    @Test
    @DisplayName("기본 SET 및 GET 동작 검증")
    void setAndGet() {
        store.set("key1", "value1");
        assertThat(store.get("key1")).isEqualTo("value1");
    }

    @Test
    @DisplayName("존재하지 않는 키 GET 시 null 반환")
    void getNonExistentKey() {
        assertThat(store.get("unknown")).isNull();
    }

    @Test
    @DisplayName("DEL 동작 검증")
    void deleteKey() {
        store.set("key1", "value1");
        boolean result = store.delete("key1");

        assertThat(result).isTrue();
        assertThat(store.get("key1")).isNull();
    }

    @Test
    @DisplayName("TTL 만료 후 GET 시 null 반환 및 삭제 (Lazy Expiration)")
    void ttlExpiration() throws InterruptedException {
        // 100ms 후 만료되도록 설정
        store.setEx("tempKey", "tempValue", 100);

        // 만료 전 조회
        assertThat(store.get("tempKey")).isEqualTo("tempValue");

        // 150ms 대기
        Thread.sleep(150);

        // 만료 후 조회 시 null 반환 및 내부 삭제
        assertThat(store.get("tempKey")).isNull();
    }
}
