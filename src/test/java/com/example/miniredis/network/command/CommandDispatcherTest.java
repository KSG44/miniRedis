package com.example.miniredis.network.command;

import com.example.miniredis.server.ServerStats;
import com.example.miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandDispatcherTest {

    @Test
    @DisplayName("SET 명령어 테스트")
    void setCommand() {
        ServerStats stats = new ServerStats();
        KeyValueStore store = new KeyValueStore();

        CommandDispatcher dispatcher = new CommandDispatcher(store,stats);

        String result = dispatcher.execute(new String[]{
                "SET", "name", "gon"
        });

        assertThat(result).isEqualTo("+OK\r\n");
        assertThat(store.get("name")).isEqualTo("gon");

    }
    @Test
    @DisplayName("GET 명령 테스트")
    void getCommand() {
        ServerStats stats = new ServerStats();
        KeyValueStore store = new KeyValueStore();
        store.set("name", "gon");

        CommandDispatcher dispatcher = new CommandDispatcher(store,stats);

        String result = dispatcher.execute(new String[]{
                "GET", "name"
        });

        assertThat(result).isEqualTo("$3\r\ngon\r\n");
    }
    @Test
    @DisplayName("INFO 명령어 테스트")
    void infoCommand() {

        KeyValueStore store = new KeyValueStore();
        ServerStats stats = new ServerStats();

        CommandDispatcher dispatcher = new CommandDispatcher(store, stats);

        store.set("name", "gon");

        String result =
                dispatcher.execute(new String[]{"INFO"});

        assertThat(result)
                .contains("miniRedis_version:0.1")
                .contains("keys:1");
    }
}
