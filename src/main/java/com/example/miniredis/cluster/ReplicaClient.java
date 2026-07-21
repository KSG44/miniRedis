package com.example.miniredis.cluster;

import com.example.miniredis.storage.KeyValueStore;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.Socket;

public class ReplicaClient {

    private final String primaryHost;
    private final int primaryPort;
    private final KeyValueStore replicaStore;

    public ReplicaClient(String primaryHost, int primaryPort, KeyValueStore replicaStore) {
        this.primaryHost = primaryHost;
        this.primaryPort = primaryPort;
        this.replicaStore = replicaStore;
    }

    public void startSync() {
        new Thread(() -> {
            try (
                    Socket socket = new Socket(primaryHost, primaryPort);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
            ) {
                // Primary 서버에게 복구/동기화 요청 명령어 전송
                out.println("REPLCONF SYNC");

                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("+") || line.startsWith("-")) continue;

                    String[] parts = line.split("\\s+");
                    String command = parts[0].toUpperCase();

                    // Primary에서 넘어온 쓰기 명령어를 Replica 로컬 메모리에 반영
                    if ("SET".equalsIgnoreCase(command) && parts.length >= 3) {
                        replicaStore.setWithoutAof(parts[1], parts[2]);
                    } else if ("DEL".equalsIgnoreCase(command) && parts.length >= 2) {
                        replicaStore.deleteWithoutAof(parts[1]);
                    }
                }
            } catch (IOException e) {
                System.out.println("Replica sync disconnected: " + e.getMessage());
            }
        }).start();
    }
}
