package com.example.mlops;

import java.util.List;

public class ParameterExperiment_V1 {

    static final int REPEATS = 10;              // timing repetitions per configuration
    static final int NEW_CANDIDATES = 10_000;   // unseen IDs used to measure the FPR
    static final long LOOKUP_DELAY_MICROS = 50; // simulated cost of one exact lookup

    record Config(String name, int m, int k, int n) {}          // one experiment setup
    record Result(double ms, PipelineMetrics metrics) {}        // one timed run

    public static void main(String[] args) {
        Config[] configs = {
                new Config("A", 1_000, 3, 100),
                new Config("B", 2_000, 3, 100),
                new Config("C", 1_000, 5, 100),
                new Config("D", 1_000, 3, 300)
        };

        System.out.println("Run | m | k | n | FPR% | lookups base | lookups bloom | avoided | accepted | duplicates | base ms | bloom ms");
        for (Config c : configs) {
            runConfig(c);
        }
    }

    static void runConfig(Config c) {
        // Same stream for every pipeline: n unique IDs + 10% duplicates
        List<DataRecord> stream = Experiment_V1.buildStream(c.n(), c.n() / 10, 42L);

        // 1. FPR: fill the filter with the n IDs, then test genuinely new IDs
        ExactIdStore fprStore = new ExactIdStore(0);
        DeduplicationPipeline fprPipeline = new DeduplicationPipeline(c.m(), c.k(), fprStore);
        for (DataRecord r : stream) {
            fprPipeline.process(r);
        }
        int falsePositives = 0;
        for (int i = 0; i < NEW_CANDIDATES; i++) {
            if (fprPipeline.filter().mightContain("unseen-" + i)) {
                falsePositives++;
            }
        }
        double fpr = 100.0 * falsePositives / NEW_CANDIDATES;

        // 2. Warm-up (JIT), results ignored
        timedRun(c, stream, false);
        timedRun(c, stream, true);

        // 3. Repeated timed runs, baseline and bloom-gated
        double baseMs = 0, bloomMs = 0;
        PipelineMetrics baseM = null, bloomM = null;
        for (int rep = 0; rep < REPEATS; rep++) {
            Result b = timedRun(c, stream, false);
            Result f = timedRun(c, stream, true);
            baseMs += b.ms();
            bloomMs += f.ms();
            baseM = b.metrics();
            bloomM = f.metrics();
        }
        baseMs /= REPEATS;
        bloomMs /= REPEATS;

        System.out.printf("%s | %d | %d | %d | %.2f | %d | %d | %d | %d | %d | %.1f | %.1f%n",
                c.name(), c.m(), c.k(), c.n(), fpr,
                baseM.exactChecks, bloomM.exactChecks, bloomM.exactChecksAvoided,
                bloomM.acceptedRecords, bloomM.confirmedDuplicates,
                baseMs, bloomMs);
    }

    static Result timedRun(Config c, List<DataRecord> stream, boolean useFilter) {
        ExactIdStore store = new ExactIdStore(LOOKUP_DELAY_MICROS);
        DeduplicationPipeline pipeline = new DeduplicationPipeline(c.m(), c.k(), store);

        long start = System.nanoTime();
        for (DataRecord r : stream) {
            if (useFilter) pipeline.process(r);
            else pipeline.processBaseline(r);
        }
        double ms = (System.nanoTime() - start) / 1e6;
        return new Result(ms, pipeline.metrics());
    }
}