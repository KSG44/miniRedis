package com.example.miniredis.storage;

public class DataValue {
    private final String value;
    private final Long expireAt; // 만료 시각 (Epoch Milliseconds), null이면 만료 없음

    public DataValue(String value, Long ttlMillis) {
        this(value, ttlMillis, false);
    }

    private DataValue(String value, Long expiration, boolean absoluteExpiration) {
        this.value = value;
        this.expireAt = expiration == null
                ? null
                : absoluteExpiration ? expiration : System.currentTimeMillis() + expiration;
    }

    public static DataValue expiringAt(String value, long expireAt) {
        return new DataValue(value, expireAt, true);
    }

    public String getValue() {
        return value;
    }

    /**
     * 현재 시각 기준으로 데이터가 만료되었는지 확인
     */
    public boolean isExpired() {
        return expireAt != null && System.currentTimeMillis() >= expireAt;
    }

    public Long getExpireAt() {
        return expireAt;
    }
}
