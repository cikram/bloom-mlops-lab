package com.example.mlops;

public class DeduplicationPipeline {

    private final BloomFilter filter;
    private final ExactIdStore exactStore;
    private final PipelineMetrics metrics = new PipelineMetrics();

    public DeduplicationPipeline(int bitArraySize, int hashCount, ExactIdStore exactStore) {
        this.filter = new BloomFilter(bitArraySize, hashCount);
        this.exactStore = exactStore;
    }

    private static String requireId(DataRecord record) {
        String id = record.recordId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Record ID is required");
        }
        return id;
    }

    /** Bloom-gated path: the exact store is read only when the filter says "possibly present". */
    public boolean process(DataRecord record) {
        metrics.totalRecords++;
        String id = requireId(record);

        if (filter.mightContain(id)) {
            // Possibly present: verify with the exact store first.
            metrics.possibleDuplicates++;
            metrics.exactChecks++;
            if (exactStore.contains(id)) {
                metrics.confirmedDuplicates++;
                return false;
            }
            metrics.falsePositives++; // filter said "maybe", exact store said "no"
        } else {
            // Definitely absent: the ID was never accepted, so skip the exact lookup.
            metrics.exactChecksAvoided++;
        }

        // Final arbiter: atomic insert-if-absent (also covers races).
        if (!exactStore.addIfAbsent(id)) {
            metrics.confirmedDuplicates++;
            return false;
        }
        filter.add(id);
        metrics.acceptedRecords++;
        return true;
    }

    /** Baseline for comparison: exact store only, no filter. */
    public boolean processBaseline(DataRecord record) {
        metrics.totalRecords++;
        String id = requireId(record);

        metrics.exactChecks++;
        if (exactStore.contains(id)) {
            metrics.confirmedDuplicates++;
            return false;
        }
        if (!exactStore.addIfAbsent(id)) {
            metrics.confirmedDuplicates++;
            return false;
        }
        metrics.acceptedRecords++;
        return true;
    }

    /** Call at startup or after a restart: the exact store is the source of truth. */
    public void rebuildFilterFromExactStore() {
        for (String id : exactStore.snapshot()) {
            filter.add(id);
        }
    }

    public BloomFilter filter() { return filter; }

    public PipelineMetrics metrics() { return metrics; }
}