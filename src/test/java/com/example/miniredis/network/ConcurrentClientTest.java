package com.example.miniredis.network;

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

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentClientTest {

    private static final int TEST_PORT = 6381;
    private Thread serverThread;

    @BeforeEach
    void setUp() throws InterruptedException {
        KeyValueStore store = new KeyValueStore();
        RedisServer server = new RedisServer(TEST_PORT, store);

        serverThread = new Thread(server::start);
        serverThread.start();
        Thread.sleep(200); // 서버 시작 대기
    }

    @AfterEach
    void tearDown() {
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
        }
    }

    @Test
    @DisplayName("10개의 동시 클라이언트가 접속해도 차단 없이 정상 응답해야 한다")
    void concurrentClientsTest() throws InterruptedException {
        int clientCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch latch = new CountDownLatch(clientCount);

        for (int i = 0; i < clientCount; i++) {
            final int clientId = i;
            executor.execute(() -> {
                try (
                        Socket socket = new Socket("localhost", TEST_PORT);
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

                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 클라이언트 작업 종료 대기
        executor.shutdown();
    }
}
