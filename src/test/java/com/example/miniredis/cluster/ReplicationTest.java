package com.example.miniredis.cluster;

import com.example.miniredis.network.RedisServer;
import com.example.miniredis.persistence.AofManager;
import com.example.miniredis.storage.KeyValueStore;
import com.example.miniredis.testsupport.TestAwait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ReplicationTest {

    @TempDir
    Path tempDir;

    private KeyValueStore primaryStore;
    private KeyValueStore replicaStore;
    private AofManager primaryAofManager;
    private Thread primaryServerThread;
    private RedisServer primaryServer;
    private ReplicaClient replicaClient;
    private int primaryPort;

    @BeforeEach
    void setUp() throws InterruptedException {
        // 1. Primary 서버 기동
        primaryAofManager = new AofManager(tempDir.resolve("primary.aof").toString());
        primaryStore = new KeyValueStore(primaryAofManager);
        primaryServer = new RedisServer(0, primaryStore, primaryAofManager);

        primaryServerThread = new Thread(primaryServer::start);
        primaryServerThread.start();
        primaryPort = primaryServer.awaitStarted(2, TimeUnit.SECONDS);

        // 2. Replica 메모리 및 동기화 클라이언트 생성
        replicaStore = new KeyValueStore();
        replicaClient = new ReplicaClient("localhost", primaryPort, replicaStore);
        replicaClient.startSync();
        replicaClient.awaitSyncStarted(2, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        if (replicaClient != null) {
            replicaClient.stop();
        }
        if (primaryServer != null) {
            primaryServer.stop();
        }
        if (primaryServerThread != null) {
            try {
                primaryServerThread.join(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (primaryAofManager != null) {
            primaryAofManager.close();
        }
    }

    @Test
    @DisplayName("Primary 서버에 쓰인 데이터가 Replica 서버로 실시간 동기화되어야 한다")
    void replicationSyncTest() throws Exception {
        // Primary 서버로 SET 명령어 전송
        try (
                Socket socket = new Socket("localhost", primaryPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            out.println("SET cluster_key hello_replica");
        }

        TestAwait.until(Duration.ofSeconds(2),
                () -> "hello_replica".equals(replicaStore.get("cluster_key")));

        // Replica의 메모리 저장소에서 Primary가 넘겨준 값이 존재하는지 확인
        assertThat(replicaStore.get("cluster_key")).isEqualTo("hello_replica");
    }

    @Test
    @DisplayName("공백과 한글이 포함된 값도 Replica에 손실 없이 동기화되어야 한다")
    void replicationPreservesSpecialValues() throws Exception {
        primaryStore.set("사용자 이름", "홍 길동");

        TestAwait.until(Duration.ofSeconds(2),
                () -> "홍 길동".equals(replicaStore.get("사용자 이름")));

        assertThat(replicaStore.get("사용자 이름")).isEqualTo("홍 길동");
    }

    @Test
    @DisplayName("SETEX의 만료 시각도 Replica에 전파되어야 한다")
    void replicationPreservesExpiration() throws Exception {
        primaryStore.setEx("session", "active", 300);
        TestAwait.until(Duration.ofSeconds(2),
                () -> "active".equals(replicaStore.get("session")));
        TestAwait.until(Duration.ofSeconds(2),
                () -> replicaStore.get("session") == null);
    }
}
