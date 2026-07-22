package com.example.miniredis.server;

public class ServerStats {

    private static final String VERSION = "0.1";

    private final long startTime;

    public ServerStats() {
        this.startTime = System.currentTimeMillis();
    }

    public String getVersion() {
        return VERSION;
    }

    public long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

}

