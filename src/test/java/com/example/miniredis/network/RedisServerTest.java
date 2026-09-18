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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RedisServerTest {

    private Thread serverThread;
    private RedisServer server;
    private int serverPort;

    @BeforeEach
    void setUp() throws InterruptedException {
        // 서버를 별도 스레드에서 비동기로 실행
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
    @DisplayName("실제 TCP Socket 연결을 통해 SET 및 GET 요청 후 정상 응답을 받는다")
    void tcpSocketClientTest() throws IOException {
        try (
                Socket socket = new Socket("localhost", serverPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            // SET 명령어 전송
            out.println("SET message hello_world");
            String setResponse = in.readLine();
            assertThat(setResponse).isEqualTo("+OK");

            // GET 명령어 전송
            out.println("GET message");
            String lengthLine = in.readLine(); // $11
            String valueLine = in.readLine();  // hello_world

            assertThat(lengthLine).isEqualTo("$11");
            assertThat(valueLine).isEqualTo("hello_world");
        }
    }

    @Test
    @DisplayName("stop 호출 시 서버 스레드가 종료되고 새 연결을 받지 않는다")
    void stopClosesServerSocket() throws Exception {
        server.stop();
        serverThread.join(1_000);

        assertThat(serverThread.isAlive()).isFalse();
    }
}
