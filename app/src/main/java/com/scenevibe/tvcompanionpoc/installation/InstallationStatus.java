package com.scenevibe.tvcompanionpoc.installation;

/**
 * Closed local result vocabulary. VALIDATED and PREPARED describe pure stages; they never
 * mean durable, armed or ACK-eligible. The other outcomes reserve the architecture's later
 * lifecycle meanings without implementing that lifecycle or carrying arbitrary diagnostics.
 */
public enum InstallationStatus {
    VALIDATED, PREPARED, ARMED, STALE, UNSUPPORTED_CAPABILITY, INVALID_PACKAGE,
    CACHE_FAILED, ARM_FAILED
}
