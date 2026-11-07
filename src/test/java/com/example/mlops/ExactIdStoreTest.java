package com.example.mlops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExactIdStoreTest {

    @Test
    void addIfAbsentReturnsFalseForADuplicate() {
        ExactIdStore store = new ExactIdStore(0);
        assertTrue(store.addIfAbsent("a"));
        assertFalse(store.addIfAbsent("a"));
    }

    @Test
    void containsCountsEveryLookup() {
        ExactIdStore store = new ExactIdStore(0);
        store.addIfAbsent("a");
        assertTrue(store.contains("a"));
        assertFalse(store.contains("b"));
        assertEquals(2, store.lookups());
    }
}