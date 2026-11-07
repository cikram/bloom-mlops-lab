package com.example.mlops;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeduplicationPipelineTest {

    private static final List<DataRecord> SAMPLE = List.of(
            new DataRecord("r001", 0.12, 10),
            new DataRecord("r002", 0.45, 20),
            new DataRecord("r003", 0.31, 15),
            new DataRecord("r002", 0.45, 20),
            new DataRecord("r004", 0.88, 12),
            new DataRecord("r005", 0.21, 18),
            new DataRecord("r001", 0.12, 10),
            new DataRecord("r006", 0.67, 14));

    private static final List<Boolean> EXPECTED =
            List.of(true, true, true, false, true, true, false, true);

    @Test
    void bloomGatedPipelineMatchesExpectedDecisions() {
        DeduplicationPipeline p = new DeduplicationPipeline(1000, 4, new ExactIdStore(0));
        List<Boolean> actual = SAMPLE.stream().map(p::process).toList();
        assertEquals(EXPECTED, actual);

        PipelineMetrics m = p.metrics();
        assertEquals(8, m.totalRecords);
        assertEquals(6, m.acceptedRecords);
        assertEquals(2, m.confirmedDuplicates);
        assertEquals(0, m.falsePositives);
        assertEquals(2, m.exactChecks);
        assertEquals(6, m.exactChecksAvoided);
    }

    @Test
    void baselinePipelineMakesTheSameDecisions() {
        DeduplicationPipeline p = new DeduplicationPipeline(1000, 4, new ExactIdStore(0));
        List<Boolean> actual = SAMPLE.stream().map(p::processBaseline).toList();
        assertEquals(EXPECTED, actual);
        assertEquals(8, p.metrics().exactChecks);
    }

    @Test
    void blankIdIsRejected() {
        DeduplicationPipeline p = new DeduplicationPipeline(1000, 4, new ExactIdStore(0));
        assertThrows(IllegalArgumentException.class,
                () -> p.process(new DataRecord(" ", 0.0, 0)));
    }
}