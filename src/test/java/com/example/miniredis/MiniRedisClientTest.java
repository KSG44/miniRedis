package com.example.miniredis;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MiniRedisClientTest {

    @Test
    void readsUtf8BulkStringByByteLength() throws IOException {
        byte[] response = "$6\r\n한글\r\n".getBytes(StandardCharsets.UTF_8);
        assertThat(MiniRedisClient.readResponse(new ByteArrayInputStream(response))).isEqualTo("한글");
    }

    @Test
    void readsNilSimpleStringIntegerAndError() throws IOException {
        assertThat(read("$-1\r\n")).isEqualTo("(nil)");
        assertThat(read("+OK\r\n")).isEqualTo("OK");
        assertThat(read(":2\r\n")).isEqualTo("2");
        assertThat(read("-ERR failure\r\n")).isEqualTo("-ERR failure");
    }

    @Test
    void rejectsTruncatedBulkString() {
        assertThatThrownBy(() -> read("$5\r\nabc"))
                .isInstanceOf(IOException.class)
                .hasMessage("Bulk String 응답이 중간에 종료되었습니다");
    }

    @Test
    void writesUtf8CommandWithCrlfOnEveryPlatform() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MiniRedisClient.writeCommand(output, "SET 이름 값");
        assertThat(output.toByteArray()).isEqualTo("SET 이름 값\r\n".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesDefaultAndCustomPort() {
        assertThat(MiniRedisClient.parsePort(new String[0])).isEqualTo(6379);
        assertThat(MiniRedisClient.parsePort(new String[]{"--port", "6380"})).isEqualTo(6380);
    }

    @Test
    void rejectsInvalidClientOptions() {
        assertThatThrownBy(() -> MiniRedisClient.parsePort(new String[]{"--host", "localhost"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MiniRedisClient.parsePort(new String[]{"--port"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MiniRedisClient.parsePort(new String[]{"--port", "0"}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MiniRedisClient.parsePort(new String[]{"--port", "abc"}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String read(String response) throws IOException {
        return MiniRedisClient.readResponse(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)));
    }
}
