package com.example.miniredis.network.command;

import com.example.miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandDispatcherTest {

    @Test
    @DisplayName("SET 명령어 테스트")
    void setCommand() {

        KeyValueStore store = new KeyValueStore();

        CommandDispatcher dispatcher = new CommandDispatcher(store);

        String result = dispatcher.execute(new String[]{
                "SET", "name", "gon"
        });

        assertThat(result).isEqualTo("+OK\r\n");
        assertThat(store.get("name")).isEqualTo("gon");

    }
    @Test
    @DisplayName("GET 명령 테스트")
    void getCommand() {

        KeyValueStore store = new KeyValueStore();
        store.set("name", "gon");

        CommandDispatcher dispatcher = new CommandDispatcher(store);

        String result = dispatcher.execute(new String[]{
                "GET", "name"
        });

        assertThat(result).isEqualTo("$3\r\ngon\r\n");
    }
}
