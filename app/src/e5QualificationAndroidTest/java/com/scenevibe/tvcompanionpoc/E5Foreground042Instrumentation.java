package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.os.Bundle;

/**
 * Read real API31 UsageEvents while Settings is the emulator-only foreground witness.
 * Only this test maps Settings to the qualification target for the pure reducer. The real
 * Android adapter must still refuse Settings. No media transport or Sony device is used.
 */
public final class E5Foreground042Instrumentation extends Instrumentation {
    private String phase;

    /** Retain the explicit emulator-test phase, never a runtime target override. */
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        phase = arguments.getString("phase");
        start();
    }

    /** Reconstruct actual framework events and check stable visibility or HOME departure. */
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            require(android.os.Build.VERSION.SDK_INT == 31, "API31_REQUIRED");
            long now = System.currentTimeMillis();
            UsageEvents events = getTargetContext().getSystemService(UsageStatsManager.class).queryEvents(0, now);
            require(events != null, "REAL_USAGE_EVENTS");
            NativeForegroundHistory history = new NativeForegroundHistory();
            UsageEvents.Event event = new UsageEvents.Event();
            long resumedAt = -1;
            int examined = 0;
            while (events.hasNextEvent()) {
                require(++examined <= AndroidForegroundEvidence.EVENT_LIMIT && events.getNextEvent(event), "EVENT_BOUND");
                String pkg = event.getPackageName();
                if ("com.android.settings".equals(pkg)) {
                    if (event.getEventType() == 1) resumedAt = event.getTimeStamp();
                    pkg = NativeQualificationPolicy.PRIME_PACKAGE;
                }
                history.event(event.getEventType(), pkg, event.getClassName(), event.getTimeStamp());
            }
            require(resumedAt > 0 && now - resumedAt > 30000, "NO_RECENT_WITNESS_RESUME");
            require(history.primeAlone() == "stable".equals(phase), "REAL_LIFECYCLE_STATE_" + phase);
            require(!new AndroidForegroundEvidence(getTargetContext()).primeForeground(), "REAL_ADAPTER_REFUSES_NON_PRIME");
            result.putInt("passed", 5);
            result.putInt("failed", 0);
            result.putString("scope", "REAL_API31_EVENTS_SETTINGS_WITNESS_TARGET_MAPPING_TEST_ONLY");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putInt("failed", 1);
            result.putString("failure", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    /** Fail the platform qualification immediately on an absent or contradictory observation. */
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
