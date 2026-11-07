package com.example.mlops;

public class PipelineMetrics {
    public long totalRecords;
    public long possibleDuplicates;
    public long confirmedDuplicates;
    public long falsePositives;
    public long acceptedRecords;
    public long exactChecks;
    public long exactChecksAvoided;

    public void printSummary() {
        System.out.println("Total records: " + totalRecords);
        System.out.println("Possible duplicates (filter says maybe): " + possibleDuplicates);
        System.out.println("Confirmed duplicates: " + confirmedDuplicates);
        System.out.println("False positives (maybe, but new): " + falsePositives);
        System.out.println("Accepted records: " + acceptedRecords);
        System.out.println("Exact-store lookups: " + exactChecks);
        System.out.println("Exact-store lookups avoided: " + exactChecksAvoided);
    }
}