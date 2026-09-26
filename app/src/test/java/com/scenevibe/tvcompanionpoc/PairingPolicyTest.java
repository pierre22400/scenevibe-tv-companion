package com.scenevibe.tvcompanionpoc;

import org.junit.Test;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** Exercises expiration, failed attempts, replacement and revocation without Android state. */
public class PairingPolicyTest {
    private final long[] now = {1000L};
    private final Map<String, String> values = new HashMap<>();
    private final PairingPolicy pairing = new PairingPolicy(() -> now[0], new PairingPolicy.Storage() {
        @Override public String get(String key) { return values.get(key); }
        @Override public void put(String key, String value) { values.put(key, value); }
        @Override public void remove(String key) { values.remove(key); }
    }, new SecureRandom());

    @Test public void pairingExpiresAndNeedsExplicitRestart() {
        String code = pairing.start();
        assertTrue(code.matches("[0-9]{6}"));
        assertEquals(120_000L, pairing.remainingMs());
        now[0] += 119_999L;
        assertEquals(PairingPolicy.Status.PAIRING_OPEN, pairing.status());
        now[0]++;
        assertEquals(PairingPolicy.Status.NOT_PAIRED, pairing.status());
        assertEquals(PairingPolicy.Result.CLOSED, pairing.pair(code, "sender"));
    }

    @Test public void fiveWrongAttemptsCloseWindow() {
        String code = pairing.start();
        String wrong = code.equals("000000") ? "000001" : "000000";
        for (int n = 0; n < 4; n++) {
            assertEquals(PairingPolicy.Result.INVALID_CODE, pairing.pair(wrong, "sender"));
        }
        assertEquals(PairingPolicy.Result.TOO_MANY_ATTEMPTS, pairing.pair(wrong, "sender"));
        assertEquals(PairingPolicy.Result.CLOSED, pairing.pair(code, "sender"));
        assertEquals(PairingPolicy.Status.NOT_PAIRED, pairing.status());
    }

    @Test public void tokenIsIndependentAndRevokedOnReplacementAndReset() {
        String firstCode = pairing.start();
        assertEquals(PairingPolicy.Result.INVALID_REQUEST, pairing.pair("wrong", "sender"));
        assertEquals(PairingPolicy.Result.PAIRED, pairing.pair(firstCode, "sender"));
        String firstToken = pairing.tokenForPairAck();
        String deviceId = pairing.deviceId();
        assertEquals(43, firstToken.length());
        assertFalse(firstToken.equals(firstCode));
        assertTrue(pairing.authorized("Bearer " + firstToken));
        assertFalse(pairing.authorized("Basic " + firstToken));
        assertFalse(pairing.authorized("Bearer " + firstCode));
        String secondCode = pairing.start();
        assertEquals(PairingPolicy.Result.PAIRED, pairing.pair(secondCode, "sender"));
        assertFalse(pairing.authorized("Bearer " + firstToken));
        assertEquals(deviceId, pairing.deviceId());
        pairing.reset();
        assertFalse(pairing.authorized("Bearer " + pairing.tokenForPairAck()));
        assertEquals(PairingPolicy.Status.NOT_PAIRED, pairing.status());
    }
}
