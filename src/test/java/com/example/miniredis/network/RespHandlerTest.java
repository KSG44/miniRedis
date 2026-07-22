package com.example.miniredis.network;

import com.example.miniredis.server.ServerStats;
import com.example.miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.IOException;

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

    private BufferedReader createReader(String input) {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(input.getBytes())));
    }

    @Test
    @DisplayName("PING 명령어에 대해 +PONG\\r\\n 응답")
    void pingCommand() throws IOException {
        BufferedReader reader = createReader("PING\r\n");
        String response = respHandler.processCommand(reader);

        assertThat(response).isEqualTo("+PONG\r\n");
    }

    @Test
    @DisplayName("RESP Array 형식 (*3\\r\\n...)으로 SET 및 GET 처리 검증")
    void respArraySetAndGet() throws IOException {
        // SET key1 value1 (RESP Array)
        String setInput = "*3\r\n$3\r\nSET\r\n$4\r\nkey1\r\n$6\r\nvalue1\r\n";
        String setResponse = respHandler.processCommand(createReader(setInput));
        assertThat(setResponse).isEqualTo("+OK\r\n");

        // GET key1 (RESP Array)
        String getInput = "*2\r\n$3\r\nGET\r\n$4\r\nkey1\r\n";
        String getResponse = respHandler.processCommand(createReader(getInput));
        assertThat(getResponse).isEqualTo("$6\r\nvalue1\r\n");
    }

    @Test
    @DisplayName("존재하지 않는 키 GET 시 Null Bulk String ($-1\\r\\n) 반환")
    void getNullKey() throws IOException {
        String getInput = "*2\r\n$3\r\nGET\r\n$7\r\nunknown\r\n";
        String getResponse = respHandler.processCommand(createReader(getInput));

        assertThat(getResponse).isEqualTo("$-1\r\n");
    }
}
