package com.scenevibe.accessibilityprobe003;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityRecord;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Passive Android adapter. It has no transport or Companion reference. Android binds it only
 * after manual enablement. Live collections are voluntary, package-scoped and process-volatile.
 */
public final class ProbeService extends AccessibilityService {
    private static volatile ProbeService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextProjection projection = new TextProjection();
    private final Set<Integer> pending = new HashSet<>();
    private final ProbeClock clock = new AndroidProbeClock();
    private JournalStore store;
    private ProbeController controller;

    /** Return a live process-local connection for the setup Activity; no exported control binder. */
    public static ProbeService current() { return instance; }

    /** Initialize private storage and disarm on every Android connection, including reconnections. */
    @Override protected void onServiceConnected() {
        handler.removeCallbacksAndMessages(null);
        pending.clear(); projection.clear();
        try {
            if (controller != null) controller.disconnect(SafeRow.Reason.RECONNECTED);
            store = new JournalStore(new File(getFilesDir(), "probe003"));
            store.prune(clock.wall());
            controller = new ProbeController(clock, store);
            controller.connect();
            instance = this;
            configure(false);
        } catch (IOException failure) { instance = null; configure(false); }
    }

    /** Configure the actual single package and mask; OFF requests no diagnostic callbacks. */
    private void configure(boolean armed) {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        info.eventTypes = armed ? AccessibilityEvent.TYPES_ALL_MASK : 0;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 0;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.packageNames = new String[] { armed ? controller.profile().packageName
            : ProbeProfile.OWN_CONTROL.packageName };
        setServiceInfo(info);
    }

    /** Arm by explicit Activity action only; schedule one deadline, never a permanent polling loop. */
    public boolean arm(ProbeProfile profile) {
        if (controller == null || !controller.arm(profile)) return false;
        projection.clear(); pending.clear();
        configure(true);
        handler.postDelayed(new Expiry(this), ProbeLimits.DURATION_MS);
        AccessibilityServiceInfo info = getServiceInfo();
        controller.record(new SafeRow(SafeRow.Kind.EVENT).number("eventTypesMask", info.eventTypes)
            .number("serviceFlags", info.flags).number("feedbackType", info.feedbackType)
            .number("notificationTimeoutMs", info.notificationTimeout)
            .number("packageCount", info.packageNames == null ? 0 : info.packageNames.length)
            .number("capabilities", info.getCapabilities()).flag("configurationSnapshot", true));
        return controller.active();
    }

    /** Clear callbacks and references and stop collection; Android's enabled setting may remain ON. */
    public void stop(SafeRow.Reason reason) {
        handler.removeCallbacksAndMessages(null); pending.clear();
        if (controller != null) controller.stop(reason);
        projection.clear(); configure(false);
    }

