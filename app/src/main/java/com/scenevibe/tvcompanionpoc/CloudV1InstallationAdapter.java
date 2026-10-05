package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONObject;

/**
 * Pure historical Cloud v1 compatibility boundary, outside the installation core.
 * CloudProtocol remains the wire authority. The adapter preserves its JSON serialization
 * and keeps the ACK's finalTrackId beside, never inside, the immutable InstallRequest.
 * Revision policy, persistence and activation belong exclusively to PackageInstaller.
 */
final class CloudV1InstallationAdapter {
    /** Immutable bounded handoff retains no mutable envelope, secret or live collaborator. */
    static final class Assignment {
        private final InstallRequest request;
        private final String finalTrackId;
        /** Bind the validated request to its exact historical ACK identifier without normalization. */
        private Assignment(InstallRequest request,String finalTrackId) {
            this.request=request;this.finalTrackId=finalTrackId;
        }
        /** Return the immutable inert package, whose byte accessors are defensive. */
        InstallRequest request() {return request;}
        /** Return only the historical transport ACK binding, never a credential. */
        String finalTrackId() {return finalTrackId;}
    }

    /**
     * Adapt wire-valid v1 data off the owner thread using the existing JSONObject.toString form.
     * A zero validator floor preserves wire checks while leaving stale/same/new authority to
     * the installer. Failure exposes only a fixed label, without retaining parser content.
     */
    static Assignment adapt(JSONObject envelope,String cloudDeviceId) {
        try {
            if (cloudDeviceId==null||!CloudProtocol.validAssignment(envelope,cloudDeviceId,0)) throw invalid();
            String finalTrackId=envelope.optString("finalTrackId","");
            if (finalTrackId.length()>3_000_000) throw invalid();
            Map<String,byte[]> artifacts=new TreeMap<>();
            artifacts.put("runtime",envelope.getJSONObject("runtimeTrack").toString().getBytes(StandardCharsets.UTF_8));
            boolean manifested=envelope.has("overlayManifest");
            if (manifested) artifacts.put("manifest",envelope.getJSONObject("overlayManifest").toString().getBytes(StandardCharsets.UTF_8));
            return new Assignment(new InstallRequest(envelope.optLong("revision",-1),
                    manifested?TvCapabilities.CODEC_TRACK_OVERLAY:TvCapabilities.CODEC_TRACK,artifacts),finalTrackId);
        } catch (Exception rejected) {throw invalid();}
    }

    /**
     * Preserve bounded manifested diagnostics through the existing handler-owned semantic seam.
     * This observational evaluation never gates installation or reinterprets a successful
     * redelivery's incoming bytes. Unsupported executable profiles are not malformed transport.
     */
    static RuntimeDiagnostics.ManifestCode manifestCode(InstallRequest request,InstallationStatus result) {
        if (request==null||!TvCapabilities.CODEC_TRACK_OVERLAY.equals(request.codecId()))
            return RuntimeDiagnostics.ManifestCode.NONE;
        if (result==InstallationStatus.CACHE_FAILED) return RuntimeDiagnostics.ManifestCode.MANIFEST_CACHE_FAILED;
        if (result!=InstallationStatus.INVALID_PACKAGE) return RuntimeDiagnostics.ManifestCode.NONE;
        try {
            VideoInstallationHandlers.manifested().prepareCompatibility(
                    VideoRuntimePreparation.utf8(request.artifact("runtime"),400_000),
                    VideoRuntimePreparation.utf8(request.artifact("manifest"),800_000));
            return RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID;
        } catch (VideoManifestInstallationHandler.Invalid rejected) {return rejected.code;}
        catch (RuntimeException rejected) {return RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID;}
    }

    /** Return a fixed wire refusal without a cause, URL, artifact or submitted identifier. */
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Invalid Cloud assignment");}
    /** Static adaptation has no retained mutable state or discovery mechanism. */
    private CloudV1InstallationAdapter() {}
}
