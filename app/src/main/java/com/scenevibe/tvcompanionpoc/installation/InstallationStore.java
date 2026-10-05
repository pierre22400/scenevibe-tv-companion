package com.scenevibe.tvcompanionpoc.installation;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Own durable representation only. Generic reads are bounded and non-mutating; a present
 * generic marker is authoritative even when corrupt, so stale historical bytes never win.
 * Historical fields remain intact as compatibility residue. Only the temporary raw facade
 * is used by current callers; no generic read/commit/restore is wired into their runtime.
 */
public final class InstallationStore {
    public static final String FORMAT_VERSION="scenevibe.os.installation-store.v1";
    public static final String SNAPSHOT_KEY="installationSnapshot";
    // Reuse qualified internal shape ids as fixed compatibility handler identities.
    public static final String COMPAT_TRACK_HANDLER_ID=TvCapabilities.CODEC_TRACK;
    public static final String COMPAT_OVERLAY_HANDLER_ID=TvCapabilities.CODEC_TRACK_OVERLAY;

    /** Persistence-only seam; implementations must share a monitor and preserve state on false commit. */
    public interface Backend {
        /** Coordinate all store instances referring to the same physical preference file. */
        Object monitor();
        /** Read a known string key, distinguishing malformed typed storage by an exception. */
        String get(String key);
        /** Atomically commit this fixed-key batch; false must leave the prior readable durable view. */
        boolean commit(Map<String,String> values,Set<String> removed,boolean clear);
    }
    /** Closed read outcome has no content-bearing diagnostics. */
    public enum ReadState { EMPTY, SNAPSHOT, CORRUPT }
    /** Observational fault classes only; they never authorize fallback, repair, ARM or acknowledgement. */
    public enum ReadFailure {
        NONE, GENERIC_INVALID, HISTORICAL_INVALID, ACK_INVALID,
        ACK_AHEAD_GENERIC, ACK_AHEAD_WITHOUT_GENERIC, BACKEND_READ_FAILED
    }
    /** COMMITTED is durability only, never an armed/ACK-eligible runtime result. */
    public enum CommitState { COMMITTED, INVALID_SNAPSHOT, CACHE_FAILED }

    /** Immutable outer read result keeps ACK distinct from the opaque installation snapshot. */
    public static final class ReadResult {
        private final ReadState state;
        private final InstallationSnapshot snapshot;
        private final long acknowledged;
        private final ReadFailure failure;
        /** Construct a closed result without retaining an exception or raw representation. */
        private ReadResult(ReadState state,InstallationSnapshot snapshot,long acknowledged,ReadFailure failure) {
            this.state=state;this.snapshot=snapshot;this.acknowledged=acknowledged;this.failure=failure;
        }
        /** Return clean empty, complete representation, or corruption. */
        public ReadState state() {return state;}
        /** Return an immutable candidate only for a complete valid durable representation. */
        public InstallationSnapshot snapshot() {return snapshot;}
        /** Return confirmed durable ACK, never a value synthesized from commit success. */
        public long acknowledgedRevision() {return acknowledged;}
        /** Describe the failed read stage without retaining content, an exception or a raw preference value. */
        public ReadFailure failure() {return failure;}
    }

    private final Backend backend;
    /** Accept a persistence backend only, without retaining any runtime or product object. */
    public InstallationStore(Backend backend) {
        if (backend==null||backend.monitor()==null) throw new IllegalArgumentException("Missing installation backend");
        this.backend=backend;
    }

    /** Read one coherent representation without a write, migration, parser, clock or activation. */
    public ReadResult read() {
        synchronized (backend.monitor()) {
            ReadFailure failure=ReadFailure.BACKEND_READ_FAILED;
            try {
                String generic=readValue(SNAPSHOT_KEY);
                failure=generic==null?ReadFailure.HISTORICAL_INVALID:ReadFailure.GENERIC_INVALID;
                InstallationSnapshot snapshot=generic==null?readHistorical():InstallationSnapshotCodec.decode(generic);
                failure=ReadFailure.ACK_INVALID;
                long ack=acknowledgedValue(readValue("ackRevision"));
                if (ack>(snapshot==null?0:snapshot.revision()))
                    return corrupt(generic==null?ReadFailure.ACK_AHEAD_WITHOUT_GENERIC:ReadFailure.ACK_AHEAD_GENERIC);
                return new ReadResult(snapshot==null?ReadState.EMPTY:ReadState.SNAPSHOT,snapshot,ack,ReadFailure.NONE);
            } catch (BackendReadFailure unavailable) {return corrupt(ReadFailure.BACKEND_READ_FAILED);}
            catch (RuntimeException invalid) {return corrupt(failure);}
        }
    }

    /** Atomically publish one opaque candidate; ACK and historical fields are never changed here. */
    public CommitState commit(InstallationSnapshot snapshot) {
        if (snapshot==null) return CommitState.INVALID_SNAPSHOT;
        synchronized (backend.monitor()) {
            try {
                if (acknowledgedValue()>snapshot.revision()) return CommitState.INVALID_SNAPSHOT;
                String encoded=InstallationSnapshotCodec.encode(snapshot);
                return backend.commit(Collections.singletonMap(SNAPSHOT_KEY,encoded),Collections.emptySet(),false)
                        ?CommitState.COMMITTED:CommitState.CACHE_FAILED;
            } catch (IllegalArgumentException invalid) {return CommitState.INVALID_SNAPSHOT;}
            catch (RuntimeException failed) {return CommitState.CACHE_FAILED;}
        }
    }

