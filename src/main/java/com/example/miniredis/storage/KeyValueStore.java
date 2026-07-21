package com.example.miniredis.storage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeyValueStore {

    private final Map<String, DataValue> store = new ConcurrentHashMap<>();

    /**
     * 기본 SET (만료 시간 없음)
     */
    public void set(String key, String value) {
        store.put(key, new DataValue(value, null));
    }

    /**
     * SET with TTL (밀리초 단위 만료 시간 설정)
     */
    public void setEx(String key, String value, long ttlMillis) {
        store.put(key, new DataValue(value, ttlMillis));
    }

    /**
     * GET (Lazy Expiration 적용)
     */
    public String get(String key) {
        DataValue dataValue = store.get(key);

        if (dataValue == null) {
            return null;
        }

        // Lazy Expiration: 조회 시점에 만료되었으면 삭제 후 null 반환
        if (dataValue.isExpired()) {
            store.remove(key);
            return null;
        }

        return dataValue.getValue();
    }

    /**
     * DEL
     */
    public boolean delete(String key) {
        return store.remove(key) != null;
    }

    /**
     * 현재 저장소의 데이터 개수 (만료된 데이터가 포함되어 있을 수 있음)
     */
    public int size() {
        return store.size();
    }
}
