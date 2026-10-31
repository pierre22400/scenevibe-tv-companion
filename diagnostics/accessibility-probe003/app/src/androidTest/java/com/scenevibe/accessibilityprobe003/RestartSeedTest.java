package com.scenevibe.accessibilityprobe003;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Seed one armed synthetic campaign before the CI-only process-stop/restart phase. */
@RunWith(AndroidJUnit4.class)
public final class RestartSeedTest {
    /** Arm own control and leave a durable row; the next runner must not restore that arm state. */
    @Test public void seedVolatileState() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        long end = SystemClock.uptimeMillis() + 15_000;
        while (ProbeService.current() == null && SystemClock.uptimeMillis() < end) SystemClock.sleep(100);
        assertNotNull(ProbeService.current());
        instrumentation.runOnMainSync(new Seed());
    }
    /** Seed action on the owned service thread. */
    private static final class Seed implements Runnable {
        /** Require a new own-control campaign; no external app is present in this test. */
        @Override public void run() {
            ProbeService service = ProbeService.current(); service.stop(SafeRow.Reason.OWNER_STOP);
            assertTrue(service.arm(ProbeProfile.OWN_CONTROL)); assertTrue(service.controller().active());
        }
    }
}
