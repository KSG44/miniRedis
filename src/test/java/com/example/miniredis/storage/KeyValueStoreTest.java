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

    @Test
    @DisplayName("EXISTS는 만료된 Key를 존재하는 것으로 판단하지 않는다")
    void existsExcludesExpiredKey() {
        store.setEx("key", "value", 100);
        assertThat(store.exists("key")).isTrue();

        now.addAndGet(100);

        assertThat(store.exists("key")).isFalse();
    }

    @Test
    @DisplayName("EXPIRE는 기존 Key에 TTL을 설정하고 TTL은 남은 초를 반환한다")
    void expireAndTtl() {
        store.set("key", "value");

        assertThat(store.expire("key", 2_500)).isTrue();
        assertThat(store.ttlSeconds("key")).isEqualTo(2);

        now.addAndGet(1_500);
        assertThat(store.ttlSeconds("key")).isEqualTo(1);

        now.addAndGet(1_000);
        assertThat(store.ttlSeconds("key")).isEqualTo(-2);
    }

    @Test
    @DisplayName("EXPIRE는 없는 Key에 false를 반환하고 0 이하 TTL은 기존 Key를 삭제한다")
    void expireMissingAndNonPositiveTtl() {
        assertThat(store.expire("missing", 1_000)).isFalse();

        store.set("key", "value");
        assertThat(store.expire("key", 0)).isTrue();
        assertThat(store.get("key")).isNull();
    }

    @Test
    @DisplayName("TTL은 영구 Key에 -1, 없는 Key에 -2를 반환한다")
    void ttlSentinelValues() {
        store.set("persistent", "value");

        assertThat(store.ttlSeconds("persistent")).isEqualTo(-1);
        assertThat(store.ttlSeconds("missing")).isEqualTo(-2);
    }

    @Test
    @DisplayName("INCR은 없는 Key를 1로 만들고 기존 정수를 증가시킨다")
    void incrementMissingAndExistingValue() {
        assertThat(store.increment("counter")).isEqualTo(1);
        assertThat(store.increment("counter")).isEqualTo(2);
        assertThat(store.get("counter")).isEqualTo("2");

        store.set("negative", "-2");
        assertThat(store.increment("negative")).isEqualTo(-1);
    }

    @Test
    @DisplayName("INCR은 기존 TTL을 유지한다")
    void incrementPreservesTtl() {
        store.setEx("counter", "1", 2_000);

        assertThat(store.increment("counter")).isEqualTo(2);
        assertThat(store.ttlSeconds("counter")).isEqualTo(2);

        now.addAndGet(2_000);
        assertThat(store.get("counter")).isNull();
    }

    @Test
    @DisplayName("INCR은 정수가 아니거나 overflow가 발생하면 기존 값을 변경하지 않는다")
    void incrementRejectsInvalidValueAndOverflow() {
        store.set("text", "hello");
        store.set("max", Long.toString(Long.MAX_VALUE));

        assertThatThrownBy(() -> store.increment("text"))
                .isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> store.increment("max"))
                .isInstanceOf(ArithmeticException.class);
        assertThat(store.get("text")).isEqualTo("hello");
        assertThat(store.get("max")).isEqualTo(Long.toString(Long.MAX_VALUE));
    }
}
