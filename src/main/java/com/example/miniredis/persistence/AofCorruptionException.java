package com.example.miniredis.persistence;

public class AofCorruptionException extends RuntimeException {

    public AofCorruptionException(int lineNumber) {
        super("AOF " + lineNumber + "번째 줄이 손상되었습니다");
    }
}
