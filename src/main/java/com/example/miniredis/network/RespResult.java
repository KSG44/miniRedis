package com.example.miniredis.network;

public record RespResult(String response, boolean closeConnection) {

    public static RespResult keepOpen(String response) {
        return new RespResult(response, false);
    }

    public static RespResult protocolError(String message) {
        return new RespResult("-ERR Protocol error: " + message + "\r\n", true);
    }
}
