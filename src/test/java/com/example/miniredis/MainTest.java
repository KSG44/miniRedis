package com.example.miniredis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MainTest {

    @TempDir
    Path tempDir;

    @Test
    void exitsWhenServerStartupFails() throws Exception {
        try (ServerSocket occupiedPort = new ServerSocket(0)) {
            Process process = new ProcessBuilder(
                    javaExecutable().toString(),
                    "-cp",
                    Path.of("build", "classes", "java", "main").toAbsolutePath().toString(),
                    Main.class.getName(),
                    "--port",
                    Integer.toString(occupiedPort.getLocalPort()),
                    "--aof",
                    tempDir.resolve("startup-failure.aof").toString())
                    .redirectErrorStream(true)
                    .start();

            try {
                assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
                assertThat(process.exitValue()).isNotZero();
            } finally {
                process.destroyForcibly();
            }
        }
    }

    private Path javaExecutable() {
        Path executable = Path.of(System.getProperty("java.home"), "bin", "java");
        if (Files.isExecutable(executable)) return executable;
        return executable.resolveSibling("java.exe");
    }
}
