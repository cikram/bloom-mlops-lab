package com.example.mlops;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CsvDeduplication {

    record Result(int read, int accepted, int invalid, PipelineMetrics metrics) {}

    public static void main(String[] args) throws IOException {
        ExperimentConfig cfg = new ExperimentConfig("experiments/experiment-config.properties");
        Path input = Path.of(cfg.getString("csv.input"));
        Path output = Path.of(cfg.getString("csv.output"));

        Result r = run(input, output, cfg.getInt("bloom.bits"), cfg.getInt("bloom.hashes"));

        System.out.printf("Read %d records, accepted %d, duplicates %d, invalid %d%n",
                r.read(), r.accepted(), r.read() - r.accepted(), r.invalid());
        System.out.println("Accepted records written to " + output);
        r.metrics().printSummary();
    }

    static Result run(Path input, Path output, int bits, int hashes) throws IOException {
        DeduplicationPipeline pipeline = new DeduplicationPipeline(bits, hashes, new ExactIdStore(0));

        List<String> lines = Files.readAllLines(input, StandardCharsets.UTF_8);
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }

        int read = 0, accepted = 0, invalid = 0;
        try (BufferedWriter out = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }

                DataRecord record;
                try {
                    record = parse(line);
                } catch (IllegalArgumentException e) {
                    if (i == 0) {                       // first line is not a record: header, copy it as is
                        out.write(line);
                        out.newLine();
                    } else {
                        invalid++;
                        System.err.println("Skipped invalid line " + (i + 1));  // line number only, no data
                    }
                    continue;
                }

                read++;
                long before = pipeline.metrics().acceptedRecords;
                pipeline.process(record);
                if (pipeline.metrics().acceptedRecords > before) {
                    accepted++;
                    out.write(line);                    // original line, format unchanged
                    out.newLine();
                }
            }
        }
        return new Result(read, accepted, invalid, pipeline.metrics());
    }

    static DataRecord parse(String line) {
        String[] parts = line.split(",");
        if (parts.length != 3 || parts[0].isBlank()) {
            throw new IllegalArgumentException("expected 3 columns with a non-blank ID");
        }
        return new DataRecord(
                parts[0].trim(),
                Double.parseDouble(parts[1].trim()),
                Integer.parseInt(parts[2].trim()));
    }
}