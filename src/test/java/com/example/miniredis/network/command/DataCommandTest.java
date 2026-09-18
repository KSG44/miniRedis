package com.example.miniredis.network.command;

import com.example.miniredis.server.ServerStats;
import com.example.miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class DataCommandTest {

    private AtomicLong now;
    private KeyValueStore store;
    private CommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        now = new AtomicLong(1_000);
        store = new KeyValueStore(null, now::get);
        dispatcher = new CommandDispatcher(store, new ServerStats());
    }

    @Test
    @DisplayName("EXISTS는 존재하는 인자의 수를 반환하고 중복 Key도 각각 센다")
    void existsCountsKeys() {
        store.set("first", "value");
        store.set("second", "value");

        assertThat(dispatcher.execute(new String[]{"EXISTS", "first", "missing", "second", "first"}))
                .isEqualTo(":3\r\n");
        assertThat(dispatcher.execute(new String[]{"EXISTS"}))
                .isEqualTo("-ERR wrong number of arguments for 'exists'\r\n");
    }

    @Test
    @DisplayName("EXISTS는 만료된 Key를 제외한다")
    void existsExcludesExpiredKey() {
        store.setEx("expired", "value", 100);
        now.addAndGet(100);

        assertThat(dispatcher.execute(new String[]{"EXISTS", "expired"})).isEqualTo(":0\r\n");
    }

    @Test
    @DisplayName("EXPIRE는 Key에 TTL을 설정하고 없는 Key에는 0을 반환한다")
    void expireKey() {
        store.set("key", "value");

        assertThat(dispatcher.execute(new String[]{"EXPIRE", "key", "10"})).isEqualTo(":1\r\n");
        assertThat(dispatcher.execute(new String[]{"TTL", "key"})).isEqualTo(":10\r\n");
        assertThat(dispatcher.execute(new String[]{"EXPIRE", "missing", "10"})).isEqualTo(":0\r\n");
    }

    @Test
    @DisplayName("EXPIRE의 0 이하 TTL은 기존 Key를 즉시 삭제한다")
    void expireWithNonPositiveTtlDeletesKey() {
        store.set("zero", "value");
        store.set("negative", "value");

        assertThat(dispatcher.execute(new String[]{"EXPIRE", "zero", "0"})).isEqualTo(":1\r\n");
        assertThat(dispatcher.execute(new String[]{"EXPIRE", "negative", "-1"})).isEqualTo(":1\r\n");
        assertThat(store.get("zero")).isNull();
        assertThat(store.get("negative")).isNull();
    }

    @Test
    @DisplayName("EXPIRE는 잘못된 숫자와 범위를 거부한다")
    void expireRejectsInvalidSeconds() {
        assertThat(dispatcher.execute(new String[]{"EXPIRE", "key", "abc"}))
                .isEqualTo("-ERR value is not an integer or out of range\r\n");
        assertThat(dispatcher.execute(new String[]{"EXPIRE", "key", Long.toString(Long.MAX_VALUE)}))
                .isEqualTo("-ERR value is not an integer or out of range\r\n");
        assertThat(dispatcher.execute(new String[]{"EXPIRE", "key"}))
                .isEqualTo("-ERR wrong number of arguments for 'expire'\r\n");
    }

    @Test
    @DisplayName("TTL은 영구 Key와 없는 Key를 구분하고 인자 개수를 검증한다")
    void ttlSentinelValuesAndArguments() {
        store.set("persistent", "value");

        assertThat(dispatcher.execute(new String[]{"TTL", "persistent"})).isEqualTo(":-1\r\n");
        assertThat(dispatcher.execute(new String[]{"TTL", "missing"})).isEqualTo(":-2\r\n");
        assertThat(dispatcher.execute(new String[]{"TTL"}))
                .isEqualTo("-ERR wrong number of arguments for 'ttl'\r\n");
    }

    @Test
    @DisplayName("INCR은 없는 Key와 기존 정수를 증가시킨다")
    void incrementValues() {
        assertThat(dispatcher.execute(new String[]{"INCR", "counter"})).isEqualTo(":1\r\n");
        assertThat(dispatcher.execute(new String[]{"INCR", "counter"})).isEqualTo(":2\r\n");
    }

    @Test
    @DisplayName("INCR은 잘못된 값과 overflow를 거부하며 인자 개수를 검증한다")
    void incrementRejectsInvalidValues() {
        store.set("text", "hello");
        store.set("max", Long.toString(Long.MAX_VALUE));

        assertThat(dispatcher.execute(new String[]{"INCR", "text"}))
                .isEqualTo("-ERR value is not an integer or out of range\r\n");
        assertThat(dispatcher.execute(new String[]{"INCR", "max"}))
                .isEqualTo("-ERR value is not an integer or out of range\r\n");
        assertThat(dispatcher.execute(new String[]{"INCR"}))
                .isEqualTo("-ERR wrong number of arguments for 'incr'\r\n");
    }

    @Test
    @DisplayName("DEL은 여러 Key를 삭제하고 실제 삭제된 수를 반환한다")
    void deleteMultipleKeys() {
        store.set("first", "value");
        store.set("second", "value");

        assertThat(dispatcher.execute(new String[]{"DEL", "first", "missing", "second", "first"}))
                .isEqualTo(":2\r\n");
        assertThat(store.size()).isZero();
    }
}
