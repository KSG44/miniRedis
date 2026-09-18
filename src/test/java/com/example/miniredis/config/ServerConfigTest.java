package com.example.miniredis.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerConfigTest {

    @Test
    @DisplayName("옵션이 없으면 기본 포트와 AOF 경로를 사용한다")
    void defaultConfiguration() {
        ServerConfig config = ServerConfig.parse(new String[]{});

        assertThat(config.port()).isEqualTo(6379);
        assertThat(config.aofPath()).isEqualTo(Path.of("appendonly.aof"));
    }

    @Test
    @DisplayName("port와 aof 옵션을 원하는 순서로 지정할 수 있다")
    void customConfiguration() {
        ServerConfig config = ServerConfig.parse(new String[]{
                "--aof", "data/custom.aof",
                "--port", "6380"
        });

        assertThat(config.port()).isEqualTo(6380);
        assertThat(config.aofPath()).isEqualTo(Path.of("data/custom.aof"));
    }

    @Test
    @DisplayName("port의 최솟값과 최댓값을 허용한다")
    void validPortBoundaries() {
        assertThat(ServerConfig.parse(new String[]{"--port", "1"}).port()).isEqualTo(1);
        assertThat(ServerConfig.parse(new String[]{"--port", "65535"}).port()).isEqualTo(65_535);
    }

    @Test
    @DisplayName("정수가 아닌 port를 거부한다")
    void rejectNonNumericPort() {
        assertThatThrownBy(() -> ServerConfig.parse(new String[]{"--port", "redis"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("정수");
    }

    @Test
    @DisplayName("허용 범위를 벗어난 port를 거부한다")
    void rejectOutOfRangePort() {
        assertThatThrownBy(() -> ServerConfig.parse(new String[]{"--port", "0"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1에서 65535");
        assertThatThrownBy(() -> ServerConfig.parse(new String[]{"--port", "65536"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1에서 65535");
    }

    @Test
    @DisplayName("옵션 값이 누락되면 오류를 반환한다")
    void rejectMissingOptionValue() {
        assertThatThrownBy(() -> ServerConfig.parse(new String[]{"--port"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("옵션 값");
        assertThatThrownBy(() -> ServerConfig.parse(new String[]{"--aof", "--port", "6380"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--aof");
    }

    @Test
    @DisplayName("알 수 없는 옵션을 거부한다")
    void rejectUnknownOption() {
        assertThatThrownBy(() -> ServerConfig.parse(new String[]{"--host", "localhost"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--host");
    }

    @Test
    @DisplayName("같은 옵션을 중복해서 지정할 수 없다")
    void rejectDuplicateOption() {
        assertThatThrownBy(() -> ServerConfig.parse(new String[]{
                "--port", "6379", "--port", "6380"
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");
    }

    @Test
    @DisplayName("빈 AOF 경로를 거부한다")
    void rejectBlankAofPath() {
        assertThatThrownBy(() -> ServerConfig.parse(new String[]{"--aof", " "}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비어");
    }

    @Test
    @DisplayName("사용법에는 지원하는 옵션이 표시된다")
    void usageIncludesSupportedOptions() {
        assertThat(ServerConfig.usage())
                .contains("--port")
                .contains("--aof");
    }
}
