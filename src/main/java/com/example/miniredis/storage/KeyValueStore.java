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
    public synchronized void setWithoutAof(String key, String value) {
        store.put(key, DataValue.persistent(value));
    }

    public synchronized void setExAtWithoutAof(String key, String value, long expireAt) {
        if (expireAt > currentTimeMillis.getAsLong()) {
            store.put(key, DataValue.expiringAt(value, expireAt));
        } else {
            store.remove(key);
        }
    }

    public synchronized void expireAtWithoutAof(String key, long expireAt) {
        DataValue current = store.get(key);
        if (current == null) return;

        if (expireAt <= currentTimeMillis.getAsLong()) {
            store.remove(key);
        } else {
            store.put(key, DataValue.expiringAt(current.getValue(), expireAt));
        }
    }

    // AOF에 기록하지 않는 내부용 DEL (AOF 복구 시 사용)
    public synchronized void deleteWithoutAof(String key) {
        store.remove(key);
    }

    public synchronized void set(String key, String value) {
        if (aofManager != null) {
            aofManager.appendSet(key, value);
        }
        store.put(key, DataValue.persistent(value));
    }

    public synchronized void setEx(String key, String value, long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("TTL must be greater than zero");
        }

        long now = currentTimeMillis.getAsLong();
        if (ttlMillis > Long.MAX_VALUE - now) {
            throw new IllegalArgumentException("TTL is out of range");
        }

        DataValue dataValue = DataValue.expiringAt(value, now + ttlMillis);
        if (aofManager != null) {
            aofManager.appendSetExAt(key, value, dataValue.getExpireAt());
        }
        store.put(key, dataValue);
    }

    public synchronized String get(String key) {
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
    public synchronized void removeExpired(String key) {

        DataValue dataValue = store.get(key);

        if (dataValue == null)
            return;

        if (!dataValue.isExpired(currentTimeMillis.getAsLong()))
            return;

        if (aofManager != null) {
            aofManager.appendDelete(key);
        }
        store.remove(key, dataValue);
    }

    public synchronized boolean delete(String key) {
        removeExpired(key);
        if (!store.containsKey(key)) return false;

        if (aofManager != null) {
            aofManager.appendDelete(key);
        }
        store.remove(key);
        return true;
    }

    public synchronized boolean exists(String key) {
        return get(key) != null;
    }

    public synchronized boolean expire(String key, long ttlMillis) {
        removeExpired(key);
        DataValue current = store.get(key);
        if (current == null) return false;

        if (ttlMillis <= 0) {
            return delete(key);
        }

        long now = currentTimeMillis.getAsLong();
        if (ttlMillis > Long.MAX_VALUE - now) {
            throw new IllegalArgumentException("TTL is out of range");
        }

        long expireAt = now + ttlMillis;
        if (aofManager != null) {
            aofManager.appendExpireAt(key, expireAt);
        }
        store.put(key, DataValue.expiringAt(current.getValue(), expireAt));
        return true;
    }

    public synchronized long ttlSeconds(String key) {
        removeExpired(key);
        DataValue current = store.get(key);
        if (current == null) return -2;
        if (current.getExpireAt() == null) return -1;

        long remainingMillis = current.getExpireAt() - currentTimeMillis.getAsLong();
        return Math.max(0, remainingMillis / 1_000);
    }

    public synchronized long increment(String key) {
        removeExpired(key);
        DataValue current = store.get(key);
        long currentValue = current == null ? 0 : Long.parseLong(current.getValue());
        long incremented = Math.addExact(currentValue, 1);

        DataValue updated = current != null && current.getExpireAt() != null
                ? DataValue.expiringAt(Long.toString(incremented), current.getExpireAt())
                : DataValue.persistent(Long.toString(incremented));

        if (aofManager != null) {
            if (updated.getExpireAt() == null) {
                aofManager.appendSet(key, updated.getValue());
            } else {
                aofManager.appendSetExAt(key, updated.getValue(), updated.getExpireAt());
            }
        }
        store.put(key, updated);
        return incremented;
    }

    public synchronized int size() {
        removeAllExpired();
        return store.size();
    }

    public synchronized Set<String> keys() {
        removeAllExpired();
        return Set.copyOf(store.keySet());
    }

    private void removeAllExpired() {
        for (String key : store.keySet()) {
            removeExpired(key);
        }
    }
}
