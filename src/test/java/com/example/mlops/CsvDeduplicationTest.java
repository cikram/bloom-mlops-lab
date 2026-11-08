package com.example.mlops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvDeduplicationTest {

    @Test
    void parseReadsAValidLine() {
        DataRecord r = CsvDeduplication.parse("r001, 0.5, 3");
        assertEquals("r001", r.recordId());
        assertEquals(0.5, r.featureA());
        assertEquals(3, r.featureB());
    }

    @Test
    void parseRejectsWrongColumnCount() {
        assertThrows(IllegalArgumentException.class, () -> CsvDeduplication.parse("r001,0.5"));
    }

    @Test
    void parseRejectsNonNumericValue() {
        assertThrows(IllegalArgumentException.class, () -> CsvDeduplication.parse("r001,abc,3"));
    }

    @Test
    void writesOnlyAcceptedRecordsAndKeepsHeader(@TempDir Path dir) throws IOException {
        Path in = dir.resolve("in.csv");
        Path out = dir.resolve("out").resolve("accepted.csv");
        Files.write(in, List.of(
                "record_id,feature_a,feature_b",
                "r001,0.5,1",
                "r002,0.7,2",
                "r001,0.5,1",      // duplicate
                "bad line",        // invalid
                "r003,0.9,3"));

        CsvDeduplication.Result result = CsvDeduplication.run(in, out, 1000, 4);

        assertEquals(4, result.read());
        assertEquals(3, result.accepted());
        assertEquals(1, result.invalid());
        assertEquals(List.of(
                "record_id,feature_a,feature_b",
                "r001,0.5,1",
                "r002,0.7,2",
                "r003,0.9,3"), Files.readAllLines(out));
    }
}