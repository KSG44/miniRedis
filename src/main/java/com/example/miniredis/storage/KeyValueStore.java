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

    public void setExAtWithoutAof(String key, String value, long expireAt) {
        if (expireAt > System.currentTimeMillis()) {
            store.put(key, DataValue.expiringAt(value, expireAt));
        } else {
            store.remove(key);
        }
    }

    // AOF에 기록하지 않는 내부용 DEL (AOF 복구 시 사용)
    public void deleteWithoutAof(String key) {
        store.remove(key);
    }

    public void set(String key, String value) {
        store.put(key, new DataValue(value, null));
        if (aofManager != null) {
            aofManager.appendSet(key, value);
        }
    }

    public void setEx(String key, String value, long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("TTL must be greater than zero");
        }

        if (ttlMillis > Long.MAX_VALUE - System.currentTimeMillis()) {
            throw new IllegalArgumentException("TTL is out of range");
        }

        DataValue dataValue = new DataValue(value, ttlMillis);
        store.put(key, dataValue);
        if (aofManager != null) {
            aofManager.appendSetExAt(key, value, dataValue.getExpireAt());
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

        if (store.remove(key, dataValue) && aofManager != null) {
            aofManager.appendDelete(key);
        }
    }

    public boolean delete(String key) {
        boolean removed = store.remove(key) != null;
        if (removed && aofManager != null) {
            aofManager.appendDelete(key);
        }
        return removed;
    }

    public int size() {
        removeAllExpired();
        return store.size();
    }

    public Set<String> keys() {
        removeAllExpired();
        return Set.copyOf(store.keySet());
    }

    private void removeAllExpired() {
        for (String key : store.keySet()) {
            removeExpired(key);
        }
    }
}
