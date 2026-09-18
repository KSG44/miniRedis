package com.example.miniredis;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class MiniRedisClient {

    private static final String HOST = "localhost";
    private static final int DEFAULT_PORT = 6379;

    public static void main(String[] args) {
        int port;
        try {
            port = parsePort(args);
        } catch (IllegalArgumentException e) {
            System.err.println("설정 오류: " + e.getMessage());
            System.err.println("Usage: java ... com.example.miniredis.MiniRedisClient [--port <1-65535>]");
            return;
        }

        try (
                Socket socket = new Socket(HOST, port);
                InputStream serverIn = socket.getInputStream();
                OutputStream serverOut = socket.getOutputStream();
                BufferedReader keyboard = new BufferedReader(
                        new InputStreamReader(System.in, StandardCharsets.UTF_8))
        ) {
            System.out.println("=== Mini Redis Client ===");
            System.out.println("Connected to " + HOST + ":" + port);
            System.out.println("Enter 'exit' to close the client.");
            System.out.println();

            while (true) {
                System.out.print("miniRedis> ");
                String command = keyboard.readLine();
                if (command == null || command.equalsIgnoreCase("exit")) break;

                writeCommand(serverOut, command);
                String response = readResponse(serverIn);
                if (response == null) {
                    System.out.println("서버와 연결이 종료되었습니다.");
                    break;
                }
                System.out.println(response);
            }
        } catch (IOException e) {
            System.out.println("서버에 연결할 수 없거나 통신 중 오류가 발생했습니다.");
            e.printStackTrace();
        }
    }

    static int parsePort(String[] args) {
        if (args.length == 0) return DEFAULT_PORT;
        if (args.length != 2 || !"--port".equals(args[0])) {
            throw new IllegalArgumentException("--port 옵션만 지정할 수 있습니다");
        }

        try {
            int port = Integer.parseInt(args[1]);
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("port는 1에서 65535 사이여야 합니다");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("port는 정수여야 합니다: " + args[1], e);
        }
    }

    static void writeCommand(OutputStream output, String command) throws IOException {
        output.write((command + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    static String readResponse(InputStream input) throws IOException {
        String line = readCrlfLine(input);
        if (line == null) return null;

        if (line.startsWith("$")) {
            if (line.equals("$-1")) return "(nil)";

            int length;
            try {
                length = Integer.parseInt(line.substring(1));
            } catch (NumberFormatException e) {
                throw new IOException("잘못된 Bulk String 길이입니다", e);
            }
            if (length < 0) throw new IOException("잘못된 Bulk String 길이입니다");

            byte[] value = input.readNBytes(length);
            if (value.length != length) throw new IOException("Bulk String 응답이 중간에 종료되었습니다");
            expectCrlf(input);
            return new String(value, StandardCharsets.UTF_8);
        }
        if (line.startsWith("+") || line.startsWith(":")) return line.substring(1);
        return line;
    }

    private static String readCrlfLine(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int current;
        while ((current = input.read()) != -1) {
            if (current == '\r') {
                if (input.read() != '\n') throw new IOException("응답 줄에 CRLF가 필요합니다");
                return buffer.toString(StandardCharsets.UTF_8);
            }
            buffer.write(current);
        }
        if (buffer.size() == 0) return null;
        throw new IOException("응답 줄이 중간에 종료되었습니다");
    }

    private static void expectCrlf(InputStream input) throws IOException {
        if (input.read() != '\r' || input.read() != '\n') {
            throw new IOException("Bulk String 뒤에 CRLF가 필요합니다");
        }
    }
}
