package com.scenevibe.tvcompanionpoc;

/**
 * Local, immutable resource identity/provenance, distinct from persisted Phase B AssetReference.
 * This lot exposes only an APK-bundled fixed fixture; no URL, upload, transport or generic cache
 * is implemented. Future players can extend the resource seam under a separate mandate.
 */
final class LocalMediaResource {
    final String id;
    final String mimeType;
    final String sha256;
    final long bytes;
    final long nominalDurationMs;
    final String provenance;

    /** Construct trusted local metadata without accepting a caller-supplied path or URI. */
    private LocalMediaResource(String id, String mimeType, String sha256, long bytes,
            long nominalDurationMs, String provenance) {
        this.id = id;
        this.mimeType = mimeType;
        this.sha256 = sha256;
        this.bytes = bytes;
        this.nominalDurationMs = nominalDurationMs;
        this.provenance = provenance;
    }

    /** Identify exactly the recovered Spike fixture; this is not new physical qualification. */
    static LocalMediaResource fixture035() {
        return new LocalMediaResource("e5-native-035-fixture", "video/mp4",
                LocalInterludePlan.SHA256, LocalInterludePlan.FILE_BYTES,
                LocalInterludePlan.DURATION_MS,
                "Spike run 37843069020 / software 27e09d529c1a55ab2ef5f861e63060943b634e35");
    }
}
