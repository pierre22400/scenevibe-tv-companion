package com.scenevibe.accessibilityprobe003;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Fresh-process emulator verification; no inference about Sony restart behavior is made. */
@RunWith(AndroidJUnit4.class)
public final class RestartTest {
    /** Confirm a fresh process is disarmed while the stopped/orphaned local campaign remains available. */
    @Test public void noImplicitRestoreAfterProcessStop() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), ProbeActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ProbeService service = EmulatorServiceControl.connect(instrumentation); assertNotNull(service);
        assertFalse(service.controller().active()); assertEquals(0, service.getServiceInfo().eventTypes);
        assertNotNull(service.store().latest());
    }
}
