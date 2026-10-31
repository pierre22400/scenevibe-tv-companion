package com.scenevibe.accessibilityprobe004;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityRecord;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Passive Android adapter. It has no transport or Companion reference. Android binds it only
 * after manual enablement. Live collections are voluntary, package-scoped and process-volatile.
 *
 * One worker handles public fresh traversals; a campaign-only timer is callback-independent.
 * Stop invalidates generation before closing. An in-flight Binder may finish, but cannot write
 * to the stopped file or another campaign. No catch-up queue or permanent monitor exists.
 */
public final class ProbeService extends AccessibilityService {
    private static volatile ProbeService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextProjection projection = new TextProjection();
    private final ProbeClock clock = new AndroidProbeClock();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final AtomicLong callbacks = new AtomicLong();
    private volatile ProbeController controller;
    private JournalStore store;
    private ObservationCadence cadence;
    private SurveyEngine.History history;
    private long snapshotSequence, lastEventSurvey, lastAcceptedSurvey;

    /** Return a live process-local connection for the setup Activity; no exported control binder. */
    public static ProbeService current() { return instance; }

    /** Initialize private storage and disarm on every Android connection, including reconnections. */
    @Override protected void onServiceConnected() {
        handler.removeCallbacksAndMessages(null);
        if (controller != null) controller.disconnect(SafeRow.Reason.RECONNECTED);
        projection.clear();
        try {
            store = new JournalStore(new File(getFilesDir(), "probe004"));
            store.prune(clock.wall()); controller = new ProbeController(clock, store);
            controller.connect(); instance = this; configure(false);
        } catch (IOException failure) { instance = null; configure(false); }
    }

    /** Configure the actual single package and mask; OFF requests no diagnostic callbacks. */
    private void configure(boolean armed) {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        info.eventTypes = armed ? AccessibilityEvent.TYPES_ALL_MASK : 0;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC; info.notificationTimeout = 0;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.packageNames = new String[] { armed ? controller.profile().packageName : ProbeProfile.OWN_CONTROL.packageName };
        setServiceInfo(info);
    }

    /** Arm by explicit Activity action only; schedule one deadline, never a permanent polling loop. */
    public boolean arm(ProbeProfile profile) { return arm(profile, ObservationCadence.Mode.NORMAL); }

    /** Bind an immutable mode; the event-only comparator is restricted to OWN_CONTROL. */
    public boolean arm(ProbeProfile profile, ObservationCadence.Mode mode) {
        if (mode == null || (mode == ObservationCadence.Mode.EVENT_ONLY_CONTROL && profile != ProbeProfile.OWN_CONTROL)
            || busy.get() || controller == null || !controller.arm(profile)) return false;
        projection.clear(); history = new SurveyEngine.History(); callbacks.set(0); snapshotSequence = 0;
        lastEventSurvey = lastAcceptedSurvey = Long.MIN_VALUE / 2; cadence = new ObservationCadence(mode, clock.elapsed());
        configure(true); handler.postDelayed(new Expiry(this), ProbeLimits.DURATION_MS);
        AccessibilityServiceInfo info = getServiceInfo();
        controller.record(new SafeRow(SafeRow.Kind.EVENT).number("eventTypesMask", info.eventTypes)
            .number("serviceFlags", info.flags).number("feedbackType", info.feedbackType)
            .number("notificationTimeoutMs", info.notificationTimeout).number("packageCount", info.packageNames.length)
            .number("capabilities", info.getCapabilities()).category("pollMode", mode).flag("configurationSnapshot", true));
        requestSurvey(null, SafeRow.Trigger.INITIAL, clock.uptime());
        scheduleTick(controller, controller.generation());
        return controller.active();
    }

    /** Clear callbacks and references and stop collection; Android's enabled setting may remain ON. */
    public void stop(SafeRow.Reason reason) {
        handler.removeCallbacksAndMessages(null);
        if (controller != null) controller.stop(reason);
        configure(false);
    }

