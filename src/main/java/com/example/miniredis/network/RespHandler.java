package com.example.miniredis.network;

import com.example.miniredis.network.command.CommandDispatcher;
import com.example.miniredis.server.ServerStats;
import com.example.miniredis.storage.KeyValueStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class RespHandler {

    private static final int MAX_ARRAY_LENGTH = 1_024;
    private static final int MAX_BULK_LENGTH = 1_048_576;
    private static final int MAX_LINE_LENGTH = 16_384;

    private final CommandDispatcher dispatcher;

    public RespHandler(KeyValueStore store, ServerStats stats) {
        this.dispatcher = new CommandDispatcher(store, stats);
    }

    /**
     * RESP 프로토콜 또는 일반 텍스트 명령어를 읽어서 파싱 후 응답 생성
     */
    public RespResult processCommand(InputStream input) throws IOException {

        String firstLine;
        try {
            firstLine = readLine(input);
        } catch (ProtocolException e) {
            return RespResult.protocolError(e.getMessage());
        }

        if (firstLine == null) {
            return null; // 클라이언트 연결 종료
        }

        firstLine = firstLine.trim();

        if (firstLine.isEmpty()) {
            return RespResult.keepOpen("");
        }

        // RESP Array 형식
        if (firstLine.startsWith("*")) {

            try {
                int count = parseNonNegativeNumber(firstLine.substring(1), "invalid array length");
                if (count == 0) {
                    return RespResult.protocolError("empty array is not a command");
                }
                if (count > MAX_ARRAY_LENGTH) {
                    return RespResult.protocolError("array length exceeds limit");
                }

                String[] args = new String[count];
                for (int i = 0; i < count; i++) {
                    String lengthLine = readRequiredLine(input);
                    if (!lengthLine.startsWith("$")) {
                        return RespResult.protocolError("expected bulk string");
                    }

                    int length = parseNonNegativeNumber(lengthLine.substring(1), "invalid bulk length");
                    if (length > MAX_BULK_LENGTH) {
                        return RespResult.protocolError("bulk length exceeds limit");
                    }
                    byte[] value = input.readNBytes(length);
                    if (value.length != length) {
                        return RespResult.protocolError("unexpected end of bulk string");
                    }
                    expectCrlf(input);
                    args[i] = new String(value, StandardCharsets.UTF_8);
                }

                return executeAndFormat(args);
            } catch (ProtocolException e) {
                return RespResult.protocolError(e.getMessage());
            }

        } else {

            // 일반 텍스트 명령어
            String[] args = firstLine.split("\\s+");

            return executeAndFormat(args);
        }
    }

    /**
     * 명령 실행은 Dispatcher에게 위임
     */
    private RespResult executeAndFormat(String[] args) {
        return RespResult.keepOpen(dispatcher.execute(args));
    }

    private int parseNonNegativeNumber(String value, String errorMessage) throws ProtocolException {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) throw new ProtocolException(errorMessage);
            return parsed;
        } catch (NumberFormatException e) {
            throw new ProtocolException(errorMessage);
        }
    }

    private String readRequiredLine(InputStream input) throws IOException, ProtocolException {
        String line = readLine(input);
        if (line == null) throw new ProtocolException("unexpected end of input");
        return line;
    }

    private String readLine(InputStream input) throws IOException, ProtocolException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int current;
        while ((current = input.read()) != -1) {
            if (current == '\r') {
                if (input.read() != '\n') {
                    throw new ProtocolException("expected CRLF");
                }
                return buffer.toString(StandardCharsets.UTF_8);
            }
            buffer.write(current);
            if (buffer.size() > MAX_LINE_LENGTH) {
                throw new ProtocolException("line length exceeds limit");
            }
        }
        if (buffer.size() == 0) return null;
        throw new ProtocolException("unexpected end of line");
    }

    private void expectCrlf(InputStream input) throws IOException, ProtocolException {
        if (input.read() != '\r' || input.read() != '\n') {
            throw new ProtocolException("expected CRLF after bulk string");
        }
    }

    private static class ProtocolException extends Exception {
        private ProtocolException(String message) {
            super(message);
        }
    }
}

