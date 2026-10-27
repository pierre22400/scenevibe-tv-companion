package com.scenevibe.tvcompanionpoc;

import android.provider.Settings;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Real Handler scheduling under Robolectric verifies cadence and cancellation without any media authority. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class MediaSessionProbeSamplingTest {
    private MediaSessionProbe probe;
    private final AtomicInteger unavailable=new AtomicInteger();

    /** Keep notification access closed so each passive sample must report unavailability. */
    @Before public void before(){Settings.Secure.putString(RuntimeEnvironment.getApplication().getContentResolver(),"enabled_notification_listeners","");}
    /** Remove only this probe's delayed callbacks. */
    @After public void after(){if(probe!=null)probe.stop();}
    /** Count actual sampling callbacks while refusing any unexpected metadata delivery. */
    private MediaSessionProbe.Listener listener(){return new MediaSessionProbe.Listener(){
        /** A disabled permission must never expose a MediaSession sample. */
        @Override public void onSnapshot(MediaSessionProbe.Snapshot snapshot){fail("Permission-free snapshot");}
        /** Count the real fail-closed branch reached on each scheduled sample. */
        @Override public void onUnavailable(){unavailable.incrementAndGet();}
    };}
    /** Existing clients still receive the historical one-second schedule. */
    @Test public void historicalConstructorRetainsOneSecond(){probe=new MediaSessionProbe(RuntimeEnvironment.getApplication(),listener());
        probe.start();assertEquals(1,unavailable.get());shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(999));
        assertEquals(1,unavailable.get());shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(1));assertEquals(2,unavailable.get());}
    /** Multimedia can observe four times per second, and stopping cancels every future callback. */
    @Test public void multimediaCadenceAndStopUseTheSameSampler(){probe=new MediaSessionProbe(RuntimeEnvironment.getApplication(),listener(),null,250L);
        probe.start();shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertEquals(5,unavailable.get());
        probe.stop();shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertEquals(5,unavailable.get());}
    /** A faster or unbounded loop cannot be requested by any caller. */
    @Test public void outOfPolicyIntervalsAreRejected(){for(long value:new long[]{-1,0,249,1001}){
        try{new MediaSessionProbe(RuntimeEnvironment.getApplication(),listener(),null,value);fail("Unbounded cadence accepted");}
        catch(IllegalArgumentException expected){assertEquals("SAMPLE_INTERVAL_OUT_OF_POLICY",expected.getMessage());}}}
}
