package com.example.miniredis.persistence;

import com.example.miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class AofTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("AOF 파일에 명령어가 기록되고, 재시작 시 파일로부터 데이터가 복구되어야 한다")
    void testAofPersistenceAndRecovery() {
        Path aofPath = tempDir.resolve("appendonly.aof");
        AofManager aofManager = new AofManager(aofPath.toString());

        // 1. 첫 번째 서버 가동 (데이터 쓰기)
        KeyValueStore store1 = new KeyValueStore(aofManager);
        store1.set("user:1", "alice");
        store1.set("user:2", "bob");
        store1.delete("user:1");

        aofManager.close(); // AOF 파일 작성 완료 및 닫기

        // 2. 두 번째 서버 가동 (새 메모리 상태에서 AOF 로드)
        AofManager recoveryAofManager = new AofManager(aofPath.toString());
        KeyValueStore store2 = new KeyValueStore(recoveryAofManager);

        // AOF 로드로 이전 변경 내역 재실행
        recoveryAofManager.load(store2);

        // 3. 복구 결과 검증
        assertThat(store2.get("user:1")).isNull(); // 삭제된 데이터
        assertThat(store2.get("user:2")).isEqualTo("bob"); // 유지된 데이터

        recoveryAofManager.close();
    }

    @Test
    @DisplayName("공백, 한글, 빈 문자열이 포함된 Key와 Value도 정확하게 복구한다")
    void recoverSpecialValues() {
        Path aofPath = tempDir.resolve("special.aof");
        AofManager writer = new AofManager(aofPath.toString());
        KeyValueStore original = new KeyValueStore(writer);
        original.set("사용자 이름", "홍 길동");
        original.set("empty", "");
        writer.close();

        AofManager reader = new AofManager(aofPath.toString());
        try {
            KeyValueStore recovered = new KeyValueStore(reader);
            reader.load(recovered);

            assertThat(recovered.get("사용자 이름")).isEqualTo("홍 길동");
            assertThat(recovered.get("empty")).isEmpty();
        } finally {
            reader.close();
        }
    }

    @Test
    @DisplayName("SETEX 데이터는 재시작 후에도 원래 만료 시각을 유지한다")
    void recoverTtlWithOriginalExpiration() throws InterruptedException {
        Path aofPath = tempDir.resolve("ttl.aof");
        AofManager writer = new AofManager(aofPath.toString());
        KeyValueStore original = new KeyValueStore(writer);
        original.setEx("session", "active", 250);
        writer.close();

        Thread.sleep(80);

        AofManager reader = new AofManager(aofPath.toString());
        KeyValueStore recovered = new KeyValueStore(reader);
        reader.load(recovered);
        assertThat(recovered.get("session")).isEqualTo("active");

        Thread.sleep(200);
        assertThat(recovered.get("session")).isNull();
        reader.close();
    }

    @Test
    @DisplayName("이미 만료된 SETEX 데이터는 재시작 시 복구하지 않는다")
    void doNotRecoverAlreadyExpiredValue() throws InterruptedException {
        Path aofPath = tempDir.resolve("expired.aof");
        AofManager writer = new AofManager(aofPath.toString());
        new KeyValueStore(writer).setEx("session", "expired", 30);
        writer.close();

        Thread.sleep(50);

        AofManager reader = new AofManager(aofPath.toString());
        KeyValueStore recovered = new KeyValueStore(reader);
        reader.load(recovered);

        assertThat(recovered.get("session")).isNull();
        reader.close();
    }

    @Test
    @DisplayName("기존 공백 구분 AOF 형식도 계속 읽을 수 있다")
    void loadLegacyAofFormat() throws IOException {
        Path aofPath = tempDir.resolve("legacy.aof");
        Files.writeString(aofPath, "SET old value\nDEL removed\n");

        AofManager reader = new AofManager(aofPath.toString());
        KeyValueStore recovered = new KeyValueStore(reader);
        recovered.setWithoutAof("removed", "value");
        reader.load(recovered);

        assertThat(recovered.get("old")).isEqualTo("value");
        assertThat(recovered.get("removed")).isNull();
        reader.close();
    }

    @Test
    @DisplayName("손상되거나 알 수 없는 AOF 줄은 나머지 복구를 막지 않는다")
    void ignoreMalformedAofLines() throws IOException {
        Path aofPath = tempDir.resolve("malformed.aof");
        Files.writeString(aofPath, "BROKEN\nSET valid value\nMR1\tSET\tinvalid-base64\t%%%\n");

        AofManager reader = new AofManager(aofPath.toString());
        KeyValueStore recovered = new KeyValueStore(reader);
        reader.load(recovered);

        assertThat(recovered.get("valid")).isEqualTo("value");
        assertThat(recovered.size()).isEqualTo(1);
        reader.close();
    }

    @Test
    @DisplayName("제거된 복제 Listener에는 이후 AOF 변경을 전달하지 않는다")
    void removedListenerDoesNotReceiveRecords() {
        Path aofPath = tempDir.resolve("listener.aof");
        AofManager manager = new AofManager(aofPath.toString());
        List<String> records = new ArrayList<>();
        Consumer<String> listener = records::add;
        manager.addCommandListener(listener);

        manager.appendSet("first", "value");
        manager.removeCommandListener(listener);
        manager.appendSet("second", "value");

        assertThat(records).hasSize(1);
        assertThat(AofManager.applyRecord(records.getFirst(), new KeyValueStore())).isTrue();
        manager.close();
    }
}
