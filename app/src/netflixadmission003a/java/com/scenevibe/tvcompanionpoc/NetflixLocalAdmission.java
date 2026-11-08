package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Explicit offline qualification entry. Owns admission and durable references only.
 * The existing installation store keeps the verified ZIP in the variant's private
 * namespace. A persisted snapshot does not grant permission to select or play it.
 */
final class NetflixLocalAdmission {
    static final String PACKAGE = BuildConfig.APPLICATION_ID;
    private static final String CODEC = "scenevibe.netflix-local-qualification.v1";
    private static final String ARTIFACT = "trackZip";

    /** A confirmed selection supplied separately from the archive being imported. */
    static final class Selection {
        final String trackId;
        final String programId;
        final long revision;
        final String sha256;

        /** Bind a named local program and immutable revision to an exact ZIP digest. */
        Selection(String trackId, String programId, long revision, String sha256) {
            if (trackId == null || !trackId.matches("[A-Za-z0-9._:-]{1,128}")
                    || programId == null || !programId.matches("[A-Za-z0-9._:-]{1,128}")
                    || revision < 1 || sha256 == null || !sha256.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("LOCAL_REFERENCE_INVALID");
            }
            this.trackId = trackId;
            this.programId = programId;
            this.revision = revision;
            this.sha256 = sha256;
        }
    }

    private final InstallationStore.Backend backend;
    private final InstallationStore store;
    private final StudioMultimediaPackage.Decoder decoder;
    private final Selection selection;

    /** Select one private namespace in this package before touching Android preferences. */
    static NetflixLocalAdmission open(Context context, Selection selection,
            StudioMultimediaPackage.Decoder decoder) throws Exception {
        Context app = context == null ? null : context.getApplicationContext();
        if (!BuildConfig.NETFLIX_LOCAL_QUALIFICATION || app == null
                || !PACKAGE.equals(app.getPackageName())) {
            throw new IllegalArgumentException("QUALIFICATION_PACKAGE_REQUIRED");
        }
        String key = StudioMultimediaPackage.sha256(selection.trackId.getBytes(StandardCharsets.UTF_8));
        return new NetflixLocalAdmission(new AndroidInstallationBackend(app,
                "multimedia003d_cache_" + key.substring(0, 32)), selection, decoder);
    }

    /** Share the bounded durable store and its monitor for validation and publication. */
    private NetflixLocalAdmission(InstallationStore.Backend backend, Selection selection,
            StudioMultimediaPackage.Decoder decoder) {
        this.backend = backend;
        this.store = new InstallationStore(backend);
        this.selection = selection;
        this.decoder = java.util.Objects.requireNonNull(decoder);
    }

    /** Validate the complete snapshot before atomic publication, preserving older valid data on refusal. */
    void admit(byte[] zip) throws Exception {
        InstallRequest request = new InstallRequest(selection.revision, CODEC, Map.of(ARTIFACT, zip));
        synchronized (backend.monitor()) {
            byte[] exact = request.artifact(ARTIFACT);
            verify(exact);
            InstallationSnapshot previous = current();
            if (previous != null) {
                if (previous.revision() > selection.revision) {
                    throw new IllegalArgumentException("LOCAL_REVISION_STALE");
                }
                if (previous.revision() == selection.revision) {
                    if (!java.util.Arrays.equals(previous.canonical().artifact(ARTIFACT), exact)) {
                        throw new IllegalArgumentException("LOCAL_REVISION_CONFLICT");
                    }
                    return;
                }
            }
            if (store.commit(new InstallationSnapshot(request, CODEC)) != InstallationStore.CommitState.COMMITTED) {
                throw new IllegalStateException("LOCAL_CACHE_COMMIT_FAILED");
            }
        }
    }

    /** Reload and verify the selected private bytes before loading the existing runtime. */
    String load(StudioMultimediaRuntime runtime) throws Exception {
        synchronized (backend.monitor()) {
            InstallationSnapshot cached = current();
            if (cached == null || cached.revision() != selection.revision) {
                throw new IllegalArgumentException("LOCAL_CACHE_REFERENCE_MISMATCH");
            }
            StudioMultimediaPackage track = verify(cached.canonical().artifact(ARTIFACT));
            String activation = UUID.randomUUID().toString();
            runtime.loadForSoftwareQualification(track, activation);
            return activation;
        }
    }

    /** Compose the verified track with the existing renderer ports on their serialized owner. */
    NetflixManualClock activateManual(RuntimeFactory factory, java.util.function.LongSupplier elapsed) throws Exception {
        synchronized (backend.monitor()) {
            InstallationSnapshot cached = current();
            if (cached == null || cached.revision() != selection.revision) {
                throw new IllegalArgumentException("LOCAL_CACHE_REFERENCE_MISMATCH");
            }
            StudioMultimediaPackage track = verify(cached.canonical().artifact(ARTIFACT));
            String persisted = backend.get(InstallationStore.SNAPSHOT_KEY);
            java.util.function.BooleanSupplier permitted = () -> sameSnapshot(persisted);
            StudioMultimediaRuntime runtime = factory.create(track, permitted);
            String activation = UUID.randomUUID().toString();
            runtime.loadForSoftwareQualification(track, activation);
            return new NetflixManualClock(runtime, activation, track.trackId, track.revision,
                    track.horizonMs(), elapsed, permitted);
        }
    }

    /** Construct the existing runtime from verified bytes and the admission fence. */
    interface RuntimeFactory {
        /** Supply the existing runtime and its rendering ports. */
        StudioMultimediaRuntime create(StudioMultimediaPackage track,
                java.util.function.BooleanSupplier permitted) throws Exception;
    }

    /** Detect a changed or invalid persisted owner without re-decoding media on every tick. */
    private boolean sameSnapshot(String expected) {
        synchronized (backend.monitor()) {
            try {
                return expected != null && expected.equals(backend.get(InstallationStore.SNAPSHOT_KEY))
                        && backend.get("ackRevision") == null;
            } catch (IllegalStateException invalid) {
                return false;
            }
        }
    }

    /** Refuse foreign storage shapes and acknowledgements without mutating them. */
    private InstallationSnapshot current() {
        InstallationStore.ReadResult value = store.read();
        InstallationSnapshot snapshot = value.snapshot();
        if (value.state() == InstallationStore.ReadState.CORRUPT || value.acknowledgedRevision() != 0
                || (snapshot != null && (!CODEC.equals(snapshot.codecId()) || !CODEC.equals(snapshot.handlerId())
                || snapshot.canonical().artifactCount() != 1 || snapshot.canonical().artifact(ARTIFACT) == null))) {
            throw new IllegalStateException("LOCAL_CACHE_PROFILE_REFUSED");
        }
        return snapshot;
    }

    /** Validate both the detached reference and the existing package contract before exposing a timeline. */
    private StudioMultimediaPackage verify(byte[] bytes) throws Exception {
        if (!selection.sha256.equals(StudioMultimediaPackage.sha256(bytes))) {
            throw new IllegalArgumentException("LOCAL_REFERENCE_DIGEST_MISMATCH");
        }
        StudioMultimediaPackage track = StudioMultimediaPackage.readNetflixQualification(bytes, decoder);
        if (track.revision != selection.revision || !selection.trackId.equals(track.trackId)
                || !selection.programId.equals(track.declaredProgramId())) {
            throw new IllegalArgumentException("LOCAL_REFERENCE_IDENTITY_MISMATCH");
        }
        return track;
    }
}
