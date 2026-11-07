package com.example.mlops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Experiment_V1 {

    public static void main(String[] args) {
        List<DataRecord> stream = buildStream(90_000, 10_000, 42L);
        run("exact store only (baseline)", stream, false);
        run("bloom-gated", stream, true);
    }

    static List<DataRecord> buildStream(int unique, int duplicates, long seed) {
        List<DataRecord> list = new ArrayList<>();
        for (int i = 0; i < unique; i++) {
            list.add(new DataRecord("id-" + i, i * 0.001, i % 100));
        }
        Random rnd = new Random(seed);
        for (int d = 0; d < duplicates; d++) {
            list.add(list.get(rnd.nextInt(unique))); // repeat of an existing ID
        }
        Collections.shuffle(list, rnd);
        return list;
    }

    static void run(String label, List<DataRecord> stream, boolean useFilter) {
        ExactIdStore store = new ExactIdStore(50); // 50 microseconds per lookup
        DeduplicationPipeline pipeline =
                new DeduplicationPipeline(862_700, 7, store); // about 1% FPR for 90,000 IDs

        long start = System.nanoTime();
        for (DataRecord record : stream) {
            if (useFilter) pipeline.process(record);
            else pipeline.processBaseline(record);
        }
        double ms = (System.nanoTime() - start) / 1e6;

        System.out.println("== " + label + " ==");
        pipeline.metrics().printSummary();
        System.out.printf("Elapsed: %.0f ms%n%n", ms);
    }

    static void measureFalsePositiveRate() {
        ExactIdStore store = new ExactIdStore(0);
        DeduplicationPipeline pipeline = new DeduplicationPipeline(862_700, 7, store);

        // 1. Populate the filter with a known set of 90,000 IDs
        for (int i = 0; i < 90_000; i++) {
            pipeline.process(new DataRecord("id-" + i, i * 0.001, i % 100));
        }

        // 2. Test 10,000 genuinely new IDs (never inserted)
        int falsePositives = 0;
        int newCandidates = 10_000;
        for (int i = 0; i < newCandidates; i++) {
            String id = "unseen-" + i;
            // This assumes these IDs have not been inserted.
            if (pipeline.filter().mightContain(id)) {
                falsePositives++;
            }
        }
        double fpr = 100.0 * falsePositives / newCandidates;
        System.out.printf("False-positive rate: %.3f%%%n", fpr);
    }
}