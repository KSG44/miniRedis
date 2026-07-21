package com.example.miniredis;

import com.example.miniredis.storage.KeyValueStore;

public class Main {

    public static void main(String[] args) {

        KeyValueStore store = new KeyValueStore();

        store.set("name", "Gon");
        store.set("age", "23");

        System.out.println(store.get("name"));
        System.out.println(store.get("age"));

        store.delete("name");

        System.out.println(store.get("name"));
    }
}
