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
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @DisplayName("RESP 구조 오류에는 오류를 응답한 뒤 연결을 종료한다")
    void protocolErrorClosesConnectionAfterResponse() throws IOException {
        try (
                Socket socket = new Socket("localhost", serverPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            socket.setSoTimeout(1_000);
            out.print("*1\r\nGET\r\n");
            out.flush();

            assertThat(in.readLine()).isEqualTo("-ERR Protocol error: expected bulk string");
            assertThat(in.readLine()).isNull();
        }
    }

    @Test
    @DisplayName("명령 인자 오류에는 연결을 유지하여 다음 명령을 처리한다")
    void commandErrorKeepsConnectionOpen() throws IOException {
        try (
                Socket socket = new Socket("localhost", serverPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            socket.setSoTimeout(1_000);
            out.print("GET\r\nPING\r\n");
            out.flush();

            assertThat(in.readLine()).isEqualTo("-ERR wrong number of arguments for 'get'");
            assertThat(in.readLine()).isEqualTo("+PONG");
        }
    }

    @Test
    @DisplayName("이미 사용 중인 포트에서는 서버 시작 실패를 호출자에게 전달한다")
    void portConflictReportsStartupFailure() throws Exception {
        try (ServerSocket reservedPort = new ServerSocket(0)) {
            RedisServer conflictingServer = new RedisServer(
                    reservedPort.getLocalPort(), new KeyValueStore(), null, new ServerStats());
            AtomicReference<Throwable> threadFailure = new AtomicReference<>();
            Thread conflictingThread = new Thread(conflictingServer::start);
            conflictingThread.setUncaughtExceptionHandler((thread, error) -> threadFailure.set(error));

            try {
                conflictingThread.start();

                assertThatThrownBy(() -> conflictingServer.awaitStarted(1, TimeUnit.SECONDS))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("서버 시작에 실패")
                        .hasCauseInstanceOf(RuntimeException.class);

                conflictingThread.join(1_000);
                assertThat(conflictingThread.isAlive()).isFalse();
                assertThat(threadFailure.get()).isInstanceOf(RuntimeException.class);
            } finally {
                conflictingServer.stop();
            }
        }
    }

    @Test
    @DisplayName("시작하지 않은 서버의 준비 상태를 기다리면 제한 시간 후 실패한다")
    void startupWaitTimesOut() {
        RedisServer unstartedServer = new RedisServer(0, new KeyValueStore(), null, new ServerStats());

        try {
            assertThatThrownBy(() -> unstartedServer.awaitStarted(20, TimeUnit.MILLISECONDS))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("제한 시간");
        } finally {
            unstartedServer.stop();
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
