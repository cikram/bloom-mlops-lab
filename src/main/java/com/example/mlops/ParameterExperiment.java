package com.example.mlops;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ParameterExperiment {

    static final String CSV_HEADER = "run_id,dataset_version,run,m,k,n,seed,fpr_percent,"
            + "lookups_baseline,lookups_bloom,lookups_avoided,accepted,duplicates,baseline_ms,bloom_ms";

    record Config(String name, int m, int k, int n) {}
    record Result(double ms, PipelineMetrics metrics) {}

    public static void main(String[] args) {
        ExperimentConfig cfg = new ExperimentConfig("experiments/experiment-config.properties");

        ResultsWriter writer = new ResultsWriter(cfg.getString("results.file"), CSV_HEADER);
        MlflowTracker tracker = new MlflowTracker(
                cfg.getString("mlflow.trackingUri"), cfg.getString("mlflow.experimentName"));
        String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        // Build the list of runs (A, B, C, D) from the config file
        List<Config> configs = new ArrayList<>();
        for (String name : cfg.getString("runs").split(",")) {
            name = name.trim();
            String[] v = cfg.getString("run." + name).split(",");
            configs.add(new Config(name,
                    Integer.parseInt(v[0].trim()),
                    Integer.parseInt(v[1].trim()),
                    Integer.parseInt(v[2].trim())));
        }

        System.out.println("Dataset version: " + cfg.getString("dataset.version"));
        System.out.println("MLflow tracking: " + (tracker.isEnabled() ? "enabled" : "disabled"));
        System.out.println("Run | m | k | n | FPR% | lookups base | lookups bloom | avoided | accepted | duplicates | base ms | bloom ms");
        for (Config c : configs) {
            runConfig(c, cfg, writer, tracker, runId);
        }
        System.out.println("\nResults appended to " + cfg.getString("results.file") + " (run_id " + runId + ")");
    }

    static void runConfig(Config c, ExperimentConfig cfg, ResultsWriter writer,
                          MlflowTracker tracker, String runId) {
        long seed = cfg.getLong("data.seed");
        long delay = cfg.getLong("store.lookupDelayMicros");
        int repeats = cfg.getInt("repeats");
        int newCandidates = cfg.getInt("fpr.newCandidates");
        double dupFraction = Double.parseDouble(cfg.getString("run.duplicateFraction"));

        // Same stream for every pipeline: n unique IDs + duplicates
        int duplicates = (int) Math.round(c.n() * dupFraction);
        List<DataRecord> stream = Experiment.buildStream(c.n(), duplicates, seed);

        // 1. FPR: fill the filter with the n IDs, then test genuinely new IDs
        ExactIdStore fprStore = new ExactIdStore(0);
        DeduplicationPipeline fprPipeline = new DeduplicationPipeline(c.m(), c.k(), fprStore);
        for (DataRecord r : stream) {
            fprPipeline.process(r);
        }
        int falsePositives = 0;
        for (int i = 0; i < newCandidates; i++) {
            if (fprPipeline.filter().mightContain("unseen-" + i)) {
                falsePositives++;
            }
        }
        double fpr = 100.0 * falsePositives / newCandidates;

        // 2. Warm-up (JIT), results ignored
        timedRun(c, stream, false, delay);
        timedRun(c, stream, true, delay);

        // 3. Repeated timed runs, baseline and bloom-gated
        double baseMs = 0, bloomMs = 0;
        PipelineMetrics baseM = null, bloomM = null;
        for (int rep = 0; rep < repeats; rep++) {
            Result b = timedRun(c, stream, false, delay);
            Result f = timedRun(c, stream, true, delay);
            baseMs += b.ms();
            bloomMs += f.ms();
            baseM = b.metrics();
            bloomM = f.metrics();
        }
        baseMs /= repeats;
        bloomMs /= repeats;

        // Console output
        System.out.printf("%s | %d | %d | %d | %.2f | %d | %d | %d | %d | %d | %.1f | %.1f%n",
                c.name(), c.m(), c.k(), c.n(), fpr,
                baseM.exactChecks, bloomM.exactChecks, bloomM.exactChecksAvoided,
                bloomM.acceptedRecords, bloomM.confirmedDuplicates,
                baseMs, bloomMs);

        // CSV output (Locale.ROOT keeps the decimal separator as a dot)
        writer.append(String.format(Locale.ROOT,
                "%s,%s,%s,%d,%d,%d,%d,%.2f,%d,%d,%d,%d,%d,%.1f,%.1f",
                runId, cfg.getString("dataset.version"), c.name(),
                c.m(), c.k(), c.n(), seed, fpr,
                baseM.exactChecks, bloomM.exactChecks, bloomM.exactChecksAvoided,
                bloomM.acceptedRecords, bloomM.confirmedDuplicates,
                baseMs, bloomMs));

        // MLflow output: parameters = inputs, metrics = results
        tracker.logRun(c.name(),
                Map.of("batch_id", runId,
                        "dataset_version", cfg.getString("dataset.version"),
                        "m", String.valueOf(c.m()),
                        "k", String.valueOf(c.k()),
                        "n", String.valueOf(c.n()),
                        "seed", String.valueOf(seed),
                        "repeats", String.valueOf(repeats),
                        "lookup_delay_us", String.valueOf(delay)),
                Map.of("fpr_percent", fpr,
                        "lookups_baseline", (double) baseM.exactChecks,
                        "lookups_bloom", (double) bloomM.exactChecks,
                        "lookups_avoided", (double) bloomM.exactChecksAvoided,
                        "accepted", (double) bloomM.acceptedRecords,
                        "duplicates", (double) bloomM.confirmedDuplicates,
                        "baseline_ms", baseMs,
                        "bloom_ms", bloomMs));
    }

    static Result timedRun(Config c, List<DataRecord> stream, boolean useFilter, long delayMicros) {
        ExactIdStore store = new ExactIdStore(delayMicros);
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