    /** Receive only scoped event scalars; payload access always follows package/deadline checks. */
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (controller == null || !controller.active()) { configure(false); return; }
        try {
            if (event == null) { controller.record(new SafeRow(SafeRow.Kind.CALLBACK_NULL)); return; }
            if (!controller.accepts(event.getPackageName())) return;
            SafeRow.Quality quality = controller.eventQuality(event.getEventTime());
            SafeRow row = new SafeRow(SafeRow.Kind.EVENT).number("eventTimeUptimeMs", event.getEventTime())
                .number("eventType", event.getEventType()).number("contentChangeTypes", event.getContentChangeTypes())
                .number("windowChangeTypes", event.getWindowChanges()).number("windowId", event.getWindowId())
                .number("recordCount", event.getRecordCount()).category("classCategory", projection.role(event.getClassName()))
                .number("classRef", projection.reference(event.getClassName())).category("quality", quality)
                .flag("password", event.isPassword()).flag("automaticAdDecision", false);
            controller.record(row);
            if (!event.isPassword()) {
                projectRecord(event, SafeRow.Origin.EVENT_TEXT);
                int limit = Math.min(event.getRecordCount(), ProbeLimits.TEXT_ITEMS);
                for (int i = 0; i < limit && controller.active(); i++) projectRecord(event.getRecord(i), SafeRow.Origin.RECORD);
                if (limit < event.getRecordCount()) controller.record(new SafeRow(SafeRow.Kind.EVENT).flag("recordsTruncated", true));
            }
            if (quality != SafeRow.Quality.OK || !controller.active()) return;
            int id = event.getWindowId();
            if (controller.allowExplore(id)) explore(event, id);
            else {
                controller.record(new SafeRow(SafeRow.Kind.COALESCED).number("windowId", id)
                    .category("quality", SafeRow.Quality.COALESCED_SOURCE));
                if (pending.size() < ProbeLimits.WINDOWS && pending.add(id))
                    handler.postDelayed(new DeferredSurvey(this, id), ProbeLimits.EXPLORE_INTERVAL_MS);
            }
        } catch (RuntimeException failure) {
            controller.record(new SafeRow(SafeRow.Kind.ACCESS_FAILURE).category("quality", SafeRow.Quality.UNAVAILABLE));
        } finally { if (!controller.active()) stop(controller.lastReason()); }
    }

    /** Reduce at most sixteen short text items plus description without keeping event objects. */
    private void projectRecord(AccessibilityRecord record, SafeRow.Origin origin) {
        if (record == null || record.isPassword()) return;
        List<CharSequence> items = record.getText();
        int limit = Math.min(items.size(), ProbeLimits.TEXT_ITEMS);
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

    /** Read scoped event source and window roots under a shared 200-node, depth-12 budget. */
    private void explore(AccessibilityEvent event, int admittedWindow) {
        if (!controller.active()) return;
        NodeSurvey survey = new NodeSurvey(controller, projection);
        Set<Integer> done = new HashSet<>();
        done.add(admittedWindow);
        try {
            if (event != null) survey.root(event.getSource(), SafeRow.Origin.SOURCE_NODE);
            else controller.record(new SafeRow(SafeRow.Kind.SNAPSHOT).category("quality", SafeRow.Quality.COALESCED_SOURCE)
                .flag("sourceAvailable", false));
            if (survey.remaining()) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null) survey.root(null, SafeRow.Origin.ACTIVE_ROOT);
                else if (done.contains(root.getWindowId()) || controller.allowExplore(root.getWindowId())) {
                    done.add(root.getWindowId()); survey.root(root, SafeRow.Origin.ACTIVE_ROOT);
                } else root.recycle();
            }
            List<AccessibilityWindowInfo> windows = getWindows();
            int limit = Math.min(windows.size(), ProbeLimits.WINDOWS);
            for (int i = 0; i < limit && survey.remaining(); i++) {
                AccessibilityWindowInfo window = windows.get(i);
                int id = window.getId();
                if (!done.contains(id) && !controller.allowExplore(id)) continue;
                done.add(id);
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && controller.profile().accepts(root.getPackageName())) {
                    controller.record(new SafeRow(SafeRow.Kind.SNAPSHOT).number("windowId", id)
                        .flag("active", window.isActive()).flag("focused", window.isFocused())
                        .flag("accessibilityFocused", window.isAccessibilityFocused()));
                }
                survey.root(root, SafeRow.Origin.WINDOW_ROOT);
            }
            controller.record(new SafeRow(SafeRow.Kind.SNAPSHOT).number("interactiveWindowCount", windows.size())
                .flag("windowsTruncated", windows.size() > limit));
            for (AccessibilityWindowInfo window : windows) window.recycle();
        } catch (RuntimeException failure) {
            controller.record(new SafeRow(SafeRow.Kind.ACCESS_FAILURE).category("quality", SafeRow.Quality.UNAVAILABLE));
        } finally { survey.finish(); }
    }

    /** Stop at an Android interruption; never attempt to resume collection automatically. */
    @Override public void onInterrupt() { stop(SafeRow.Reason.INTERRUPTED); }

    /** Close a dying process/service without launching an Activity or touching another application. */
    @Override public void onDestroy() {
        stop(SafeRow.Reason.DISCONNECTED);
        if (controller != null) controller.disconnect(SafeRow.Reason.DISCONNECTED);
        if (instance == this) instance = null;
        super.onDestroy();
    }

    /** Return the volatile controller for local UI/test assertions only. */
    public ProbeController controller() { return controller; }
    /** Return the app-private store for stopped local export/deletion. */
    public JournalStore store() { return store; }

    /** One-shot deadline task; event admission also checks elapsedRealtime after device sleep. */
    private static final class Expiry implements Runnable {
        private final ProbeService service;
        /** Bind a deadline task to the current service, without a retained Activity. */
        Expiry(ProbeService service) { this.service = service; }
        /** Stop after the deadline; this task does not poll or wake a sleeping television. */
        @Override public void run() { service.stop(SafeRow.Reason.TIME_LIMIT); }
    }

    /** Coalesced request retains only a numeric window ID, never an AccessibilityEvent or its text. */
    private static final class DeferredSurvey implements Runnable {
        private final ProbeService service;
        private final int windowId;
        /** Store the last relevant window scope for one delayed, event-driven survey. */
        DeferredSurvey(ProbeService service, int windowId) { this.service = service; this.windowId = windowId; }
        /** Explore current roots if still admitted; the original event source is explicitly unavailable. */
        @Override public void run() {
            service.pending.remove(windowId);
            if (service.controller.active() && service.controller.allowExplore(windowId)) service.explore(null, windowId);
            if (!service.controller.active()) service.stop(service.controller.lastReason());
        }
    }
}
