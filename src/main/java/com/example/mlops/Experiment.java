package com.example.mlops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Experiment {

    public static void main(String[] args) {
        ExperimentConfig cfg = new ExperimentConfig("experiments/experiment-config.properties");

        System.out.println("Dataset version: " + cfg.getString("dataset.version"));
        System.out.println("Filter: m=" + cfg.getInt("bloom.bits")
                + " bits, k=" + cfg.getInt("bloom.hashes")
                + ", seed=" + cfg.getLong("data.seed") + "\n");

        List<DataRecord> stream = buildStream(
                cfg.getInt("data.unique"),
                cfg.getInt("data.duplicates"),
                cfg.getLong("data.seed"));

        run("exact store only (baseline)", stream, false, cfg);
        run("bloom-gated", stream, true, cfg);
        measureFalsePositiveRate(cfg);
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

    static void run(String label, List<DataRecord> stream, boolean useFilter, ExperimentConfig cfg) {
        ExactIdStore store = new ExactIdStore(cfg.getLong("store.lookupDelayMicros"));
        DeduplicationPipeline pipeline = new DeduplicationPipeline(
                cfg.getInt("bloom.bits"), cfg.getInt("bloom.hashes"), store);

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

    static void measureFalsePositiveRate(ExperimentConfig cfg) {
        ExactIdStore store = new ExactIdStore(0);
        DeduplicationPipeline pipeline = new DeduplicationPipeline(
                cfg.getInt("bloom.bits"), cfg.getInt("bloom.hashes"), store);

        // Populate the filter with a known set of IDs
        int unique = cfg.getInt("data.unique");
        for (int i = 0; i < unique; i++) {
            pipeline.process(new DataRecord("id-" + i, i * 0.001, i % 100));
        }

        // Test genuinely new IDs (never inserted)
        int falsePositives = 0;
        int newCandidates = cfg.getInt("fpr.newCandidates");
        for (int i = 0; i < newCandidates; i++) {
            String id = "unseen-" + i;
            if (pipeline.filter().mightContain(id)) {
                falsePositives++;
            }
        }
        double fpr = 100.0 * falsePositives / newCandidates;
        System.out.printf("False-positive rate: %.3f%%%n", fpr);
    }
}