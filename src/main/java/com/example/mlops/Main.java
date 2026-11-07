package com.example.mlops;

public class Main {

    public static void main(String[] args) {
        DeduplicationPipeline pipeline =
                new DeduplicationPipeline(1000, 4, new ExactIdStore(0));

        DataRecord[] records = {
                new DataRecord("r001", 0.12, 10),
                new DataRecord("r002", 0.45, 20),
                new DataRecord("r003", 0.31, 15),
                new DataRecord("r002", 0.45, 20),
                new DataRecord("r004", 0.88, 12),
                new DataRecord("r005", 0.21, 18),
                new DataRecord("r001", 0.12, 10),
                new DataRecord("r006", 0.67, 14)
        };

        for (DataRecord record : records) {
            boolean accepted = pipeline.process(record);
            System.out.println(record.recordId() + " accepted=" + accepted);
        }

        pipeline.metrics().printSummary();
    }
}