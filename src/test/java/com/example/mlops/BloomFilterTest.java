package com.example.mlops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BloomFilterTest {

    @Test
    void emptyFilterSaysDefinitelyAbsent() {
        BloomFilter f = new BloomFilter(1000, 4);
        assertFalse(f.mightContain("r001"));
    }

    @Test
    void neverProducesFalseNegatives() {
        BloomFilter f = new BloomFilter(10_000, 5);
        for (int i = 0; i < 1000; i++) {
            f.add("id-" + i);
        }
        for (int i = 0; i < 1000; i++) {
            assertTrue(f.mightContain("id-" + i));
        }
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(0, 3));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(100, 0));
    }

    @Test
    void falsePositiveRateStaysNearTheTarget() {
        BloomFilter f = new BloomFilter(862_700, 7); // sized for ~1% with 90,000 IDs
        for (int i = 0; i < 90_000; i++) {
            f.add("id-" + i);
        }
        int fp = 0;
        for (int i = 0; i < 10_000; i++) {
            if (f.mightContain("unseen-" + i)) {
                fp++;
            }
        }
        double rate = 100.0 * fp / 10_000;
        assertTrue(rate < 2.0, "False-positive rate too high: " + rate + "%");
    }
}