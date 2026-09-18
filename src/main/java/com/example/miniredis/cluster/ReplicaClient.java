package com.example.miniredis.cluster;

import com.example.miniredis.persistence.AofManager;
import com.example.miniredis.storage.KeyValueStore;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReplicaClient implements AutoCloseable {

    private final String primaryHost;
    private final int primaryPort;
    private final KeyValueStore replicaStore;
    private final AtomicBoolean running = new AtomicBoolean();
    private final CountDownLatch syncStarted = new CountDownLatch(1);
    private volatile Socket socket;
    private volatile Thread syncThread;
    private volatile IOException syncFailure;

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

                String response = in.readLine();
                if (response == null || !response.startsWith("+OK SLAVE SYNC STARTED")) {
                    throw new IOException("Primary가 복제 시작을 승인하지 않았습니다");
                }
                syncStarted.countDown();

                String line;
                while (running.get() && (line = in.readLine()) != null) {
                    if (line.isBlank()) continue;

                    AofManager.applyRecord(line, replicaStore);
                }
            } catch (IOException e) {
                syncFailure = e;
                if (running.get()) {
                    System.out.println("Replica sync disconnected: " + e.getMessage());
                }
            } finally {
                syncStarted.countDown();
                socket = null;
                running.set(false);
            }
        }, "mini-redis-replica-sync");
        syncThread.start();
    }

    public void awaitSyncStarted(long timeout, TimeUnit unit) throws InterruptedException {
        if (!syncStarted.await(timeout, unit)) {
            throw new IllegalStateException("복제가 제한 시간 안에 시작되지 않았습니다");
        }
        if (syncFailure != null) {
            throw new IllegalStateException("복제 시작에 실패했습니다", syncFailure);
        }
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
