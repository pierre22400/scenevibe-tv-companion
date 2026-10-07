package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.ExecutionRequirements;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationHandler;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.PreparedInstallation;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import org.json.JSONObject;

/**
 * Own the local Banner (WALL) profile parsing and the single Banner cross-contract bridge
 * evaluation (section 8). It is the WALL analogue of {@link VideoManifestInstallationHandler}:
 * stateless, bounded diagnostics, and validate/prepare/encode/restore with ZERO persistence,
 * runtime, rendering, clock, timer or ACK side effect before commit. It reuses the EXISTING single
 * durable path (the generic {@link PreparedInstallation}/{@code InstallRequest} + the installer and
 * store), adds no second store/installer/journal/anchor persistence, and ARMs through the common
 * owner's Banner ports (FEAT-002 {@link BannerInstallationRuntimePorts}).
 */
final class BannerInstallationHandler implements InstallationHandler {
    /** The single Banner body artifact key inside the one-artifact Banner package. */
    static final String ARTIFACT = "banner";
    /** Reuse the codec identity as the fixed local Banner handler id, exactly as the Video shapes do. */
    static final String HANDLER_ID = TvCapabilities.CODEC_BANNER_WALL_OVERLAY;
    /** The Banner body reuses the overlay contract's exact UTF-16 ceiling for its inline manifest. */
    private static final int MAX_BODY_UTF16 = 800_000;

    /** Fixed Banner-specific failure exposes only the existing bounded diagnostic code. */
    static final class Invalid extends IllegalArgumentException {
        final InstallationStatus status;
        final RuntimeDiagnostics.ManifestCode code;
        /** Carry neither parser cause nor input content into the repository's existing result. */
        Invalid(InstallationStatus status, RuntimeDiagnostics.ManifestCode code) {
            super(status.name());
            this.status = status;
            this.code = code;
        }
    }

    /** Construct a stateless build-local handler without any platform or live-runtime dependency. */
    BannerInstallationHandler() {}

    /** Return a closed semantic/capability result with no mutation or raw exception exposure. */
    @Override public InstallationStatus validate(InstallRequest request, TvCapabilities capabilities) {
        try { prepare(request, capabilities); return InstallationStatus.VALIDATED; }
        catch (Invalid invalid) { return invalid.status; }
        catch (RuntimeException invalid) { return InstallationStatus.INVALID_PACKAGE; }
    }

    /** Decode the single body once, run the full Banner cross-contract, then bind exact bytes. */
    @Override public PreparedInstallation prepare(InstallRequest request, TvCapabilities capabilities) {
        Map<String, byte[]> artifacts = artifacts(request);
        BannerProfileParser.Profile profile = parse(utf8(artifacts.get(ARTIFACT)));
        requireCoherence(profile);
        ExecutionRequirements needs = new ExecutionRequirements(TvCapabilities.OVERLAY_CONTRACT,
                ExecutionRequirements.ClockMode.WALL, ExecutionRequirements.PauseBehavior.CONTINUE,
                profile.manifest.scenes.size(), false, false);
        requireCapabilities(request, needs, capabilities);
        BannerPreparedState state = new BannerPreparedState(profile.calendar, profile.manifest)
                .bind(request, HANDLER_ID, needs);
        return new PreparedInstallation(request, HANDLER_ID, Collections.emptyMap(), needs, capabilities, state);
    }

    /** Return only the exact immutable candidate belonging to this handler and trusted state. */
    @Override public InstallRequest encodeForCache(PreparedInstallation prepared) {
        if (BannerPreparedState.owned(prepared, HANDLER_ID, TvCapabilities.CODEC_BANNER_WALL_OVERLAY) == null) {
            throw new Invalid(InstallationStatus.INVALID_PACKAGE, RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
        }
        return prepared.canonical();
    }

    /** Durable bytes remain untrusted: run the same live decode/cross-contract preparation without ARM. */
    @Override public PreparedInstallation restoreFromCache(InstallRequest snapshot, TvCapabilities capabilities) {
        return prepare(snapshot, capabilities);
    }

    /** Retire the opposite owner and arm the prepared Banner through the common owner's WALL ports. */
    @Override public InstallationStatus arm(PreparedInstallation prepared, RuntimePorts ports) {
        return BannerPreparedState.arm(prepared, ports, HANDLER_ID, TvCapabilities.CODEC_BANNER_WALL_OVERLAY);
    }

    /** Validate the exact codec/single-key vocabulary and obtain the detached bounded body. */
    private static Map<String, byte[]> artifacts(InstallRequest request) {
        if (request == null || !TvCapabilities.CODEC_BANNER_WALL_OVERLAY.equals(request.codecId())) {
            throw new Invalid(InstallationStatus.INVALID_PACKAGE, RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
        }
        Map<String, byte[]> artifacts = request.artifacts();
        if (artifacts.size() != 1 || !artifacts.containsKey(ARTIFACT)) {
            throw new Invalid(InstallationStatus.INVALID_PACKAGE, RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
        }
        return artifacts;
    }

    /** Decode strict UTF-8 without replacement/normalization and retain the UTF-16 ceiling. */
    private static String utf8(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BODY_UTF16 * 3) {
            throw new Invalid(InstallationStatus.INVALID_PACKAGE, RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
        }
        try {
            String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (value.length() > MAX_BODY_UTF16) {
                throw new Invalid(InstallationStatus.INVALID_PACKAGE, RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
            }
            return value;
        } catch (Invalid invalid) { throw invalid; }
        catch (Exception invalid) {
            throw new Invalid(InstallationStatus.INVALID_PACKAGE, RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
        }
    }

    /** Apply the qualified Banner body parser, discarding all parser causes/content on rejection. */
    private static BannerProfileParser.Profile parse(String body) {
        try { return BannerProfileParser.parse(new JSONObject(body)); }
        catch (Exception invalid) {
            throw new Invalid(InstallationStatus.INVALID_PACKAGE, RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
        }
    }

    /** Sole Banner cross-contract call; a reject carries only the bounded manifest code. */
    private static void requireCoherence(BannerProfileParser.Profile profile) {
        BannerOverlayManifestBridge.Result cross =
                BannerOverlayManifestBridge.validate(profile.calendar, profile.manifest);
        if (!cross.ok) throw new Invalid(InstallationStatus.INVALID_PACKAGE, cross.code);
    }

    /** Reject unsupported executable requirements before any durable or live operation. */
    private static void requireCapabilities(InstallRequest request, ExecutionRequirements needs,
            TvCapabilities capabilities) {
        InstallationStatus status = capabilities == null
                ? InstallationStatus.INVALID_PACKAGE : capabilities.validate(request, needs);
        if (status != InstallationStatus.VALIDATED) {
            throw new Invalid(status, RuntimeDiagnostics.ManifestCode.MANIFEST_INVALID);
        }
    }
}
