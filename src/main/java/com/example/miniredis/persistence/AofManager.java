package com.example.miniredis.persistence;

import com.example.miniredis.storage.KeyValueStore;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class AofManager {

    private final String filePath;
    private BufferedWriter writer;
    // Replica 서버로 명령어를 전파하기 위한 이벤트 리스너 목록
    private final List<Consumer<String>> commandListeners = new CopyOnWriteArrayList<>();

    public AofManager(String filePath) {
        this.filePath = filePath;
        initWriter();
    }

    private void initWriter() {
        try {
            FileWriter fw = new FileWriter(filePath, true);
            this.writer = new BufferedWriter(fw);
        } catch (IOException e) {
            throw new RuntimeException("AOF 파일 초기화 실패", e);
        }
    }

    public void addCommandListener(Consumer<String> listener) {
        commandListeners.add(listener);
    }

    public synchronized void append(String command) {
        try {
            writer.write(command);
            writer.newLine();
            writer.flush();

            // 연결된 Replica들에게 명령어 전파 (Broadcasting)
            for (Consumer<String> listener : commandListeners) {
                listener.accept(command);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load(KeyValueStore store) {
        File file = new File(filePath);
        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                if (parts.length == 0) continue;

                String command = parts[0].toUpperCase();

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