    /** Persist a server-confirmed ACK only for the complete current durable revision; never send it. */
    public boolean markAcknowledged(long revision) {
        synchronized (backend.monitor()) {
            ReadResult current=read();
            if (revision<1||current.state()!=ReadState.SNAPSHOT||current.snapshot().revision()!=revision) return false;
            return writeAcknowledgement(revision);
        }
    }

    /** Keep the existing raw get seam limited to the four historical fields. */
    public String historicalValue(String key) {
        if (!historicalKey(key)) throw new IllegalArgumentException("Invalid compatibility key");
        synchronized (backend.monitor()) {
            if (!historicalAuthority()) return null;
            return backend.get(key);
        }
    }

    /** Commit the original historical tuple in one batch, retiring a stale manifest in that batch. */
    public boolean saveHistorical(long revision,String runtime,String manifest) {
        if (revision<1||runtime==null||runtime.isEmpty()||runtime.length()>400_000
                ||(manifest!=null&&(manifest.isEmpty()||manifest.length()>800_000))) return false;
        Map<String,String> values=new TreeMap<>();
        values.put("revision",Long.toString(revision));values.put("runtime",runtime);
        if (manifest!=null) values.put("manifest",manifest);
        synchronized (backend.monitor()) {
            if (!historicalAuthority()) return false;
            return backend.commit(values,manifest==null?Collections.singleton("manifest"):Collections.emptySet(),false);
        }
    }

    /** Preserve the raw compatibility ACK seam, with the exact revision guard owned by durable storage. */
    public boolean saveHistoricalAcknowledgement(long revision) {
        synchronized (backend.monitor()) {
            try {
                if (!historicalAuthority()) return false;
                if (revision<1||revision!=InstallationSnapshotCodec.decimal(backend.get("revision"),false)) return false;
                return writeAcknowledgement(revision);
            } catch (RuntimeException invalid) {return false;}
        }
    }

    /** Retain the historical explicit cache-reset operation without touching any other preference file. */
    public void clearHistorical() {
        clearAll();
    }

    /** Atomically clear this installation file, including generic state, residue and ACK; never reset identity. */
    public boolean clearAll() {
        synchronized (backend.monitor()) {
            try {return backend.commit(Collections.emptyMap(),Collections.emptySet(),true);}
            catch (RuntimeException failed) {return false;}
        }
    }

    /** Infer only artifact-key shape; semantic product/coherence validation remains outside the store. */
    private InstallationSnapshot readHistorical() {
        String raw=readValue("revision"),runtime=readValue("runtime"),manifest=readValue("manifest");
        if (raw==null&&runtime==null&&manifest==null) return null;
        long revision=InstallationSnapshotCodec.decimal(raw,false);
        Map<String,byte[]> artifacts=new TreeMap<>();
        artifacts.put("runtime",historicalBytes(runtime,400_000));
        if (manifest!=null) artifacts.put("manifest",historicalBytes(manifest,800_000));
        String codec=manifest==null?TvCapabilities.CODEC_TRACK:TvCapabilities.CODEC_TRACK_OVERLAY;
        String handler=manifest==null?COMPAT_TRACK_HANDLER_ID:COMPAT_OVERLAY_HANDLER_ID;
        return new InstallationSnapshot(new InstallRequest(revision,codec,artifacts),handler);
    }

    /** Check character/byte bounds and reject unpaired surrogates rather than silently replacing them. */
    private static byte[] historicalBytes(String value,int maximumUtf16) {
        if (value==null||value.isEmpty()||value.length()>maximumUtf16)
            throw new IllegalArgumentException("Invalid compatibility artifact");
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        if (!new String(bytes,StandardCharsets.UTF_8).equals(value))
            throw new IllegalArgumentException("Invalid compatibility encoding");
        return bytes;
    }

    /** Treat absent ACK as zero; malformed numbers remain explicit corruption for generic reads. */
    private long acknowledgedValue() {
        return acknowledgedValue(backend.get("ackRevision"));
    }
    /** Share the identical strict ACK parser between writes and the staged observational read. */
    private static long acknowledgedValue(String raw) {
        return raw==null?0:InstallationSnapshotCodec.decimal(raw,true);
    }
    /** Bound every read-boundary exception without confusing it with codec or ACK rejection. */
    private String readValue(String key) {
        try {return backend.get(key);}
        catch (RuntimeException unavailable) {throw new BackendReadFailure();}
    }
    /** Preserve the exact fail-closed result and unavailable revision/ACK, adding only a fixed observation. */
    private static ReadResult corrupt(ReadFailure failure) {
        return new ReadResult(ReadState.CORRUPT,null,0,failure);
    }
    /** Fixed internal marker holds neither the backend cause nor a content-bearing message. */
    private static final class BackendReadFailure extends RuntimeException {
        /** Discard the arbitrary backend exception rather than attaching its cause. */
        BackendReadFailure() {super("Installation backend read failed");}
    }
    /** A generic marker is unsupported by the raw facade; never resurrect its historical residue. */
    private boolean historicalAuthority() {
        try {return backend.get(SNAPSHOT_KEY)==null;}
        catch (RuntimeException corruptMarker) {return false;}
    }
    /** Commit only the separate ACK key and return durability failure without changing a runtime. */
    private boolean writeAcknowledgement(long revision) {
        try {return backend.commit(Collections.singletonMap("ackRevision",Long.toString(revision)),Collections.emptySet(),false);}
        catch (RuntimeException failed) {return false;}
    }
    /** Reject arbitrary preference access through the temporary compatibility facade. */
    private static boolean historicalKey(String key) {
        return "revision".equals(key)||"runtime".equals(key)||"manifest".equals(key)||"ackRevision".equals(key);
    }
}
