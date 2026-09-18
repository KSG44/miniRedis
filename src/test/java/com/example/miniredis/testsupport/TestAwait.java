package com.example.miniredis.testsupport;

import java.time.Duration;
import java.util.function.BooleanSupplier;

public final class TestAwait {

    private static final long POLL_INTERVAL_MILLIS = 10;

    private TestAwait() {
    }

    public static void until(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }

        if (!condition.getAsBoolean()) {
            throw new AssertionError("조건이 " + timeout + " 안에 충족되지 않았습니다");
        }
    }
}
