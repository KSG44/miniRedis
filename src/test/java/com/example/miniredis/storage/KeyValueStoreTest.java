package com.example.miniredis.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyValueStoreTest {

    private KeyValueStore store;
    private AtomicLong now;

    @BeforeEach
    void setUp() {
        now = new AtomicLong(1_000);
        store = new KeyValueStore(null, now::get);
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
    @DisplayName("존재하지 않는 Key 삭제 시 false를 반환한다")
    void deleteMissingKey() {
        assertThat(store.delete("unknown")).isFalse();
    }

    @Test
    @DisplayName("TTL 만료 후 GET 시 null 반환 및 삭제 (Lazy Expiration)")
    void ttlExpiration() {
        // 100ms 후 만료되도록 설정
        store.setEx("tempKey", "tempValue", 100);

        // 만료 전 조회
        assertThat(store.get("tempKey")).isEqualTo("tempValue");

        now.addAndGet(150);

        // 만료 후 조회 시 null 반환 및 내부 삭제
        assertThat(store.get("tempKey")).isNull();
    }

    @Test
    @DisplayName("일반 SET으로 덮어쓰면 기존 TTL이 제거된다")
    void setClearsExistingTtl() {
        store.setEx("key", "temporary", 50);
        store.set("key", "permanent");

        now.addAndGet(80);

        assertThat(store.get("key")).isEqualTo("permanent");
    }

    @Test
    @DisplayName("0 이하의 TTL은 저장소에서도 거부한다")
    void rejectNonPositiveTtl() {
        assertThatThrownBy(() -> store.setEx("key", "value", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.setEx("key", "value", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("size와 keys는 만료되었지만 아직 조회되지 않은 Key를 제외한다")
    void sizeAndKeysExcludeExpiredEntries() {
        store.set("alive", "value");
        store.setEx("expired", "value", 30);

        now.addAndGet(50);

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.keys()).containsExactly("alive");
    }
}
