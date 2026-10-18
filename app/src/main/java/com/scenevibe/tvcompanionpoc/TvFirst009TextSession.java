package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * Explicit local composition entry: current 008 cache -> M5 -> existing SceneRenderer
 * and the original passive MediaSessionProbe. No Activity/service/boot hook calls this
 * entry in any delivered profile. In particular 008 remains cache-only. The software-only
 * 009 profile retains 008's disabled Android components and cannot authorize a Sony trial.
 * This describes the accepted 009-003 profiles. 009-004 adds a separate Sony profile whose
 * explicit service consumes a one-use, in-memory confirmed reference from the same 008 source.
 */
final class TvFirst009TextSession {
    private final TvFirst009TextRuntime runtime;
    private final MediaSessionProbe probe;
    private final SceneRenderer renderer;

    /** Acquire no network or credential authority while connecting the already-confirmed local cache. */
    static TvFirst009TextSession open(Context context,TvFirst008Source source,TvFirst009TextRuntime.Decision decision) {
        if (!BuildConfig.TVFIRST_TEXT_LOCAL_QUALIFICATION
                || decision != TvFirst009TextRuntime.Decision.OWNER_LOCAL_AD_FREE_QUALIFICATION
                || Looper.myLooper() != Looper.getMainLooper() || context == null || source == null) return null;
        if (!Settings.canDrawOverlays(context) || !NotificationAccess.isGranted(context)) return null;
        TvFirst008Source.ConfirmedCacheReference reference=source.confirmedCacheReference();
        return openConfirmed(context,reference,decision,null);
    }

    /** Consume an already-confirmed memory reference after Activity pause, without any disk-based auto-ARM. */
    static TvFirst009TextSession openConfirmed(Context context,TvFirst008Source.ConfirmedCacheReference reference,
            TvFirst009TextRuntime.Decision decision,TvFirst009TextRuntime.Observer observer) {
        if (!BuildConfig.TVFIRST_TEXT_LOCAL_QUALIFICATION || context == null
                || decision != TvFirst009TextRuntime.Decision.OWNER_LOCAL_AD_FREE_QUALIFICATION
                || Looper.myLooper() != Looper.getMainLooper()
                || !Settings.canDrawOverlays(context) || !NotificationAccess.isGranted(context)) return null;
        if (reference == null) return null;
        TvFirst009TextCache.Prepared prepared;
        try { prepared=TvFirst009TextCache.load(context,reference); }
        catch (RuntimeException refused) { return null; }
        try { return new TvFirst009TextSession(context,prepared,observer); }
        catch (RuntimeException refused) { return null; }
    }

    /** Wire only the existing Android renderer and passive probe after explicit local qualification admission. */
    private TvFirst009TextSession(Context context,TvFirst009TextCache.Prepared prepared,TvFirst009TextRuntime.Observer observer) {
        Handler owner=new Handler(Looper.getMainLooper());
        renderer=new SceneRenderer(context,asset -> null,() -> owner.post(this::close));
        SceneRuntimeController.SceneSink scenes=new SceneRuntimeController.SceneSink() {
            /** Permissions are necessary for drawing, never sufficient for MEDIA identity. */
            @Override public boolean preflight(OverlayManifest.Scene scene) {
                return Settings.canDrawOverlays(context) && renderer.preflight(scene);
            }
            /** Draw through the exact renderer already qualified physically on Sony. */
            @Override public void show(OverlayManifest.Scene scene) { renderer.render(scene); }
            /** Close a finished scene immediately at the qualified media-window boundary. */
            @Override public void hide(OverlayManifest.Scene scene) { renderer.dismissNow(); }
            /** Loss of eligibility removes every view without leaving a fade callback. */
            @Override public void hideAll() { renderer.dismissNow(); }
        };
        runtime=new TvFirst009TextRuntime(owner,SystemClock::elapsedRealtime,scenes,observer);
        if (!runtime.start(prepared,TvFirst009TextRuntime.Decision.OWNER_LOCAL_AD_FREE_QUALIFICATION))
            throw new IllegalStateException("LOCAL_ARM_REFUSED");
        MediaSessionProbe acquired;
        try { acquired=new MediaSessionProbe(context,runtime.listener()); }
        catch (RuntimeException failed) { runtime.close(); throw failed; }
        probe=acquired;
        try {
            probe.start();
            if (!runtime.isActive()) throw new IllegalStateException("LOCAL_SESSION_CLOSED");
        }
        catch (RuntimeException failed) { probe.stop(); runtime.close(); throw failed; }
    }

    /** Release passive callbacks and local visuals only; never issue a command to a third-party player. */
    void close() { probe.stop(); runtime.close(); }

    /** Service notification ownership follows the existing runtime lifetime, not the Activity. */
    boolean isActive() { return runtime.isActive(); }
}
