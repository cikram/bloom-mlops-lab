package com.example.mlops;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkSmokeTest {

    @Test
    void bloomGatedMatchesBaselineWithFarFewerLookups() {
        List<DataRecord> stream = Experiment.buildStream(10_000, 1_000, 42L);

        DeduplicationPipeline baseline = new DeduplicationPipeline(95_851, 7, new ExactIdStore(0));
        DeduplicationPipeline bloom = new DeduplicationPipeline(95_851, 7, new ExactIdStore(0));

        List<Boolean> baselineDecisions = new ArrayList<>();
        long t0 = System.nanoTime();
        for (DataRecord r : stream) {
            baselineDecisions.add(baseline.processBaseline(r));
        }
        double baselineMs = (System.nanoTime() - t0) / 1e6;

        List<Boolean> bloomDecisions = new ArrayList<>();
        t0 = System.nanoTime();
        for (DataRecord r : stream) {
            bloomDecisions.add(bloom.process(r));
        }
        double bloomMs = (System.nanoTime() - t0) / 1e6;

        // Correctness: identical decisions
        assertEquals(baselineDecisions, bloomDecisions);

        // Benchmark: at least 80% fewer exact-store lookups
        long baseLookups = baseline.metrics().exactChecks;
        long bloomLookups = bloom.metrics().exactChecks;
        assertTrue(bloomLookups < baseLookups / 5,
                "Expected far fewer lookups, got " + bloomLookups + " vs " + baseLookups);

        System.out.printf("Benchmark: lookups %d -> %d, time %.1f ms -> %.1f ms%n",
                baseLookups, bloomLookups, baselineMs, bloomMs);
    }
}