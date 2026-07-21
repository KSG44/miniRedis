package com.example.miniredis.persistence;

import com.example.miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class AofTest {

    private static final String TEST_AOF_PATH = "test_appendonly.aof";
    private AofManager aofManager;

    @BeforeEach
    void setUp() {
        // 기존 파일이 남아있다면 삭제 후 시작
        File file = new File(TEST_AOF_PATH);
        if (file.exists()) {
            file.delete();
        }
        aofManager = new AofManager(TEST_AOF_PATH);
    }

    @AfterEach
    void tearDown() {
        if (aofManager != null) {
            aofManager.close();
        }
        // 테스트 완료 후 생성된 테스트 파일 삭제
        File file = new File(TEST_AOF_PATH);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    @DisplayName("AOF 파일에 명령어가 기록되고, 재시작 시 파일로부터 데이터가 복구되어야 한다")
    void testAofPersistenceAndRecovery() {
        // 1. 첫 번째 서버 가동 (데이터 쓰기)
        KeyValueStore store1 = new KeyValueStore(aofManager);
        store1.set("user:1", "alice");
        store1.set("user:2", "bob");
        store1.delete("user:1");

        aofManager.close(); // AOF 파일 작성 완료 및 닫기

        // 2. 두 번째 서버 가동 (새 메모리 상태에서 AOF 로드)
        AofManager recoveryAofManager = new AofManager(TEST_AOF_PATH);
        KeyValueStore store2 = new KeyValueStore(recoveryAofManager);

        // AOF 로드로 이전 변경 내역 재실행
        recoveryAofManager.load(store2);

        // 3. 복구 결과 검증
        assertThat(store2.get("user:1")).isNull(); // 삭제된 데이터
        assertThat(store2.get("user:2")).isEqualTo("bob"); // 유지된 데이터

        recoveryAofManager.close();
    }
}
