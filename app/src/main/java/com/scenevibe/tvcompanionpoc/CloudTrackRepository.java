package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Phase C moves Android persistence mechanics under InstallationStore. This compatibility
 * repository retains its exact Video parsers, scheduler/restore sequence, revision checks
 * and test seam. Current callers still use the historical tuple, never generic handlers.
 */

/** Persists one validated text-only runtime track and its revision in app-private storage. */
final class CloudTrackRepository {
    interface Storage {
        String get(String key);
        boolean save(long revision, String json);
        boolean saveAck(long revision);
        void clear();
        /**
         * Persists a revision together with its runtimeTrack AND its OverlayManifest in ONE
         * atomic commit, so a manifested revision is either fully durable (revision + runtime
         * + manifest) or not applied at all - never a mixed revision. A null manifestJson
         * means "this revision has no manifest" and MUST remove any previously stored manifest
         * in the same commit so a stale manifest from an older revision can never survive.
         *
         * <p>Default implementation keeps legacy no-manifest test doubles working: it refuses
         * to carry a manifest it cannot store atomically (returns false) and otherwise falls
         * back to the manifest-less {@link #save(long, String)} path.</p>
         */
        default boolean save(long revision, String runtimeJson, String manifestJson) {
            if (manifestJson != null) return false;
            return save(revision, runtimeJson);
        }
    }
    private final Storage storage;
    /** Wraps SharedPreferences.commit so a successful return means both fields are durable. */
    CloudTrackRepository(Context context) {
        InstallationStore store=new InstallationStore(new AndroidInstallationBackend(context));
        storage=new Storage() {
            /** Delegate raw historical reads without interpreting generic installation state. */
            @Override public String get(String key) {return store.historicalValue(key);}
            /** Keep legacy removal of a stale manifest inside the same durable batch. */
            @Override public boolean save(long revision,String json) {
                // Legacy Case A (no manifest): persist revision + runtime and remove any stale
                // manifest in the SAME commit so a disconnected revision never keeps an older
                // revision's manifest. Observable no-manifest behavior is otherwise unchanged.
                return store.saveHistorical(revision,json,null);
            }
            /** Delegate the complete historical tuple to the sole Android persistence owner. */
            @Override public boolean save(long revision,String runtimeJson,String manifestJson) {
                // Atomic manifested install: revision + runtime + manifest all durable in one
                // commit, or none of them. A null manifest removes any stale manifest key.
                return store.saveHistorical(revision,runtimeJson,manifestJson);
            }
            /** Record the confirmed exact historical revision, never transmit its ACK. */
            @Override public boolean saveAck(long revision) {
                return store.saveHistoricalAcknowledgement(revision);
            }
            /** Retain explicit reset/corruption clearing of this cache file only. */
            @Override public void clear() {store.clearHistorical();}
        };
    }
    /** Injectable persistence boundary for deterministic JVM tests. */
    CloudTrackRepository(Storage storage) {this.storage=storage;}
    /** Reads and validates cached state; corruption never reaches the scheduler. */
    synchronized long restore(MediaSyncedTrackScheduler scheduler) {
        try {
            String json=storage.get("runtime"), raw=storage.get("revision");
            if(json==null||raw==null)return 0;
            long revision=Long.parseLong(raw);
            if(revision<1)throw new IllegalArgumentException("Invalid cache revision");
            ScheduledTrack track=parse(json);
            scheduler.load(track);
            return revision;
        } catch(Exception invalid) {storage.clear();return 0;}
    }
    /** Validates completely, commits atomically and only then changes the live scheduler. */
    synchronized boolean install(long revision,String runtimeJson,MediaSyncedTrackScheduler scheduler) {
        if(revision<1||runtimeJson==null||runtimeJson.length()>400_000)return false;
        try {
            long prior=0;
            String raw=storage.get("revision");
            if(raw!=null)prior=Long.parseLong(raw);
            if(revision<=prior)return false;
            ScheduledTrack track=parse(runtimeJson);
            if(!storage.save(revision,runtimeJson))return false;
            scheduler.load(track);
            return true;
        } catch(Exception invalid) {return false;}
    }
    /**
     * Bounded outcome of a manifested install. On {@link #ok} the revision + runtimeTrack +
     * manifest were validated, cross-checked and committed atomically, and the scheduler was
     * loaded. On failure {@link #code} is the single bounded reason and NOTHING was changed:
     * the prior durable revision (runtime + manifest + revision metadata) stays entirely
     * intact, so there is never a mixed revision.
     */
    static final class InstallResult {
        final boolean ok;
        final RuntimeDiagnostics.ManifestCode code;

        private InstallResult(boolean ok, RuntimeDiagnostics.ManifestCode code) {
            this.ok = ok;
            this.code = code;
        }
    }

