package com.example.miniredis.server;

import java.util.function.LongSupplier;

public class ServerStats {

    private static final String VERSION = "0.1";

    private final long startTime;
    private final LongSupplier currentTimeMillis;

    public ServerStats() {
        this(System::currentTimeMillis);
    }

    ServerStats(LongSupplier currentTimeMillis) {
        this.currentTimeMillis = currentTimeMillis;
        this.startTime = currentTimeMillis.getAsLong();
    }

    public String getVersion() {
        return VERSION;
    }

    public long getUptimeSeconds() {
        return (currentTimeMillis.getAsLong() - startTime) / 1000;
    }

}

