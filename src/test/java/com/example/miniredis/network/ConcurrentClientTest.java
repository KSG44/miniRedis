package com.example.miniredis.network;

import com.example.miniredis.server.ServerStats;
import com.example.miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentClientTest {

    private Thread serverThread;
    private RedisServer server;
    private int serverPort;

    @BeforeEach
    void setUp() throws InterruptedException {
        KeyValueStore store = new KeyValueStore();
        ServerStats stats = new ServerStats();

        server = new RedisServer(0, store, null, stats);
        serverThread = new Thread(server::start);
        serverThread.start();
        serverPort = server.awaitStarted(2, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (serverThread != null) {
            try {
                serverThread.join(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    @DisplayName("10개의 동시 클라이언트가 접속해도 차단 없이 정상 응답해야 한다")
    void concurrentClientsTest() throws InterruptedException {
        int clientCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch latch = new CountDownLatch(clientCount);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int i = 0; i < clientCount; i++) {
            final int clientId = i;
            executor.execute(() -> {
                try (
                        Socket socket = new Socket("localhost", serverPort);
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
                ) {
                    // 동시 SET 및 GET 수행
                    out.println("SET client_" + clientId + " val_" + clientId);
                    String setResp = in.readLine();
                    assertThat(setResp).isEqualTo("+OK");

                    out.println("GET client_" + clientId);
                    in.readLine(); // $5 (길이)
                    String getVal = in.readLine();
                    assertThat(getVal).isEqualTo("val_" + clientId);

                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();
        assertThat(failure.get()).isNull();
    }
}
