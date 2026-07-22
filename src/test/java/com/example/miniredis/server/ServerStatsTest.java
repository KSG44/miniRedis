package com.example.miniredis.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerStatsTest {

    @Test
    @DisplayName("버전 정보를 반환한다.")
    void version() {

        ServerStats stats = new ServerStats();

        assertThat(stats.getVersion())
                .isEqualTo("0.1");
    }

    @Test
    @DisplayName("Uptime은 시간이 지날수록 증가한다.")
    void uptime() throws Exception {

        ServerStats stats = new ServerStats();

        Thread.sleep(1200);

        assertThat(stats.getUptimeSeconds())
                .isGreaterThanOrEqualTo(1);
    }
}
