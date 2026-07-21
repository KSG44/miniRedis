package com.example.miniredis.network;

import com.example.miniredis.persistence.AofManager;
import com.example.miniredis.storage.KeyValueStore;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RedisServer {

    private final int port;
    private final KeyValueStore store;
    private final AofManager aofManager; // 추가된 부분!
    private final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    // 기존 테스트 코드 호환용 생성자
    public RedisServer(int port, KeyValueStore store) {
        this(port, store, null);
    }

    // AofManager를 포함하는 생성자
    public RedisServer(int port, KeyValueStore store, AofManager aofManager) {
        this.port = port;
        this.store = store;
        this.aofManager = aofManager; // 추가된 부분!
    }

    public void start() {
        RespHandler respHandler = new RespHandler(store);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("=== Mini Redis Multi-Threaded Server listening on port " + port + " ===");

            while (!Thread.currentThread().isInterrupted()) {
                // 1. 클라이언트 연결 대기
                Socket clientSocket = serverSocket.accept();

                // 2. 새로운 클라이언트 연결이 들어오면 쓰레드 풀의 Worker Thread에게 위임
                threadPool.execute(() -> handleClient(clientSocket, respHandler));
            }
        } catch (IOException e) {
            System.out.println("Server stopped or exception occurred: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    /**
     * 개별 클라이언트 요청 처리 (별도 쓰레드에서 실행됨)
     */
    private void handleClient(Socket clientSocket, RespHandler respHandler) {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStream output = clientSocket.getOutputStream()
        ) {
            while (true) {
                String response = respHandler.processCommand(reader);
                if (response == null) {
                    break;
                }
                if (!response.isEmpty()) {
                    output.write(response.getBytes());
                    output.flush();

                    // 만약 클라이언트가 REPLCONF SYNC를 보냈다면, 이 소켓을 AOF 명령어 리스너로 등록
                    if (response.contains("SLAVE SYNC STARTED") && aofManager != null) {
                        aofManager.addCommandListener(cmd -> {
                            try {
                                output.write((cmd + "\r\n").getBytes());
                                output.flush();
                            } catch (IOException e) {
                                // 소켓 끊김
                            }
                        });
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }
}
