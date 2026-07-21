package com.example.miniredis.network;

import com.example.miniredis.storage.KeyValueStore;

import java.io.BufferedReader;
import java.io.IOException;

public class RespHandler {

    private final KeyValueStore store;

    public RespHandler(KeyValueStore store) {
        this.store = store;
    }

    /**
     * RESP 프로토콜 또는 일반 텍스트 명령어를 읽어서 파싱 후 응답 생성
     */
    public String processCommand(BufferedReader reader) throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null) {
            return null; // 클라이언트 연결 종료
        }

        firstLine = firstLine.trim();
        if (firstLine.isEmpty()) {
            return "";
        }

        // RESP Array 규격인 경우 (*3\r\n...)
        if (firstLine.startsWith("*")) {
            int count = Integer.parseInt(firstLine.substring(1));
            String[] args = new String[count];

            for (int i = 0; i < count; i++) {
                reader.readLine(); // $3 같은 길이 정보 건너뛰기
                args[i] = reader.readLine(); // 실제 값 (e.g. SET, key, value)
            }
            return executeAndFormat(args);
        } else {
            // telnet이나 간단한 텍스트 입력 처리 (e.g. SET key value)
            String[] args = firstLine.split("\\s+");
            return executeAndFormat(args);
        }
    }

    private String executeAndFormat(String[] args) {
        if (args.length == 0) return "-ERR empty command\r\n";

        String command = args[0].toUpperCase();

        switch (command) {
            case "PING":
                return "+PONG\r\n";

            case "SET":
                if (args.length < 3) return "-ERR wrong number of arguments for 'set'\r\n";
                store.set(args[1], args[2]);
                return "+OK\r\n";

            case "SETEX":
                if (args.length < 4) return "-ERR wrong number of arguments for 'setex'\r\n";
                try {
                    long ttl = Long.parseLong(args[3]);
                    store.setEx(args[1], args[2], ttl);
                    return "+OK\r\n";
                } catch (NumberFormatException e) {
                    return "-ERR value is not an integer or out of range\r\n";
                }

            case "GET":
                if (args.length < 2) return "-ERR wrong number of arguments for 'get'\r\n";
                String val = store.get(args[1]);
                if (val == null) {
                    return "$-1\r\n"; // Null Bulk String
                }
                return "$" + val.getBytes().length + "\r\n" + val + "\r\n";

            case "DEL":
                if (args.length < 2) return "-ERR wrong number of arguments for 'del'\r\n";
                boolean deleted = store.delete(args[1]);
                return ":" + (deleted ? 1 : 0) + "\r\n";


            case "REPLCONF":
                if (args.length >= 2 && "SYNC".equalsIgnoreCase(args[1])) {
                    // 복제 연결 요청 수락
                    return "+OK SLAVE SYNC STARTED\r\n";
                }
                return "+OK\r\n";

            default:
                return "-ERR unknown command '" + args[0] + "'\r\n";
        }
    }
}
