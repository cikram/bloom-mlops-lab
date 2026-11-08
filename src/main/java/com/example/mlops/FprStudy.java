package com.example.mlops;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

public class FprStudy {

    public static void main(String[] args) throws IOException {
        ExperimentConfig cfg = new ExperimentConfig("experiments/experiment-config.properties");
        int n = cfg.getInt("data.unique");
        int candidates = cfg.getInt("fpr.study.candidates");
        int[] bitSizes = parseInts(cfg.getString("fpr.study.bits"));
        int[] hashCounts = parseInts(cfg.getString("fpr.study.hashes"));
        Path output = Path.of(cfg.getString("fpr.study.output"));

        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }

        try (BufferedWriter out = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            out.write("m,k,n,candidates,false_positives,measured_fpr_percent,theoretical_fpr_percent");
            out.newLine();

            for (int k : hashCounts) {
                for (int m : bitSizes) {
                    int falsePositives = countFalsePositives(m, k, n, candidates);
                    double measured = 100.0 * falsePositives / candidates;
                    double theoretical = 100.0 * Math.pow(1 - Math.exp(-(double) k * n / m), k);

                    out.write(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%.4f,%.4f",
                            m, k, n, candidates, falsePositives, measured, theoretical));
                    out.newLine();
                    System.out.printf(Locale.ROOT, "m=%d k=%d measured=%.4f%% theory=%.4f%%%n",
                            m, k, measured, theoretical);
                }
            }
        }
        System.out.println("Written to " + output);

        FprPlot.main(args);
    }

    static int countFalsePositives(int m, int k, int n, int candidates) {
        DeduplicationPipeline pipeline = new DeduplicationPipeline(m, k, new ExactIdStore(0));
        for (int i = 0; i < n; i++) {
            pipeline.process(new DataRecord("id-" + i, i * 0.001, i % 100));
        }
        int falsePositives = 0;
        for (int i = 0; i < candidates; i++) {
            if (pipeline.filter().mightContain("unseen-" + i)) {
                falsePositives++;
            }
        }
        return falsePositives;
    }

    static int[] parseInts(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .mapToInt(Integer::parseInt)
                .toArray();
    }
}