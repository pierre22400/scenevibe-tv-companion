package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.os.SystemClock;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PreparedInstallation;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;

/**
 * Read 008's exact durable artifact set and reuse M4/M5's existing semantic restoration.
 * This adapter has no installer commit, transport, credentials, ACK or automatic ARM path.
 * Preparation is not rendering permission. The original store and receipt remain unchanged.
 */
final class TvFirst009TextCache {
    /** Immutable prepared value retains the original bytes and their complete cache binding. */
    static final class Prepared {
        final TvFirst008Source.ConfirmedCacheReference reference;
        final PreparedInstallation installation;
        final VideoPreparedState video;
        /** Retain trusted, already-validated state without cloning or retiming comments. */
        private Prepared(TvFirst008Source.ConfirmedCacheReference reference,
                PreparedInstallation installation, VideoPreparedState video) {
            this.reference=reference; this.installation=installation; this.video=video;
        }
    }

    /** Resolve only the generation namespace already owned by the confirmed 008 reference. */
    static Prepared load(Context context, TvFirst008Source.ConfirmedCacheReference reference) {
        if (reference == null) throw new IllegalArgumentException("CACHE_NOT_CONFIRMED");
        InstallationStore store = new InstallationStore(new AndroidInstallationBackend(context,
                "tvfirst008_cache_" + reference.binding.generation.replace("-", "")));
        return load(store, reference, SystemClock.elapsedRealtime());
    }

    /** Restore through the unchanged manifested handler; never persist or mark acknowledged. */
    static Prepared load(InstallationStore store, TvFirst008Source.ConfirmedCacheReference reference, long now) {
        if (reference == null || !reference.isCurrent(now)) throw new IllegalArgumentException("CACHE_NOT_CURRENT");
        InstallationStore.ReadResult read = store.read();
        if (read.state() != InstallationStore.ReadState.SNAPSHOT || read.acknowledgedRevision() != 0
                || read.snapshot().revision() != reference.binding.revision
                || !TvCapabilities.CODEC_TRACK_OVERLAY.equals(reference.binding.codec)
                || !reference.binding.codec.equals(read.snapshot().codecId())
                || !reference.binding.digest.equals(TvFirst008Wire.digest(read.snapshot().canonical())))
            throw new IllegalArgumentException("CACHE_BINDING_MISMATCH");
        PreparedInstallation prepared = new VideoManifestInstallationHandler()
                .restoreFromCache(read.snapshot().canonical(), TvCapabilities.current());
        VideoPreparedState video = VideoPreparedState.owned(prepared,
                InstallationStore.COMPAT_OVERLAY_HANDLER_ID, TvCapabilities.CODEC_TRACK_OVERLAY, true);
        if (video == null || !reference.isCurrent(now)) throw new IllegalArgumentException("CACHE_NOT_CURRENT");
        for (OverlayManifest.Scene scene : video.manifest.scenes) {
            if (!SceneRenderer.imageAssetRefs(scene).isEmpty())
                throw new IllegalArgumentException("TEXT_ONLY_QUALIFICATION");
        }
        return new Prepared(reference, prepared, video);
    }

    /** This adapter is a pure local preparation boundary with no instance lifecycle. */
    private TvFirst009TextCache() {}
}
