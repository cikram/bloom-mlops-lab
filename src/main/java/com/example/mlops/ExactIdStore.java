package com.example.mlops;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Exact ID store. contains() simulates an expensive point lookup. */
public class ExactIdStore {

    private final Set<String> ids = ConcurrentHashMap.newKeySet();
    private final AtomicLong lookups = new AtomicLong();
    private final long lookupDelayMicros;

    public ExactIdStore(long lookupDelayMicros) {
        this.lookupDelayMicros = lookupDelayMicros;
    }

    /** Expensive read: the operation the Bloom filter is meant to avoid. */
    public boolean contains(String id) {
        lookups.incrementAndGet();
        simulateLatency();
        return ids.contains(id);
    }

    /** Atomic insert-if-absent. Returns false if the ID was already stored. */
    public boolean addIfAbsent(String id) {
        return ids.add(id);
    }

    public Set<String> snapshot() { return Set.copyOf(ids); }

    public long lookups() { return lookups.get(); }

    private void simulateLatency() {
        if (lookupDelayMicros <= 0) return;
        // sleep() is too coarse for microsecond delays, so busy-wait instead.
        long end = System.nanoTime() + lookupDelayMicros * 1_000;
        while (System.nanoTime() < end) { }
    }
}