    /** Receive only scoped event scalars; payload access always follows package/deadline checks. */
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        ProbeController core = controller;
        if (core == null || !core.active()) { configure(false); return; }
        try {
            if (event == null) { core.record(new SafeRow(SafeRow.Kind.CALLBACK_NULL)); return; }
            if (!core.accepts(event.getPackageName())) return;
            callbacks.incrementAndGet();
            SafeRow.Quality quality = core.eventQuality(event.getEventTime());
            core.record(new SafeRow(SafeRow.Kind.EVENT).number("eventTimeUptimeMs", event.getEventTime())
                .number("eventType", event.getEventType()).number("contentChangeTypes", event.getContentChangeTypes())
                .number("windowChangeTypes", event.getWindowChanges()).number("windowId", event.getWindowId())
                .number("recordCount", event.getRecordCount()).category("classCategory", projection.role(event.getClassName()))
                .number("classRef", projection.reference(event.getClassName())).category("quality", quality)
                .flag("password", event.isPassword()).flag("automaticAdDecision", false));
            if (!event.isPassword()) {
                projectRecord(event, SafeRow.Origin.EVENT_TEXT);
                int limit = Math.min(event.getRecordCount(), ProbeLimits.TEXT_ITEMS);
                for (int i = 0; i < limit && core.active(); i++) projectRecord(event.getRecord(i), SafeRow.Origin.RECORD);
                if (limit < event.getRecordCount()) core.record(new SafeRow(SafeRow.Kind.EVENT).flag("recordsTruncated", true));
            }
            if (quality != SafeRow.Quality.OK || !core.active()) return;
            if (clock.elapsed() - lastEventSurvey < ProbeLimits.EXPLORE_INTERVAL_MS || busy.get()) {
                core.record(new SafeRow(SafeRow.Kind.COALESCED).number("windowId", event.getWindowId())
                    .category("quality", SafeRow.Quality.COALESCED_SOURCE));
            } else { lastEventSurvey = clock.elapsed(); requestSurvey(event, SafeRow.Trigger.ANDROID_EVENT, clock.uptime()); }
        } catch (RuntimeException failure) {
            core.record(new SafeRow(SafeRow.Kind.ACCESS_FAILURE).category("quality", SafeRow.Quality.UNAVAILABLE));
        } finally { if (!core.active() && controller == core) stop(core.lastReason()); }
    }

    /** Reduce at most sixteen short text items plus description without keeping event objects. */
    private void projectRecord(AccessibilityRecord record, SafeRow.Origin origin) {
        if (record == null || record.isPassword()) return;
        List<CharSequence> items = record.getText(); int limit = Math.min(items.size(), ProbeLimits.TEXT_ITEMS);
        for (int i = 0; i < limit && controller.active(); i++) projectLabel(items.get(i), origin);
        projectLabel(record.getContentDescription(), origin);
        if (limit < items.size()) controller.record(new SafeRow(SafeRow.Kind.EVENT).flag("textItemsTruncated", true));
    }

    /** Serialize a closed label category, never its original text or matched numeric content. */
    private void projectLabel(CharSequence text, SafeRow.Origin origin) {
        SafeRow.Label label = projection.classify(text, false);
        if (label != SafeRow.Label.NONE) controller.record(new SafeRow(SafeRow.Kind.EVENT)
            .category("origin", origin).category("labelCategory", label).flag("automaticAdDecision", false));
    }

    /** Queue one traversal only, retaining one copied event until worker completion. */
    private void requestSurvey(AccessibilityEvent event, SafeRow.Trigger trigger, long dueUptime) {
        ProbeController core = controller; long generation = core.generation();
        if (!core.generationActive(generation)) return;
        if (clock.elapsed() - lastAcceptedSurvey < ProbeLimits.EXPLORE_INTERVAL_MS || !busy.compareAndSet(false, true)) {
            core.recordFor(generation, new SafeRow(SafeRow.Kind.TICK_SKIPPED).category("trigger", trigger)
                .category("quality", SafeRow.Quality.TIMEOUT_OR_DELAY).flag("automaticAdDecision", false)); return;
        }
        lastAcceptedSurvey = clock.elapsed();
        AccessibilityEvent copy = event == null || event.isPassword() ? null : AccessibilityEvent.obtain(event);
        try {
            worker.execute(new SurveyTask(this, core, generation, ++snapshotSequence, trigger, dueUptime, copy, history, cadence));
        } catch (RuntimeException failure) {
            if (copy != null) copy.recycle(); busy.set(false);
            core.recordFor(generation, new SafeRow(SafeRow.Kind.ACCESS_FAILURE).category("quality", SafeRow.Quality.INCOMPLETE));
            stop(SafeRow.Reason.INTERRUPTED);
        }
    }

    /** Schedule a future campaign tick from now; missed periods are not replayed. */
    private void scheduleTick(ProbeController core, long generation) {
        if (!core.generationActive(generation)) return;
        long delay = cadence.interval(clock.elapsed());
        if (delay > 0) handler.postDelayed(new PollTick(this, core, generation, clock.uptime() + delay), delay);
    }

    /** Read scoped event source and window roots under a shared 200-node, depth-12 budget. */
    private SafeRow.Quality explore(ProbeController core, long generation, long id, SafeRow.Trigger trigger,
            AccessibilityEvent event, SurveyEngine.History safeHistory) {
        NodeSurvey survey = new NodeSurvey(core, projection, generation, id, trigger, safeHistory);
        List<AccessibilityWindowInfo> windows = null; AccessibilityNodeInfo source = null;
        SafeRow.Quality outcome;
        try {
            if (event != null && core.generationActive(generation)) source = event.getSource();
            core.recordFor(generation, new SafeRow(SafeRow.Kind.SNAPSHOT).number("snapshotId", id)
                .category("trigger", trigger).flag("sourceAvailable", source != null).flag("sourceRequested", event != null));
            if (survey.remaining()) survey.root(getRootInActiveWindow(), SafeRow.Origin.ACTIVE_ROOT);
            if (core.generationActive(generation) && survey.remaining()) {
                windows = getWindows(); int limit = Math.min(windows.size(), ProbeLimits.WINDOWS);
                core.recordFor(generation, new SafeRow(SafeRow.Kind.SNAPSHOT).number("snapshotId", id)
                    .category("trigger", trigger).number("interactiveWindowCount", windows.size())
                    .flag("windowsTruncated", windows.size() > limit));
                if (windows.size() > limit) survey.incomplete();
                for (int i = 0; i < limit && survey.remaining(); i++) {
                    AccessibilityWindowInfo window = windows.get(i); int windowId = window.getId();
                    if (survey.hasWindow(windowId)) {
                        core.recordFor(generation, windowRow(id, trigger, window)); continue;
                    }
                    AccessibilityNodeInfo root = window.getRoot();
                    if (root != null && core.profile().accepts(root.getPackageName()))
                        core.recordFor(generation, windowRow(id, trigger, window));
                    survey.root(root, SafeRow.Origin.WINDOW_ROOT);
                }
            }
            if (source != null && survey.remaining() && !survey.hasWindow(source.getWindowId())) {
                survey.incomplete();
                AccessibilityNodeInfo owned = source; source = null; survey.root(owned, SafeRow.Origin.SOURCE_NODE);
            }
        } catch (RuntimeException failure) {
            survey.incomplete();
            core.recordFor(generation, new SafeRow(SafeRow.Kind.ACCESS_FAILURE).number("snapshotId", id)
                .category("quality", SafeRow.Quality.INCOMPLETE).category("trigger", trigger));
        } finally {
            if (source != null) source.recycle();
            if (windows != null) for (AccessibilityWindowInfo window : windows) window.recycle();
            outcome = survey.finish();
        }
        return outcome;
    }

    /** Project flags only for a window whose root has passed target-package admission. */
    private SafeRow windowRow(long id, SafeRow.Trigger trigger, AccessibilityWindowInfo window) {
        return new SafeRow(SafeRow.Kind.SNAPSHOT).number("snapshotId", id).category("trigger", trigger)
            .number("windowId", window.getId()).flag("active", window.isActive())
            .flag("focused", window.isFocused()).flag("accessibilityFocused", window.isAccessibilityFocused());
    }

    /** Stop at an Android interruption; never attempt to resume collection automatically. */
    @Override public void onInterrupt() { stop(SafeRow.Reason.INTERRUPTED); }

    /** Close a dying process/service without launching an Activity or touching another application. */
    @Override public void onDestroy() {
        stop(SafeRow.Reason.DISCONNECTED);
        for (Runnable pending : worker.shutdownNow()) if (pending instanceof SurveyTask) ((SurveyTask) pending).cancelPending();
        if (controller != null) controller.disconnect(SafeRow.Reason.DISCONNECTED);
        if (instance == this) instance = null; super.onDestroy();
    }

    /** Return the volatile controller for local UI/test assertions only. */
    public ProbeController controller() { return controller; }
    /** Return the app-private store for stopped local export/deletion. */
    public JournalStore store() { return store; }
    /** Return actual worker occupancy for bounded software checks. */
    boolean observing() { return busy.get(); }

    /** One-shot deadline task; event admission also checks elapsedRealtime after device sleep. */
    private static final class Expiry implements Runnable {
        private final ProbeService service;
        /** Bind a deadline task to the current service, without a retained Activity. */
        Expiry(ProbeService service) { this.service = service; }
        /** Stop after the deadline; this task does not poll or wake a sleeping television. */
        @Override public void run() { service.stop(SafeRow.Reason.TIME_LIMIT); }
    }

    /** Independent periodic request, scoped to one still-active campaign. */
    private static final class PollTick implements Runnable {
        private final ProbeService service; private final ProbeController core; private final long generation, due;
        /** Capture the exact controller, generation and expected uptime for this request. */
        PollTick(ProbeService service, ProbeController core, long generation, long due) {
            this.service = service; this.core = core; this.generation = generation; this.due = due;
        }
        /** Request a single scan and plan the next future period without catch-up. */
        @Override public void run() {
            if (service.controller != core || !core.generationActive(generation)) return;
            service.requestSurvey(null, SafeRow.Trigger.PERIODIC_TICK, due);
            service.scheduleTick(core, generation);
        }
    }

    /** One bounded worker operation; copied event ownership ends in its finally block. */
    private static final class SurveyTask implements Runnable {
        private final ProbeService service; private final ProbeController core;
        private final long generation, id, due; private final SafeRow.Trigger trigger;
        private final AccessibilityEvent event; private final SurveyEngine.History history;
        private final ObservationCadence cadence;
        /** Capture immutable scope, safe history and one optional owned event copy. */
        SurveyTask(ProbeService service, ProbeController core, long generation, long id, SafeRow.Trigger trigger,
                long due, AccessibilityEvent event, SurveyEngine.History history, ObservationCadence cadence) {
            this.service = service; this.core = core; this.generation = generation; this.id = id; this.trigger = trigger;
            this.due = due; this.event = event; this.history = history; this.cadence = cadence;
        }
        /** Release a queued copy cancelled before execution; an engaged task owns its own finally. */
        void cancelPending() { if (event != null) event.recycle(); service.busy.set(false); }

        /** Execute and measure one read; always leave an end result while the generation is active. */
        @Override public void run() {
            long start = service.clock.elapsed(), uptime = service.clock.uptime(), cpu = Process.getElapsedCpuTime();
            SafeRow.Quality outcome = SafeRow.Quality.FRESH_OK;
            try {
                if (!core.generationActive(generation)) return;
                core.recordFor(generation, new SafeRow(SafeRow.Kind.TICK_START).number("snapshotId", id).category("trigger", trigger)
                    .number("tickStartElapsedMs", start).number("tickStartUptimeMs", uptime)
                    .number("schedulingDelayMs", Math.max(0, uptime - due)).number("callbacksSincePrevious", service.callbacks.getAndSet(0))
                    .flag("automaticAdDecision", false));
                core.observationState(generation, true);
                outcome = service.explore(core, generation, id, trigger, event, history);
            } catch (RuntimeException failure) {
                outcome = SafeRow.Quality.INCOMPLETE;
            } finally {
                long memory = core.generationActive(generation) ? Debug.getPss() : 0;
                long duration = Math.max(0, service.clock.elapsed() - start);
                core.recordFor(generation, new SafeRow(SafeRow.Kind.TICK_END).number("snapshotId", id).category("trigger", trigger)
                    .category("quality", outcome).number("tickEndElapsedMs", service.clock.elapsed())
                    .number("tickEndUptimeMs", service.clock.uptime()).number("durationMs", duration)
                    .number("processCpuDeltaMs", Math.max(0, Process.getElapsedCpuTime() - cpu))
                    .number("processPssKb", memory).number("nativeHeapAllocatedBytes", Debug.getNativeHeapAllocatedSize())
                    .flag("automaticAdDecision", false));
                if (core.generationActive(generation) && cadence.overloaded(duration)) core.stop(SafeRow.Reason.OVERLOAD);
                core.observationState(generation, false);
                if (event != null) event.recycle(); service.busy.set(false);
                if (service.controller == core && !core.active()) service.handler.post(new Cleanup(service, core));
            }
        }
    }

    /** Main-thread mask cleanup after a worker limit; cannot act on a newly armed generation. */
    private static final class Cleanup implements Runnable {
        private final ProbeService service; private final ProbeController core;
        /** Retain only the adapter and the stopped controller being cleaned. */
        Cleanup(ProbeService service, ProbeController core) { this.service = service; this.core = core; }
        /** Apply OFF only while this exact controller remains stopped. */
        @Override public void run() { if (service.controller == core && !core.active()) service.stop(core.lastReason()); }
    }
}
