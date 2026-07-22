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

import static org.assertj.core.api.Assertions.assertThat;

class RedisServerTest {

    private static final int TEST_PORT = 6380;
    private Thread serverThread;

    @BeforeEach
    void setUp() throws InterruptedException {
        // 서버를 별도 스레드에서 비동기로 실행
        KeyValueStore store = new KeyValueStore();
        ServerStats stats = new ServerStats();

        RedisServer server = new RedisServer(TEST_PORT, store, null, stats);
        serverThread = new Thread(server::start);
        serverThread.start();

        // 서버 소켓이 켜질 때까지 잠시 대기
        Thread.sleep(200);
    }

    @AfterEach
    void tearDown() {
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
        }
    }

    @Test
    @DisplayName("실제 TCP Socket 연결을 통해 SET 및 GET 요청 후 정상 응답을 받는다")
    void tcpSocketClientTest() throws IOException {
        try (
                Socket socket = new Socket("localhost", TEST_PORT);
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
}
