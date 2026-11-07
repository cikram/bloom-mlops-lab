package com.example.mlops;

public class BloomFilter {

    private final boolean[] bits;
    private final int numberOfHashes;

    public BloomFilter(int size, int numberOfHashes) {
        if (size <= 0 || numberOfHashes <= 0) {
            throw new IllegalArgumentException(
                    "Size and hash count must be positive");
        }
        this.bits = new boolean[size];
        this.numberOfHashes = numberOfHashes;
    }

    // Position of the i-th bit for an item (double hashing)
    private int index(String item, int i) {
        int h1 = item.hashCode();
        int h2 = Integer.rotateLeft(h1 ^ 0x5bd1e995, 13);
        if (h2 == 0) h2 = 1;
        long combined = (long) h1 + (long) i * h2;
        return (int) Math.floorMod(combined, (long) bits.length);
    }

    // Set the k bits of the item to true
    public void add(String item) {
        for (int i = 0; i < numberOfHashes; i++) {
            bits[index(item, i)] = true;
        }
    }

    // false = definitely absent, true = possibly present
    public boolean mightContain(String item) {
        for (int i = 0; i < numberOfHashes; i++) {
            if (!bits[index(item, i)]) return false;
        }
        return true;
    }
}