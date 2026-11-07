package com.example.mlops;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class DataChecksum {

    private DataChecksum() {}

    public static String sha256(List<DataRecord> records) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (DataRecord r : records) {
                String line = r.recordId() + "," + r.featureA() + "," + r.featureB() + "\n";
                digest.update(line.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}