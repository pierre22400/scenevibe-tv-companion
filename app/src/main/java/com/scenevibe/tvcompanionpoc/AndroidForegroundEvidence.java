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
        return primeForeground(null);
    }

    /** Observe the existing finite reduction without changing any decision or exception path. */
    boolean primeForeground(NativeSelectionTrace trace) {
        if (trace != null) trace.foregroundWindowMs = EVENT_WINDOW_MS;
        try {
            return evaluateForeground(trace);
        } catch (RuntimeException unavailable) {
            mark(trace, NativeSelectionTrace.Foreground.SYSTEM_EXCEPTION);
            throw unavailable;
        }
    }

    /** Preserve permission/query/event order; wall time belongs only to UsageEvents evidence. */
    private boolean evaluateForeground(NativeSelectionTrace trace) {
        if (!accessGranted()) {
            mark(trace, NativeSelectionTrace.Foreground.ACCESS_DENIED);
            return false;
        }
        UsageStatsManager usage = context.getSystemService(UsageStatsManager.class);
        if (usage == null) {
            mark(trace, NativeSelectionTrace.Foreground.MANAGER_UNAVAILABLE);
            return false;
        }
        long now = System.currentTimeMillis();
        UsageEvents events = usage.queryEvents(now - EVENT_WINDOW_MS, now);
        if (events == null) {
            mark(trace, NativeSelectionTrace.Foreground.EVENTS_UNAVAILABLE);
            return false;
        }
        UsageEvents.Event event = new UsageEvents.Event();
        NativeForegroundProof proof = new NativeForegroundProof();
        int examined = 0;
        while (events.hasNextEvent()) {
            if (++examined > 1024) {
                if (trace != null) trace.eventsExamined = examined;
                mark(trace, NativeSelectionTrace.Foreground.EVENT_LIMIT);
                return false;
            }
            if (trace != null) trace.eventsExamined = examined;
            events.getNextEvent(event);
            proof.event(event.getEventType(), event.getPackageName());
            observeEvent(trace, event, examined, now);
        }
        boolean admitted = proof.primeAlone();
        if (trace != null) {
            trace.resumedApps = proof.resumedCount();
            mark(trace, admitted ? NativeSelectionTrace.Foreground.PRIME_ALONE
                    : trace.relevantEvents == 0 ? NativeSelectionTrace.Foreground.NO_RELEVANT_EVENTS
                    : trace.resumedApps > 1 ? NativeSelectionTrace.Foreground.AMBIGUOUS
                    : NativeSelectionTrace.Foreground.PRIME_NOT_FOREGROUND);
        }
        return admitted;
    }

    /** Write a bounded diagnostic enum only; it is never input to primeAlone(). */
    private static void mark(NativeSelectionTrace trace, NativeSelectionTrace.Foreground result) {
        if (trace != null) trace.foreground = result;
    }

    /** Extra event-age evidence is best effort and must never affect the existing reduction. */
    private static void observeEvent(NativeSelectionTrace trace, UsageEvents.Event event,
            int examined, long now) {
        if (trace == null) return;
        trace.eventsExamined = examined;
        try {
            int type = event.getEventType();
            String pkg = event.getPackageName();
            if ((type == 1 || type == 2 || type == 23) && pkg != null && !pkg.isEmpty()) {
                trace.relevantEvents++;
                trace.lastRelevantEventAgeMs = now - event.getTimeStamp();
            }
        } catch (RuntimeException diagnosticUnavailable) {
            trace.detailsComplete = false;
        }
    }
}
