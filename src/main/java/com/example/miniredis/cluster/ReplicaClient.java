package com.example.miniredis.cluster;

import com.example.miniredis.persistence.AofManager;
import com.example.miniredis.storage.KeyValueStore;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReplicaClient implements AutoCloseable {

    private final String primaryHost;
    private final int primaryPort;
    private final KeyValueStore replicaStore;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Socket socket;
    private volatile Thread syncThread;

    public ReplicaClient(String primaryHost, int primaryPort, KeyValueStore replicaStore) {
        this.primaryHost = primaryHost;
        this.primaryPort = primaryPort;
        this.replicaStore = replicaStore;
    }

    public void startSync() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        syncThread = new Thread(() -> {
            try (
                    Socket connectedSocket = new Socket(primaryHost, primaryPort);
                    PrintWriter out = new PrintWriter(connectedSocket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(connectedSocket.getInputStream()))
            ) {
                socket = connectedSocket;
                // Primary 서버에게 복구/동기화 요청 명령어 전송
                out.println("REPLCONF SYNC");

                String line;
                while (running.get() && (line = in.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("+") || line.startsWith("-")) continue;

                    AofManager.applyRecord(line, replicaStore);
                }
            } catch (IOException e) {
                if (running.get()) {
                    System.out.println("Replica sync disconnected: " + e.getMessage());
                }
            } finally {
                socket = null;
                running.set(false);
            }
        }, "mini-redis-replica-sync");
        syncThread.start();
    }

    public void stop() {
        running.set(false);
        Socket connectedSocket = socket;
        if (connectedSocket != null) {
            try {
                connectedSocket.close();
            } catch (IOException ignored) {
            }
        }
        Thread thread = syncThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public void close() {
        stop();
    }
}
