package com.example.mlops;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ExperimentConfig {

    private final Properties props = new Properties();

    public ExperimentConfig(String path) {
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read config file: " + path, e);
        }
    }

    public String getString(String key) {
        String value = props.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing config key: " + key);
        }
        return value.trim();
    }

    public int getInt(String key) {
        return Integer.parseInt(getString(key));
    }

    public long getLong(String key) {
        return Long.parseLong(getString(key));
    }
}