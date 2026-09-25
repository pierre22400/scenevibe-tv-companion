package com.scenevibe.tvcompanionpoc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Single-client pairing state. The temporary code exists only in process memory. */
final class PairingPolicy {
    static final long WINDOW_MS = 120_000L;
    static final int MAX_FAILURES = 5;

    interface Clock { long nowMs(); }
    interface Storage {
        String get(String key);
        void put(String key, String value);
        void remove(String key);
    }

    enum Status { NOT_PAIRED, PAIRED, PAIRING_OPEN }
    enum Result { PAIRED, CLOSED, INVALID_CODE, TOO_MANY_ATTEMPTS, INVALID_REQUEST }

    private final Clock clock;
    private final Storage storage;
    private final SecureRandom random;
    private String code;
    private long expiresAt;
    private int failures;

    PairingPolicy(Clock clock, Storage storage, SecureRandom random) {
        this.clock = clock;
        this.storage = storage;
        this.random = random;
    }

    synchronized String start() {
        code = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000));
        expiresAt = clock.nowMs() + WINDOW_MS;
        failures = 0;
        return code;
    }

    synchronized Status status() {
        expire();
        if (code != null) return Status.PAIRING_OPEN;
        return storage.get("token") == null ? Status.NOT_PAIRED : Status.PAIRED;
    }

    synchronized long remainingMs() {
        expire();
        return code == null ? 0L : Math.max(0L, expiresAt - clock.nowMs());
    }

    synchronized String codeForTv() {
        expire();
        return code;
    }

    synchronized void reset() {
        code = null;
        expiresAt = 0L;
        failures = 0;
        storage.remove("token");
    }

    synchronized Result pair(String candidate, String clientName) {
        expire();
        if (code == null) return Result.CLOSED;
        if (clientName == null || clientName.trim().isEmpty() || clientName.length() > 80
                || candidate == null || !candidate.matches("[0-9]{6}")) {
            return Result.INVALID_REQUEST;
        }
        if (!MessageDigest.isEqual(code.getBytes(StandardCharsets.US_ASCII),
                candidate.getBytes(StandardCharsets.US_ASCII))) {
            failures++;
            if (failures >= MAX_FAILURES) {
                code = null;
                return Result.TOO_MANY_ATTEMPTS;
            }
            return Result.INVALID_CODE;
        }
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        storage.put("token", Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes));
        code = null;
        return Result.PAIRED;
    }

    synchronized boolean authorized(String authorization) {
        String token = storage.get("token");
        if (token == null || authorization == null || !authorization.startsWith("Bearer ")) return false;
        String supplied = authorization.substring("Bearer ".length());
        return supplied.length() == token.length() && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII));
    }

    synchronized String tokenForPairAck() { return storage.get("token"); }

    synchronized boolean hasToken() { return storage.get("token") != null; }

    synchronized String deviceId() {
        String id = storage.get("deviceId");
        if (id == null) {
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            storage.put("deviceId", id);
        }
        return id;
    }

    private void expire() {
        if (code != null && clock.nowMs() >= expiresAt) code = null;
    }
}
