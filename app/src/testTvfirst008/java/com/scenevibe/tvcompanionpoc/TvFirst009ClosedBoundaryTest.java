package com.scenevibe.tvcompanionpoc;

import android.os.Handler;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** The delivered 008 profile remains closed before any cache, probe, secret or renderer construction. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={31,35})
public final class TvFirst009ClosedBoundaryTest {
    /** An explicit local decision cannot bypass the build boundary in the unchanged 008 journey. */
    @Test public void cacheOnlyProfileCannotOpenTextComposition() {
        assertFalse(BuildConfig.TVFIRST_TEXT_LOCAL_QUALIFICATION);
        assertNull(TvFirst009TextSession.open(null,null,TvFirst009TextRuntime.Decision.OWNER_LOCAL_AD_FREE_QUALIFICATION));
        TvFirst009TextRuntime runtime=new TvFirst009TextRuntime(new Handler(Looper.getMainLooper()),()->0L,new SceneRuntimeController.SceneSink() {
            /** No preflight should occur behind the closed profile. */
            @Override public boolean preflight(OverlayManifest.Scene scene) { throw new AssertionError("CLOSED"); }
            /** No SHOW should occur behind the closed profile. */
            @Override public void show(OverlayManifest.Scene scene) { throw new AssertionError("CLOSED"); }
            /** No scene exists behind the closed profile. */
            @Override public void hide(OverlayManifest.Scene scene) { throw new AssertionError("CLOSED"); }
            /** Construction alone performs no cleanup side effect. */
            @Override public void hideAll() { throw new AssertionError("CLOSED"); }
        });
        assertFalse(runtime.start(null,TvFirst009TextRuntime.Decision.OWNER_LOCAL_AD_FREE_QUALIFICATION));
    }
}
