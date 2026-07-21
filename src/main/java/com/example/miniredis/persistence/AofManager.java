package com.example.miniredis.persistence;

import com.example.miniredis.storage.KeyValueStore;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AofManager {

    private final String filePath;
    private BufferedWriter writer;

    public AofManager(String filePath) {
        this.filePath = filePath;
        initWriter();
    }

    private void initWriter() {
        try {
            // 기존 파일이 존재하면 이어쓰기(append: true) 모드로 BufferedWriter 생성
            FileWriter fw = new FileWriter(filePath, true);
            this.writer = new BufferedWriter(fw);
        } catch (IOException e) {
            throw new RuntimeException("AOF 파일 초기화 실패", e);
        }
    }

    /**
     * 명령어를 AOF 파일에 기록
     */
    public synchronized void append(String command) {
        try {
            writer.write(command);
            writer.newLine();
            writer.flush(); // 즉시 파일에 쓰기
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 서버 재시작 시 AOF 파일 읽어 데이터 복구
     */
    public void load(KeyValueStore store) {
        File file = new File(filePath);
        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\s+");
                if (parts.length == 0) continue;

                String command = parts[0].toUpperCase();

                // 복구 시에는 AOF에 다시 쓰지 않는 메서드 호출!
                if ("SET".equals(command) && parts.length >= 3) {
                    store.setWithoutAof(parts[1], parts[2]);
                } else if ("DEL".equals(command) && parts.length >= 2) {
                    store.deleteWithoutAof(parts[1]);
                }
            }
        } catch (IOException e) {
            System.err.println("AOF 복구 중 오류 발생: " + e.getMessage());
        }
    }


    /**
     * 파일 자원 해제
     */
    public void close() {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
