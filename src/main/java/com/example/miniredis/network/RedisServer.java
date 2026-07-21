package com.example.miniredis.network;

import com.example.miniredis.storage.KeyValueStore;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class RedisServer {

    private final int port;
    private final KeyValueStore store;

    public RedisServer(int port, KeyValueStore store) {
        this.port = port;
        this.store = store;
    }

    public void start() {
        RespHandler respHandler = new RespHandler(store);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("=== Mini Redis Server listening on port " + port + " ===");

            while (true) {
                // 클라이언트 접속 대기
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());

                // 한 클라이언트와의 통신 처리
                try (
                        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        OutputStream output = clientSocket.getOutputStream()
                ) {
                    while (true) {
                        String response = respHandler.processCommand(reader);
                        if (response == null) {
                            break; // 클라이언트 연결 끊김
                        }
                        if (!response.isEmpty()) {
                            output.write(response.getBytes());
                            output.flush();
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Client disconnected: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
