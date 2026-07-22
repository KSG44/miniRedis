package com.example.miniredis.storage;

import com.example.miniredis.persistence.AofManager;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KeyValueStore {

    private final Map<String, DataValue> store = new ConcurrentHashMap<>();
    private final AofManager aofManager;

    public KeyValueStore() {
        this.aofManager = null;
    }

    public KeyValueStore(AofManager aofManager) {
        this.aofManager = aofManager;
    }

    // AOF에 기록하지 않는 내부용 SET (AOF 복구 시 사용)
    public void setWithoutAof(String key, String value) {
        store.put(key, new DataValue(value, null));
    }

    // AOF에 기록하지 않는 내부용 DEL (AOF 복구 시 사용)
    public void deleteWithoutAof(String key) {
        store.remove(key);
    }

    public void set(String key, String value) {
        store.put(key, new DataValue(value, null));
        if (aofManager != null) {
            aofManager.append("SET " + key + " " + value);
        }
    }

    public void setEx(String key, String value, long ttlMillis) {
        store.put(key, new DataValue(value, ttlMillis));
        if (aofManager != null) {
            aofManager.append("SET " + key + " " + value);
        }
    }

    public String get(String key) {
        DataValue dataValue = store.get(key);

        if (dataValue == null) {
            return null;
        }

        removeExpired(key);

        dataValue = store.get(key);

        if(dataValue == null){
            return null;
        }

        return dataValue.getValue();
    }
    public void removeExpired(String key) {

        DataValue dataValue = store.get(key);

        if (dataValue == null)
            return;

        if (!dataValue.isExpired())
            return;

        store.remove(key);

        if (aofManager != null) {
            aofManager.append("DEL " + key);
        }
    }

    public boolean delete(String key) {
        boolean removed = store.remove(key) != null;
        if (removed && aofManager != null) {
            aofManager.append("DEL " + key);
        }
        return removed;
    }

    public int size() {
        return store.size();
    }
    public Set<String> keys() {
        return store.keySet();
    }
}
