package com.example.miniredis.persistence;

import com.example.miniredis.storage.KeyValueStore;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class AofManager implements AutoCloseable {

    private static final String FORMAT_PREFIX = "MR1";

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

    public void removeCommandListener(Consumer<String> listener) {
        commandListeners.remove(listener);
    }

    public void appendSet(String key, String value) {
        appendRecord("SET", encode(key), encode(value));
    }

    public void appendSetExAt(String key, String value, long expireAt) {
        appendRecord("SETEXAT", encode(key), encode(value), Long.toString(expireAt));
    }

    public void appendDelete(String key) {
        appendRecord("DEL", encode(key));
    }

    private synchronized void appendRecord(String command, String... arguments) {
        String record = FORMAT_PREFIX + "\t" + command + "\t" + String.join("\t", arguments);
        try {
            writer.write(record);
            writer.newLine();
            writer.flush();

            for (Consumer<String> listener : commandListeners) {
                listener.accept(record);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("AOF 기록 실패", e);
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
                if (line.isBlank()) continue;

                applyRecord(line, store);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("AOF 복구 실패", e);
        }
    }

    public static boolean applyRecord(String record, KeyValueStore store) {
        if (record.startsWith(FORMAT_PREFIX + "\t")) {
            return applyCurrentRecord(record, store);
        }
        return applyLegacyRecord(record, store);
    }

    private static boolean applyCurrentRecord(String record, KeyValueStore store) {
        String[] parts = record.split("\t", -1);
        try {
            if (parts.length == 4 && "SET".equals(parts[1])) {
                store.setWithoutAof(decode(parts[2]), decode(parts[3]));
                return true;
            }
            if (parts.length == 5 && "SETEXAT".equals(parts[1])) {
                store.setExAtWithoutAof(decode(parts[2]), decode(parts[3]), Long.parseLong(parts[4]));
                return true;
            }
            if (parts.length == 3 && "DEL".equals(parts[1])) {
                store.deleteWithoutAof(decode(parts[2]));
                return true;
            }
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        return false;
    }

    private static boolean applyLegacyRecord(String record, KeyValueStore store) {
        String[] parts = record.split("\\s+");
        if (parts.length >= 3 && "SET".equalsIgnoreCase(parts[0])) {
            store.setWithoutAof(parts[1], parts[2]);
            return true;
        }
        if (parts.length >= 2 && "DEL".equalsIgnoreCase(parts[0])) {
            store.deleteWithoutAof(parts[1]);
            return true;
        }
        return false;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    @Override
    public synchronized void close() {
        try {
            if (writer != null) {
                writer.close();
                writer = null;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("AOF 종료 실패", e);
        }
    }
}
