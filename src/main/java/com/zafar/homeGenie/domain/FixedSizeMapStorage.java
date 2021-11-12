package com.zafar.homeGenie.domain;


import java.util.Map;
import java.util.Set;

public class FixedSizeMapStorage<K, V, T extends Map<K, V>> {
    private T storage;
    private int size;

    public FixedSizeMapStorage(Map<K, V> storage, int size) {
        this.storage = (T) storage;
        this.size = size;
    }

    public void put(K key, V value) {
        if (storage.entrySet().size() > size) {
            //remove earliest entry
            Map.Entry<K, V> entry = storage.entrySet().stream().reduce((f,s)->s).get();
            storage.remove(entry.getKey());
        }
        storage.put(key, value);
    }

    public Set<Map.Entry<K,V>> getEntrySet() {
        return storage.entrySet();
    }

    public void clear() {
        storage.clear();
    }
}
