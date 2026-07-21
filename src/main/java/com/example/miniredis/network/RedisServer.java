package com.example.miniredis.network;

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
    // 동시 접속 처리를 위한 쓰레드 풀 (최대 10개 쓰레드)
    private final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    public RedisServer(int port, KeyValueStore store) {
        this.port = port;
        this.store = store;
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
        System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());

        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStream output = clientSocket.getOutputStream()
        ) {
            while (true) {
                String response = respHandler.processCommand(reader);
                if (response == null) {
                    break; // 클라이언트 연결 종료
                }
                if (!response.isEmpty()) {
                    output.write(response.getBytes());
                    output.flush();
                }
            }
        } catch (IOException e) {
            // 클라이언트 비정상 종료 시 처리
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
            System.out.println("Client disconnected: " + clientSocket.getRemoteSocketAddress());
        }
    }
}
