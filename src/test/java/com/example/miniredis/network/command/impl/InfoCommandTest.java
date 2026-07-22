package com.example.miniredis.network.command.impl;

import com.example.miniredis.server.ServerStats;
import com.example.miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InfoCommandTest {

    @Test
    @DisplayName("INFO 명령은 서버 정보를 반환한다.")
    void infoCommand() {

        KeyValueStore store = new KeyValueStore();
        ServerStats stats = new ServerStats();

        store.set("name", "gon");

        InfoCommand command = new InfoCommand(store, stats);

        String result = command.execute(new String[]{"INFO"});

        assertThat(result).contains("miniRedis_version:0.1");
        assertThat(result).contains("uptime_in_seconds:");
        assertThat(result).contains("keys:1");
    }
}
