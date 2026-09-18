package com.example.miniredis.network;

import com.example.miniredis.persistence.AofManager;
import com.example.miniredis.server.ServerStats;
import com.example.miniredis.storage.KeyValueStore;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class RedisServer implements AutoCloseable {

    private final int port;
    private final KeyValueStore store;
    private final AofManager aofManager;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(10);
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private final ServerStats stats;
    private volatile boolean running;
    private volatile ServerSocket serverSocket;

    public RedisServer(int port, KeyValueStore store, AofManager aofManager ,ServerStats stats) {
        this.port = port;
        this.store = store;
        this.stats = stats;
        this.aofManager = aofManager;
    }

    public RedisServer(int port, KeyValueStore store, AofManager aofManager) {
        this.port = port;
        this.store = store;
        this.aofManager = aofManager;
        this.stats = new ServerStats();
    }

    public void start() {
        RespHandler respHandler = new RespHandler(store, stats);

        running = true;
        try (ServerSocket listeningSocket = new ServerSocket(port)) {
            this.serverSocket = listeningSocket;
            System.out.println("=== Mini Redis Multi-Threaded Server listening on port " + port + " ===");

            while (running) {
                // 1. 클라이언트 연결 대기
                Socket clientSocket = listeningSocket.accept();
                clients.add(clientSocket);

                // 2. 새로운 클라이언트 연결이 들어오면 쓰레드 풀의 Worker Thread에게 위임
                threadPool.execute(() -> handleClient(clientSocket, respHandler));
            }
        } catch (SocketException e) {
            if (running) {
                throw new RuntimeException("서버 소켓 오류", e);
            }
        } catch (IOException e) {
            throw new RuntimeException("서버 실행 오류", e);
        } finally {
            running = false;
            serverSocket = null;
            threadPool.shutdown();
        }
    }

    /**
     * 개별 클라이언트 요청 처리 (별도 쓰레드에서 실행됨)
     */
    private void handleClient(Socket clientSocket, RespHandler respHandler) {
        Consumer<String> replicationListener = null;
        try (
                InputStream input = new BufferedInputStream(clientSocket.getInputStream());
                OutputStream output = clientSocket.getOutputStream()
        ) {
            while (true) {
                String response = respHandler.processCommand(input);
                if (response == null) {
                    break;
                }
                if (!response.isEmpty()) {
                    output.write(response.getBytes(StandardCharsets.UTF_8));
                    output.flush();

                    // 만약 클라이언트가 REPLCONF SYNC를 보냈다면, 이 소켓을 AOF 명령어 리스너로 등록
                    if (response.contains("SLAVE SYNC STARTED")
                            && aofManager != null
                            && replicationListener == null) {
                        replicationListener = cmd -> {
                            try {
                                output.write((cmd + "\r\n").getBytes(StandardCharsets.UTF_8));
                                output.flush();
                            } catch (IOException e) {
                                // 소켓 끊김
                            }
                        };
                        aofManager.addCommandListener(replicationListener);
                    }
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (aofManager != null && replicationListener != null) {
                aofManager.removeCommandListener(replicationListener);
            }
            clients.remove(clientSocket);
        }
    }

    public void stop() {
        running = false;
        closeQuietly(serverSocket);
        for (Socket client : clients) {
            closeQuietly(client);
        }
        threadPool.shutdownNow();
    }

    @Override
    public void close() {
        stop();
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
