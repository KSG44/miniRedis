package com.example.miniredis;

import com.example.miniredis.cli.ConsoleRunner;
import com.example.miniredis.persistence.AofManager;
import com.example.miniredis.storage.KeyValueStore;

public class Main {

    private static final String AOF_FILE_PATH = "appendonly.aof";

    public static void main(String[] args) {
        // 1. AOF 파일 매니저 및 저장소 초기화
        AofManager aofManager = new AofManager(AOF_FILE_PATH);
        KeyValueStore store = new KeyValueStore(aofManager);

        // 2. 기존 AOF 파일이 있다면 데이터 복구
        aofManager.load(store);

        // 3. CLI 실행
        ConsoleRunner runner = new ConsoleRunner(store, aofManager);
        runner.start();
    }
}
