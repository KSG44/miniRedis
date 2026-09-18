package com.example.miniredis.network;

import com.example.miniredis.server.ServerStats;
import com.example.miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RespHandlerTest {

    private KeyValueStore store;
    private RespHandler respHandler;

    @BeforeEach
    void setUp() {
        store = new KeyValueStore();

        ServerStats stats = new ServerStats();

        respHandler = new RespHandler(store, stats);
    }

    private ByteArrayInputStream createInput(String input) {
        return new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("PING 명령어에 대해 +PONG\\r\\n 응답")
    void pingCommand() throws IOException {
        String response = respHandler.processCommand(createInput("PING\r\n"));

        assertThat(response).isEqualTo("+PONG\r\n");
    }

    @Test
    @DisplayName("RESP Array 형식 (*3\\r\\n...)으로 SET 및 GET 처리 검증")
    void respArraySetAndGet() throws IOException {
        // SET key1 value1 (RESP Array)
        String setInput = "*3\r\n$3\r\nSET\r\n$4\r\nkey1\r\n$6\r\nvalue1\r\n";
        String setResponse = respHandler.processCommand(createInput(setInput));
        assertThat(setResponse).isEqualTo("+OK\r\n");

        // GET key1 (RESP Array)
        String getInput = "*2\r\n$3\r\nGET\r\n$4\r\nkey1\r\n";
        String getResponse = respHandler.processCommand(createInput(getInput));
        assertThat(getResponse).isEqualTo("$6\r\nvalue1\r\n");
    }

    @Test
    @DisplayName("존재하지 않는 키 GET 시 Null Bulk String ($-1\\r\\n) 반환")
    void getNullKey() throws IOException {
        String getInput = "*2\r\n$3\r\nGET\r\n$7\r\nunknown\r\n";
        String getResponse = respHandler.processCommand(createInput(getInput));

        assertThat(getResponse).isEqualTo("$-1\r\n");
    }

    @Test
    @DisplayName("UTF-8 값은 문자 수가 아닌 바이트 길이로 읽고 응답한다")
    void utf8BulkString() throws IOException {
        String value = "안녕";
        String setInput = "*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$6\r\n" + value + "\r\n";

        assertThat(respHandler.processCommand(createInput(setInput))).isEqualTo("+OK\r\n");
        assertThat(respHandler.processCommand(createInput("GET key\r\n")))
                .isEqualTo("$6\r\n안녕\r\n");
    }

    @Test
    @DisplayName("Bulk String의 선언 길이보다 데이터가 짧으면 프로토콜 오류를 반환한다")
    void truncatedBulkString() throws IOException {
        String response = respHandler.processCommand(createInput("*1\r\n$4\r\nGET\r\n"));

        assertThat(response).startsWith("-ERR Protocol error:");
    }

    @Test
    @DisplayName("Bulk String 길이 표기가 아니면 프로토콜 오류를 반환한다")
    void missingBulkLengthPrefix() throws IOException {
        String response = respHandler.processCommand(createInput("*1\r\nGET\r\n"));

        assertThat(response).isEqualTo("-ERR Protocol error: expected bulk string\r\n");
    }

    @Test
    @DisplayName("잘못된 배열 길이는 예외 대신 프로토콜 오류를 반환한다")
    void invalidArrayLength() throws IOException {
        String response = respHandler.processCommand(createInput("*abc\r\n"));

        assertThat(response).isEqualTo("-ERR Protocol error: invalid array length\r\n");
    }

    @Test
    @DisplayName("과도하게 큰 배열과 Bulk String은 메모리를 할당하기 전에 거부한다")
    void rejectOversizedRequests() throws IOException {
        assertThat(respHandler.processCommand(createInput("*1000000\r\n")))
                .isEqualTo("-ERR Protocol error: array length exceeds limit\r\n");
        assertThat(respHandler.processCommand(createInput("*1\r\n$2147483647\r\n")))
                .isEqualTo("-ERR Protocol error: bulk length exceeds limit\r\n");
    }
}
