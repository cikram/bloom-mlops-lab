package com.example.mlops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DataChecksumTest {

    @Test
    void sameParametersGiveSameChecksum() {
        var a = Experiment.buildStream(1_000, 100, 42L);
        var b = Experiment.buildStream(1_000, 100, 42L);
        assertEquals(DataChecksum.sha256(a), DataChecksum.sha256(b));
    }

    @Test
    void differentSeedGivesDifferentChecksum() {
        var a = Experiment.buildStream(1_000, 100, 42L);
        var b = Experiment.buildStream(1_000, 100, 7L);
        assertNotEquals(DataChecksum.sha256(a), DataChecksum.sha256(b));
    }
}