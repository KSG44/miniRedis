package com.example.miniredis.network;

import com.example.miniredis.network.command.CommandDispatcher;
import com.example.miniredis.storage.KeyValueStore;

import java.io.BufferedReader;
import java.io.IOException;

public class RespHandler {

    private final CommandDispatcher dispatcher;

    public RespHandler(KeyValueStore store) {
        this.dispatcher = new CommandDispatcher(store);
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

        // RESP Array 형식
        if (firstLine.startsWith("*")) {

            int count = Integer.parseInt(firstLine.substring(1));
            String[] args = new String[count];

            for (int i = 0; i < count; i++) {
                reader.readLine();       // $3 같은 길이 정보
                args[i] = reader.readLine();
            }

            return executeAndFormat(args);

        } else {

            // 일반 텍스트 명령어
            String[] args = firstLine.split("\\s+");

            return executeAndFormat(args);
        }
    }

    /**
     * 명령 실행은 Dispatcher에게 위임
     */
    private String executeAndFormat(String[] args) {
        return dispatcher.execute(args);
    }
}

