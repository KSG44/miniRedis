package com.example.miniredis.storage;

import com.example.miniredis.persistence.AofManager;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public class KeyValueStore {

    private final Map<String, DataValue> store = new ConcurrentHashMap<>();
    private final AofManager aofManager;
    private final LongSupplier currentTimeMillis;

    public KeyValueStore() {
        this(null, System::currentTimeMillis);
    }

    public KeyValueStore(AofManager aofManager) {
        this(aofManager, System::currentTimeMillis);
    }

    public KeyValueStore(AofManager aofManager, LongSupplier currentTimeMillis) {
        this.aofManager = aofManager;
        this.currentTimeMillis = currentTimeMillis;
    }

    // AOF에 기록하지 않는 내부용 SET (AOF 복구 시 사용)
    public void setWithoutAof(String key, String value) {
        store.put(key, DataValue.persistent(value));
    }

    public void setExAtWithoutAof(String key, String value, long expireAt) {
        if (expireAt > currentTimeMillis.getAsLong()) {
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
        store.put(key, DataValue.persistent(value));
        if (aofManager != null) {
            aofManager.appendSet(key, value);
        }
    }

    public void setEx(String key, String value, long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("TTL must be greater than zero");
        }

        long now = currentTimeMillis.getAsLong();
        if (ttlMillis > Long.MAX_VALUE - now) {
            throw new IllegalArgumentException("TTL is out of range");
        }

        DataValue dataValue = DataValue.expiringAt(value, now + ttlMillis);
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

        if (!dataValue.isExpired(currentTimeMillis.getAsLong()))
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
