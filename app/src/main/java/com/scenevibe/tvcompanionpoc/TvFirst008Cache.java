package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;

/**
 * Reuse the durable M4/M5 store and static V1 handlers without accepting RuntimePorts.
 * A generation namespace prevents unrelated identities from sharing revision evidence.
 * The historical ACK field and cloud_track cache are never written through this facade.
 */
final class TvFirst008Cache {
    private final InstallationStore store;
    private final PackageInstaller installer;
    /** Construct only inert persistence/preparation owners; no service or renderer is reachable. */
    TvFirst008Cache(Context context, String generation) {
        this(new InstallationStore(new AndroidInstallationBackend(context,
                "tvfirst008_cache_" + TvFirst008Wire.uuid(generation).replace("-", ""))));
    }
    /** Reuse real handlers with an injectable durable backend for fault qualification. */
    TvFirst008Cache(InstallationStore store) {
        this.store = store; installer = new PackageInstaller(store, VideoInstallationHandlers.registry(), TvCapabilities.current());
    }
    /** Prepare and re-read exact durable bytes; never ARM, mark ACK, or infer a server receipt. */
    boolean prepare(TvFirst008Wire.Envelope envelope) {
        return installer.prepareOnly(envelope.request) == InstallationStatus.PREPARED && matches(envelope.binding);
    }
    /** Revalidate storage and semantic preparation again on restart before confirming cache. */
    boolean matches(TvFirst008Wire.Binding binding) {
        InstallationStore.ReadResult read = store.read();
        return read.state() == InstallationStore.ReadState.SNAPSHOT && read.acknowledgedRevision() == 0
                && read.snapshot().revision() == binding.revision && read.snapshot().codecId().equals(binding.codec)
                && TvFirst008Wire.digest(read.snapshot().canonical()).equals(binding.digest)
                && installer.prepareOnly(read.snapshot().canonical()) == InstallationStatus.PREPARED;
    }
}
