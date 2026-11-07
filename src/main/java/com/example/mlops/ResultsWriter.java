package com.example.mlops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ResultsWriter {

    private final Path file;
    private final String header;

    public ResultsWriter(String path, String header) {
        this.file = Path.of(path);
        this.header = header;
    }

    /** Appends one CSV row; creates the file (with its header) if it does not exist yet. */
    public void append(String row) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.notExists(file)) {
                Files.writeString(file, header + System.lineSeparator(), StandardCharsets.UTF_8);
            }
            Files.writeString(file, row + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write results file: " + file, e);
        }
    }
}