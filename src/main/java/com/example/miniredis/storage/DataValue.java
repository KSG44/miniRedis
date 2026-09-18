package com.example.miniredis.storage;

public class DataValue {
    private final String value;
    private final Long expireAt; // 만료 시각 (Epoch Milliseconds), null이면 만료 없음

    private DataValue(String value, Long expireAt) {
        this.value = value;
        this.expireAt = expireAt;
    }

    public static DataValue persistent(String value) {
        return new DataValue(value, null);
    }

    public static DataValue expiringAt(String value, long expireAt) {
        return new DataValue(value, expireAt);
    }

    public String getValue() {
        return value;
    }

    /**
     * 현재 시각 기준으로 데이터가 만료되었는지 확인
     */
    public boolean isExpired(long currentTimeMillis) {
        return expireAt != null && currentTimeMillis >= expireAt;
    }

    public Long getExpireAt() {
        return expireAt;
    }
}
