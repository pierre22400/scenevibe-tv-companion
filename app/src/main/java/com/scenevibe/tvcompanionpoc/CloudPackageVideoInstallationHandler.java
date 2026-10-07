package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationHandler;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.PreparedInstallation;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.util.Collections;
import org.json.JSONObject;

/**
 * Static Video-side wrapper preserves the exact v1 transport body as one opaque
 * durable artifact. Only in memory does the historical adapter split it for the
 * unchanged Video handler. Historical runtime/manifest snapshots delegate directly.
 * No artifact-count increase, generic-core branch or second cache is introduced.
 */
final class CloudPackageVideoInstallationHandler implements InstallationHandler {
    private final InstallationHandler delegate;
    private final String codec,handlerId;
    /** Bind one literal existing Video handler/codec pair at build time. */
    CloudPackageVideoInstallationHandler(InstallationHandler delegate,String codec,String handlerId) {
        this.delegate=delegate;this.codec=codec;this.handlerId=handlerId;
    }
    /** Retain only immutable trusted delegate preparation and the exact canonical transport body. */
    private static final class State implements PreparedState {
        final CloudPackageVideoInstallationHandler owner;final PreparedInstallation video;final InstallRequest canonical;
        /** Private construction prevents untrusted bytes from supplying a foreign prepared state. */
        State(CloudPackageVideoInstallationHandler owner,PreparedInstallation video,InstallRequest canonical) {
            this.owner=owner;this.video=video;this.canonical=canonical;
        }
    }
    /** Return bounded pure validation, including the existing executable Video profile checks. */
    @Override public InstallationStatus validate(InstallRequest request,TvCapabilities capabilities) {
        try {prepare(request,capabilities);return InstallationStatus.VALIDATED;}
        catch(VideoRuntimePreparation.Invalid refused) {return refused.status;}
        catch(RuntimeException refused) {return InstallationStatus.INVALID_PACKAGE;}
    }
    /** Adapt a new exact v1 body in memory; retain its original bytes for commit and later proof. */
    @Override public PreparedInstallation prepare(InstallRequest request,TvCapabilities capabilities) {
        if(request==null||request.artifact(CloudPackageInstallationAdapter.VIDEO_ARTIFACT)==null)return delegate.prepare(request,capabilities);
        try {
            if(request.artifactCount()!=1||!codec.equals(request.codecId())
                    ||request.artifact(CloudPackageInstallationAdapter.VIDEO_ARTIFACT).length>CloudPackageInstallationAdapter.MAX_BODY_BYTES)throw invalid();
            JSONObject envelope=new JSONObject(VideoRuntimePreparation.utf8(request.artifact(CloudPackageInstallationAdapter.VIDEO_ARTIFACT),800_000));
            InstallRequest adapted=CloudV1InstallationAdapter.adapt(envelope,envelope.getString("deviceId")).request();
            if(adapted.revision()!=request.revision()||!codec.equals(adapted.codecId()))throw invalid();
            PreparedInstallation video=delegate.prepare(adapted,capabilities);
            return new PreparedInstallation(request,handlerId,Collections.emptyMap(),video.requirements(),capabilities,new State(this,video,request));
        } catch(VideoRuntimePreparation.Invalid refused) {throw refused;}
        catch(Exception refused) {throw invalid();}
    }
    /** Persist only the exact wrapper body; old snapshots keep their historical canonical artifact set. */
    @Override public InstallRequest encodeForCache(PreparedInstallation prepared) {
        State state=owned(prepared);return state==null?delegate.encodeForCache(prepared):state.canonical;
    }
    /** Restore untrusted durable bytes through the same strict preparation, without storage or ARM. */
    @Override public PreparedInstallation restoreFromCache(InstallRequest request,TvCapabilities capabilities) {return prepare(request,capabilities);}
    /** Delegate only trusted immutable Video state to the unchanged owner-thread Video ARM. */
    @Override public InstallationStatus arm(PreparedInstallation prepared,RuntimePorts ports) {
        State state=owned(prepared);return delegate.arm(state==null?prepared:state.video,ports);
    }
    /** Require exact owner, canonical identity and fixed binding before accepting wrapper state. */
    private State owned(PreparedInstallation prepared) {
        if(prepared==null||!(prepared.preparedState() instanceof State))return null;
        State state=(State)prepared.preparedState();
        if(state.owner!=this||state.canonical!=prepared.canonical()||!codec.equals(prepared.codecId())||!handlerId.equals(prepared.handlerId()))throw invalid();
        return state;
    }
    /** Fixed semantic refusal preserves the old bounded status surface. */
    private static VideoRuntimePreparation.Invalid invalid() {return new VideoRuntimePreparation.Invalid(InstallationStatus.INVALID_PACKAGE);}
}
