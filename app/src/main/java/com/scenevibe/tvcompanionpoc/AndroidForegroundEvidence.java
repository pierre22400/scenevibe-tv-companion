package com.scenevibe.tvcompanionpoc;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

/**
 * Qualification-only, read-only foreground proof; no history or app names are persisted.
 *
 * <p>A paused MediaSession can survive HOME. Token checks alone cannot detect that departure.
 * This optional debug profile therefore requires explicit Usage Access and a recent resumed
 * Prime Activity. Missing/old evidence refuses transport. It is not used by M5 or flag-OFF builds.</p>
 */
final class AndroidForegroundEvidence {
    private final Context context;
    static final long EVENT_WINDOW_MS = 30000L;

    /** Retain only the application context, never an Activity or a durable access credential. */
    AndroidForegroundEvidence(Context context) { this.context = context.getApplicationContext(); }

    /** Check the explicit local qualification permission without opening Settings automatically. */
    boolean accessGranted() {
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * Require the last resumed Activity in the finite window to belong to Prime.
     * WALL timestamps are used only because UsageEvents requires them, not to schedule content.
     */
    boolean primeForeground() {
        if (!accessGranted()) return false;
        UsageStatsManager usage = context.getSystemService(UsageStatsManager.class);
        if (usage == null) return false;
        long now = System.currentTimeMillis();
        UsageEvents events = usage.queryEvents(now - EVENT_WINDOW_MS, now);
        if (events == null) return false;
        UsageEvents.Event event = new UsageEvents.Event();
        NativeForegroundProof proof = new NativeForegroundProof();
        int examined = 0;
        while (events.hasNextEvent()) {
            if (++examined > 1024) return false;
            events.getNextEvent(event);
            proof.event(event.getEventType(), event.getPackageName());
        }
        return proof.primeAlone();
    }
}
