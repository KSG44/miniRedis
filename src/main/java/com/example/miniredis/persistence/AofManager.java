package com.example.miniredis.persistence;

import com.example.miniredis.storage.KeyValueStore;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.zip.CRC32;

public class AofManager implements AutoCloseable {

    private static final String FORMAT_PREFIX = "MR2";
    private static final String PREVIOUS_FORMAT_PREFIX = "MR1";

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
            throw new UncheckedIOException("AOF 파일 초기화 실패: " + filePath, e);
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

    public void appendExpireAt(String key, long expireAt) {
        appendRecord("EXPIREAT", encode(key), Long.toString(expireAt));
    }

    private synchronized void appendRecord(String command, String... arguments) {
        if (writer == null) {
            throw new IllegalStateException("종료된 AOF에는 기록할 수 없습니다");
        }

        String payload = FORMAT_PREFIX + "\t" + command + "\t" + String.join("\t", arguments);
        String record = payload + "\t" + checksum(payload);
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

        boolean endsWithLineBreak = endsWithLineBreak(file);
        boolean discardLastLine = false;
        List<AofOperation> operations = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
            String line = reader.readLine();
            int lineNumber = 0;
            while (line != null) {
                String nextLine = reader.readLine();
                lineNumber++;

                if (!line.isBlank()) {
                    AofOperation operation = parseRecord(line);
                    if (operation == null) {
                        boolean incompleteLastLine = nextLine == null && !endsWithLineBreak;
                        if (!incompleteLastLine) {
                            throw new AofCorruptionException(lineNumber);
                        }
                        discardLastLine = true;
                        break;
                    }
                    operations.add(operation);
                }
                line = nextLine;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("AOF 복구 실패", e);
        }

        if (!endsWithLineBreak) {
            repairIncompleteTail(file, discardLastLine);
        }

        for (AofOperation operation : operations) {
            operation.apply(store);
        }
    }

    public static boolean applyRecord(String record, KeyValueStore store) {
        AofOperation operation = parseRecord(record);
        if (operation == null) return false;

        operation.apply(store);
        return true;
    }

    private static AofOperation parseRecord(String record) {
        if (record.startsWith(FORMAT_PREFIX + "\t")) {
            return parseChecksummedRecord(record);
        }
        if (record.startsWith(PREVIOUS_FORMAT_PREFIX + "\t")) {
            return parseEncodedRecord(record, PREVIOUS_FORMAT_PREFIX);
        }
        return parseLegacyRecord(record);
    }

    private static AofOperation parseChecksummedRecord(String record) {
        int checksumSeparator = record.lastIndexOf('\t');
        if (checksumSeparator < 0) return null;

        String payload = record.substring(0, checksumSeparator);
        String expectedChecksum = record.substring(checksumSeparator + 1);
        if (!checksum(payload).equalsIgnoreCase(expectedChecksum)) return null;

        return parseEncodedRecord(payload, FORMAT_PREFIX);
    }

    private static AofOperation parseEncodedRecord(String record, String prefix) {
        String[] parts = record.split("\t", -1);
        try {
            if (!prefix.equals(parts[0])) return null;
            if (parts.length == 4 && "SET".equals(parts[1])) {
                return new SetOperation(decode(parts[2]), decode(parts[3]));
            }
            if (parts.length == 5 && "SETEXAT".equals(parts[1])) {
                return new SetExAtOperation(
                        decode(parts[2]),
                        decode(parts[3]),
                        Long.parseLong(parts[4]));
            }
            if (parts.length == 3 && "DEL".equals(parts[1])) {
                return new DeleteOperation(decode(parts[2]));
            }
            if (parts.length == 4 && "EXPIREAT".equals(parts[1])) {
                return new ExpireAtOperation(decode(parts[2]), Long.parseLong(parts[3]));
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private boolean endsWithLineBreak(File file) {
        if (file.length() == 0) return true;

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            randomAccessFile.seek(file.length() - 1);
            int lastByte = randomAccessFile.read();
            return lastByte == '\n' || lastByte == '\r';
        } catch (IOException e) {
            throw new UncheckedIOException("AOF 마지막 줄 확인 실패", e);
        }
    }

    private synchronized void repairIncompleteTail(File file, boolean discardLastLine) {
        close();
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
            if (discardLastLine) {
                randomAccessFile.setLength(findLastCompleteLineEnd(randomAccessFile));
            } else {
                randomAccessFile.seek(randomAccessFile.length());
                randomAccessFile.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("AOF 마지막 줄 복구 실패", e);
        } finally {
            initWriter();
        }
    }

    private long findLastCompleteLineEnd(RandomAccessFile randomAccessFile) throws IOException {
        for (long position = randomAccessFile.length() - 1; position >= 0; position--) {
            randomAccessFile.seek(position);
            int currentByte = randomAccessFile.read();
            if (currentByte == '\n' || currentByte == '\r') {
                return position + 1;
            }
        }
        return 0;
    }

    private static AofOperation parseLegacyRecord(String record) {
        String[] parts = record.split("\\s+");
        if (parts.length >= 3 && "SET".equalsIgnoreCase(parts[0])) {
            return new SetOperation(parts[1], parts[2]);
        }
        if (parts.length >= 2 && "DEL".equalsIgnoreCase(parts[0])) {
            return new DeleteOperation(parts[1]);
        }
        return null;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String checksum(String payload) {
        CRC32 crc32 = new CRC32();
        crc32.update(payload.getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(crc32.getValue());
    }

    private interface AofOperation {
        void apply(KeyValueStore store);
    }

    private record SetOperation(String key, String value) implements AofOperation {
        @Override
        public void apply(KeyValueStore store) {
            store.setWithoutAof(key, value);
        }
    }

    private record SetExAtOperation(String key, String value, long expireAt) implements AofOperation {
        @Override
        public void apply(KeyValueStore store) {
            store.setExAtWithoutAof(key, value, expireAt);
        }
    }

    private record DeleteOperation(String key) implements AofOperation {
        @Override
        public void apply(KeyValueStore store) {
            store.deleteWithoutAof(key);
        }
    }

    private record ExpireAtOperation(String key, long expireAt) implements AofOperation {
        @Override
        public void apply(KeyValueStore store) {
            store.expireAtWithoutAof(key, expireAt);
        }
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
