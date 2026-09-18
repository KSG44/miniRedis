package com.example.miniredis.network.command;

import com.example.miniredis.server.ServerStats;
import com.example.miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandValidationTest {

    private KeyValueStore store;
    private CommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        store = new KeyValueStore();
        dispatcher = new CommandDispatcher(store, new ServerStats());
    }

    @Test
    @DisplayName("명령어 이름은 대소문자를 구분하지 않는다")
    void commandNameIsCaseInsensitive() {
        assertThat(dispatcher.execute(new String[]{"set", "name", "gon"})).isEqualTo("+OK\r\n");
        assertThat(dispatcher.execute(new String[]{"get", "name"})).isEqualTo("$3\r\ngon\r\n");
    }

    @Test
    @DisplayName("빈 명령과 알 수 없는 명령은 오류를 반환한다")
    void emptyAndUnknownCommands() {
        assertThat(dispatcher.execute(new String[]{})).isEqualTo("-ERR empty command\r\n");
        assertThat(dispatcher.execute(new String[]{"NOPE"}))
                .isEqualTo("-ERR unknown command 'NOPE'\r\n");
    }

    @Test
    @DisplayName("PING은 불필요한 인자를 허용하지 않는다")
    void pingArgumentValidation() {
        assertThat(dispatcher.execute(new String[]{"PING"})).isEqualTo("+PONG\r\n");
        assertThat(dispatcher.execute(new String[]{"PING", "extra"}))
                .isEqualTo("-ERR wrong number of arguments for 'ping'\r\n");
    }

    @Test
    @DisplayName("SET은 키와 값이 정확히 하나씩 필요하다")
    void setArgumentValidation() {
        assertThat(dispatcher.execute(new String[]{"SET", "key"}))
                .isEqualTo("-ERR wrong number of arguments for 'set'\r\n");
        assertThat(dispatcher.execute(new String[]{"SET", "key", "value", "extra"}))
                .isEqualTo("-ERR wrong number of arguments for 'set'\r\n");
    }

    @Test
    @DisplayName("GET은 키가 정확히 하나 필요하다")
    void getArgumentValidation() {
        assertThat(dispatcher.execute(new String[]{"GET"}))
                .isEqualTo("-ERR wrong number of arguments for 'get'\r\n");
        assertThat(dispatcher.execute(new String[]{"GET", "key", "extra"}))
                .isEqualTo("-ERR wrong number of arguments for 'get'\r\n");
    }

    @Test
    @DisplayName("DEL은 삭제 여부를 정수 응답으로 반환한다")
    void deleteResultAndArgumentValidation() {
        store.set("key", "value");

        assertThat(dispatcher.execute(new String[]{"DEL", "key"})).isEqualTo(":1\r\n");
        assertThat(dispatcher.execute(new String[]{"DEL", "key"})).isEqualTo(":0\r\n");
        assertThat(dispatcher.execute(new String[]{"DEL"}))
                .isEqualTo("-ERR wrong number of arguments for 'del'\r\n");
    }

    @Test
    @DisplayName("SETEX는 key seconds value 순서로 값을 저장한다")
    void setExUsesRedisArgumentOrder() {
        assertThat(dispatcher.execute(new String[]{"SETEX", "session", "10", "active"}))
                .isEqualTo("+OK\r\n");
        assertThat(store.get("session")).isEqualTo("active");
    }

    @Test
    @DisplayName("SETEX는 숫자가 아니거나 0 이하인 TTL을 거부한다")
    void setExRejectsInvalidTtl() {
        assertThat(dispatcher.execute(new String[]{"SETEX", "key", "abc", "value"}))
                .isEqualTo("-ERR value is not an integer or out of range\r\n");
        assertThat(dispatcher.execute(new String[]{"SETEX", "key", "0", "value"}))
                .isEqualTo("-ERR invalid expire time in 'setex' command\r\n");
        assertThat(dispatcher.execute(new String[]{"SETEX", "key", "-1", "value"}))
                .isEqualTo("-ERR invalid expire time in 'setex' command\r\n");
        assertThat(dispatcher.execute(new String[]{"SETEX", "key", Long.toString(Long.MAX_VALUE), "value"}))
                .isEqualTo("-ERR value is not an integer or out of range\r\n");
    }

    @Test
    @DisplayName("SETEX와 INFO는 잘못된 인자 개수를 거부한다")
    void setExAndInfoArgumentValidation() {
        assertThat(dispatcher.execute(new String[]{"SETEX", "key", "10"}))
                .isEqualTo("-ERR wrong number of arguments for 'setex'\r\n");
        assertThat(dispatcher.execute(new String[]{"INFO", "server"}))
                .isEqualTo("-ERR wrong number of arguments for 'info'\r\n");
    }

    @Test
    @DisplayName("REPLCONF는 현재 지원하는 SYNC 옵션만 허용한다")
    void replConfValidation() {
        assertThat(dispatcher.execute(new String[]{"REPLCONF", "SYNC"}))
                .isEqualTo("+OK SLAVE SYNC STARTED\r\n");
        assertThat(dispatcher.execute(new String[]{"REPLCONF", "unknown"}))
                .isEqualTo("-ERR unsupported REPLCONF option\r\n");
        assertThat(dispatcher.execute(new String[]{"REPLCONF"}))
                .isEqualTo("-ERR wrong number of arguments for 'replconf'\r\n");
    }
}