    /**
     * Atomically installs a manifested Video revision: validates the runtimeTrack with the
     * shared parser, parses+validates the OverlayManifest, runs the cross-contract
     * {@link VideoOverlayManifestBridge}, and ONLY on full success commits revision + runtime
     * + manifest together before loading the scheduler. Any validation failure or a
     * non-durable commit leaves the prior revision entirely intact (no mixed revision, no
     * scheduler change). Returns a bounded {@link InstallResult}; this method never ACKs and
     * never surfaces comment/scene content.
     */
    synchronized InstallResult install(long revision,String runtimeJson,String manifestJson,
            MediaSyncedTrackScheduler scheduler) {
        if(revision<1||runtimeJson==null||runtimeJson.length()>400_000
                ||manifestJson==null||manifestJson.length()>800_000)
            return new InstallResult(false,RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
        ScheduledTrack track;
        OverlayManifest manifest;
        try {
            long prior=0;
            String raw=storage.get("revision");
            if(raw!=null)prior=Long.parseLong(raw);
            if(revision<=prior)
                return new InstallResult(false,RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
            track=parse(runtimeJson);
            manifest=OverlayManifestParser.parse(new org.json.JSONObject(manifestJson));
        } catch(Exception invalid) {
            return new InstallResult(false,RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
        }
        VideoOverlayManifestBridge.Result cross=VideoOverlayManifestBridge.validate(track,manifest);
        if(!cross.ok)return new InstallResult(false,cross.code);
        if(!storage.save(revision,runtimeJson,manifestJson))
            return new InstallResult(false,RuntimeDiagnostics.ManifestCode.MANIFEST_CACHE_FAILED);
        scheduler.load(track);
        return new InstallResult(true,RuntimeDiagnostics.ManifestCode.NONE);
    }

    /**
     * Bounded outcome of a manifested restore. On {@link #ok}, {@link #revision} is the single
     * durable revision whose runtimeTrack has been loaded into the scheduler and whose
     * {@link #manifest} parsed+cross-checked successfully. Corruption of EITHER half fails
     * closed to the whole revision being discarded ({@code ok==false, revision==0}): the
     * scheduler is never touched and no half-restore reaches the regie.
     */
    static final class RestoreResult {
        final boolean ok;
        final long revision;
        final OverlayManifest manifest;

        private RestoreResult(boolean ok,long revision,OverlayManifest manifest) {
            this.ok = ok;
            this.revision = revision;
            this.manifest = manifest;
        }
    }

    /**
     * Atomically restores a manifested revision: it requires BOTH a valid runtimeTrack and a
     * valid, cross-checked OverlayManifest at the same durable revision. If either is missing
     * or corrupt the whole revision is discarded (storage cleared, scheduler untouched) and a
     * failed result is returned, so a mixed or half revision can never reach the scheduler.
     * A revision with no stored manifest is NOT a manifested revision and returns a failed
     * result WITHOUT clearing storage, so the legacy {@link #restore(MediaSyncedTrackScheduler)}
     * path remains the authority for Case A.
     */
    synchronized RestoreResult restoreWithManifest(MediaSyncedTrackScheduler scheduler) {
        String manifestJson=storage.get("manifest");
        if(manifestJson==null)return new RestoreResult(false,0,null);
        try {
            String json=storage.get("runtime"), raw=storage.get("revision");
            if(json==null||raw==null)throw new IllegalArgumentException("Incomplete manifested cache");
            long revision=Long.parseLong(raw);
            if(revision<1)throw new IllegalArgumentException("Invalid cache revision");
            ScheduledTrack track=parse(json);
            OverlayManifest manifest=OverlayManifestParser.parse(new org.json.JSONObject(manifestJson));
            VideoOverlayManifestBridge.Result cross=VideoOverlayManifestBridge.validate(track,manifest);
            if(!cross.ok)throw new IllegalArgumentException("Inconsistent manifested cache");
            scheduler.load(track);
            return new RestoreResult(true,revision,manifest);
        } catch(Exception invalid) {
            storage.clear();
            return new RestoreResult(false,0,null);
        }
    }

    /** Returns the last durable revision without trusting a corrupt number. */
    synchronized long revision() {
        try {return Long.parseLong(storage.get("revision"));}
        catch(Exception invalid) {return 0;}
    }
    /**
     * The trackId of the cached runtime track (a bounded, non-secret identifier), or null
     * when nothing is cached or the cache is corrupt. It returns ONLY the trackId string,
     * never the full FinalTrack, comment text or any other payload, so it is safe to surface
     * in diagnostics. Corruption fails closed to null.
     */
    synchronized String cachedTrackId() {
        try {
            String json=storage.get("runtime");
            if(json==null)return null;
            String id=new JSONObject(json).optString("trackId",null);
            return id==null||id.isEmpty()?null:id;
        } catch(Exception invalid) {return null;}
    }
    /** Revisions lacking a confirmed ACK remain eligible for redelivery and retry. */
    synchronized long acknowledged() {
        try {return Long.parseLong(storage.get("ackRevision"));}
        catch(Exception invalid) {return 0;}
    }
    /** Records an ACK only after the server confirms it, without changing the track JSON. */
    synchronized boolean markAcknowledged(long revision) {
        return revision==revision() && storage.saveAck(revision);
    }
    /**
     * Erases the entire cached runtime track, its revision and ACK state. This is used ONLY
     * by the exceptional "Reset SceneVibe Cloud connection" flow; the normal Disconnect Cloud
     * path never calls it, so a disconnected TV keeps playing its last cached track offline.
     */
    synchronized void clear() {storage.clear();}
    /** Applies the exact parser used by LAN; cloud runtime is text-only. */
    private ScheduledTrack parse(String json) throws Exception {
        JSONObject envelope=new JSONObject(json);
        JSONArray comments=envelope.optJSONArray("comments");
        if(comments==null)throw new IllegalArgumentException("Invalid cloud comments");
        for(int i=0;i<comments.length();i++) {
            JSONObject item=comments.optJSONObject(i);
            if(item==null||item.has("media"))throw new IllegalArgumentException("Cloud media unsupported");
        }
        return TrackParser.parse(envelope, media -> {
            throw new TrackParser.Invalid("invalid_media","Cloud v1 is text-only");
        });
    }
}
