package com.example.mlops;

public record DataRecord(
        String recordId,
        double featureA,
        int featureB
) {}