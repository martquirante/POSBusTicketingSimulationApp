package com.buscorp.employee.core.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class AuditChainHasher {

    private AuditChainHasher() {
    }

    public static String hash(String previousHash, String canonicalPayloadJson) {
        String input = (previousHash == null ? "" : previousHash) + (canonicalPayloadJson == null ? "{}" : canonicalPayloadJson);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for Bus Corp. audit chaining.", e);
        }
    }
}
