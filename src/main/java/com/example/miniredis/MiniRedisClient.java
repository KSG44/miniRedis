package com.example.miniredis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class MiniRedisClient {

    private static final String HOST = "localhost";
    private static final int PORT = 6379;

    public static void main(String[] args) {

        try (
                Socket socket = new Socket(HOST, PORT);
                BufferedReader serverIn =
                        new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter serverOut =
                        new PrintWriter(socket.getOutputStream(), true);
                BufferedReader keyboard =
                        new BufferedReader(new InputStreamReader(System.in))
        ) {

            System.out.println("=== Mini Redis Client ===");
            System.out.println("Connected to " + HOST + ":" + PORT);
            System.out.println("Enter 'exit' to close the client.");
            System.out.println();

            while (true) {

                System.out.print("miniRedis> ");
                String command = keyboard.readLine();

                if (command == null) break;

                if (command.equalsIgnoreCase("exit")) {
                    break;
                }

                serverOut.println(command);

                String line = serverIn.readLine();

                if (line == null) {
                    System.out.println("서버와 연결이 종료되었습니다.");
                    break;
                }

                // Bulk String
                if (line.startsWith("$")) {

                    if (line.equals("$-1")) {
                        System.out.println("(nil)");
                    } else {

                        int length = Integer.parseInt(line.substring(1));

                        char[] buffer = new char[length];
                        serverIn.read(buffer, 0, length);

                        System.out.println(new String(buffer));

                        // 마지막 CRLF 제거
                        serverIn.readLine();
                    }
                }
                // Simple String
                else if (line.startsWith("+")) {
                    System.out.println(line.substring(1));
                }
                // Error
                else if (line.startsWith("-")) {
                    System.out.println(line);
                }
                // Integer
                else if (line.startsWith(":")) {
                    System.out.println(line.substring(1));
                }
                else {
                    System.out.println(line);
                }

            }

        } catch (IOException e) {
            System.out.println("서버에 연결할 수 없습니다.");
            e.printStackTrace();
        }
    }
}
