package com.scenevibe.tvcompanionpoc;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;
import java.util.function.Predicate;
import com.scenevibe.tvcompanionpoc.tvfirst.MediaEvidence;
import com.scenevibe.tvcompanionpoc.tvfirst.PassiveMediaReader;

/**
 * Passive one-second probe of active Android MediaSessions.
 *
 * It reads only published session metadata/playback state and never sends
 * transport controls or interacts with the streaming application's UI.
 * Existing constructors retain one second. Multimedia can explicitly request
 * a bounded shorter interval on this same passive probe.
 */
public final class MediaSessionProbe {
    /** Optional, read-only experiment; it never substitutes the scheduler's listener. */
    interface ControllerObserver {
        /** Inspect the exact public facts already read by the historical probe. */
        void onController(MediaController controller, Snapshot snapshot, MediaMetadata metadata, PlaybackState state);
        /** Finish this batch, including the scheduler's unchanged selected session. */
        void onSelection(Snapshot selected);
        /** Record missing observations without manufacturing an advertising transition. */
        void onUnavailable();
        /** Unregister this experiment's callbacks when disabled or when the service stops. */
        void close();
    }
    /** Independent observation sink; it cannot receive a scheduling or rendering command. */
    public interface Observer {
        /** Receive one immutable batch whose programme/edition/context proofs remain unknown. */
        void onObservation(MediaEvidence evidence);
    }
    private static final String TAG = "SceneVibeMedia";
    private static final long SAMPLE_INTERVAL_MS = 1000L;

    public interface Listener {
        void onSnapshot(Snapshot snapshot);
        /** Absence/loss of a usable session must fail closed and hide tracked commentary. */
        default void onUnavailable() {}
    }

    /** Immutable playback sample forwarded to the SceneVibe scheduler. */
    public static final class Snapshot {
        public final String packageName;
        public final int state;
        public final String stateName;
        public final long positionMs;
        public final long estimatedPositionMs;
        public final float speed;
        public final long updateAgeMs;
        public final String mediaId;
        public final String title;
        public final String subtitle;
        public final long durationMs;
        public final Object sessionKey;
        public final long capturedAtMs, updatedAtMs;

        Snapshot(
                String packageName,
                int state,
                String stateName,
                long positionMs,
                long estimatedPositionMs,
                float speed,
                long updateAgeMs,
                String mediaId,
                String title,
                String subtitle,
                long durationMs) {
            this(packageName,state,stateName,positionMs,estimatedPositionMs,speed,updateAgeMs,
                    mediaId,title,subtitle,durationMs,null,-1L,-1L);
        }

        /** Retain a nonserialized session token and raw observation anchors for opt-in interruption guards. */
        Snapshot(String packageName,int state,String stateName,long positionMs,long estimatedPositionMs,
                float speed,long updateAgeMs,String mediaId,String title,String subtitle,long durationMs,
                Object sessionKey,long capturedAtMs,long updatedAtMs) {
            this.sessionKey=sessionKey;this.capturedAtMs=capturedAtMs;this.updatedAtMs=updatedAtMs;
            this.packageName = packageName;
            this.state = state;
            this.stateName = stateName;
            this.positionMs = positionMs;
            this.estimatedPositionMs = estimatedPositionMs;
            this.speed = speed;
            this.updateAgeMs = updateAgeMs;
            this.mediaId = mediaId;
            this.title = title;
            this.subtitle = subtitle;
            this.durationMs = durationMs;
        }
    }

    private final Context context;
    private final MediaSessionManager sessions;
    private final ComponentName listenerComponent;
    private final Listener listener;
    private final Observer observer;
    private final PassiveMediaReader observationReader;
    private final long sampleIntervalMs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean started;
    private ControllerObserver controllerObserver;
    private final Predicate<Snapshot> preferred;

    private final Runnable sampler = new Runnable() {
        @Override
        public void run() {
            if (!started) return;
            sample();
            handler.postDelayed(this, sampleIntervalMs);
        }
    };

    /** Preserve the historical no-listener construction. */
    public MediaSessionProbe(Context context) {
        this(context, null);
    }

    /** Preserve the historical listener and its sampling branch exactly by default. */
    public MediaSessionProbe(Context context, Listener listener) {
        this(context, listener, null);
    }

    /** Select a passive observation branch without constructing a second probe or transport. */
    public MediaSessionProbe(Context context, Listener listener, Observer observer) {
        this(context,listener,observer,SAMPLE_INTERVAL_MS);
    }

    /** Bound an explicit multimedia cadence while keeping every historical constructor at one second. */
    public MediaSessionProbe(Context context, Listener listener, Observer observer,long sampleIntervalMs) {
        this(context,listener,observer,sampleIntervalMs,null);
    }

    /** Prefer an already-admitted identity while leaving historical session selection unchanged by default. */
    public MediaSessionProbe(Context context,Listener listener,Observer observer,long sampleIntervalMs,Predicate<Snapshot> preferred) {
        this.preferred=preferred;
        if(sampleIntervalMs<250L || sampleIntervalMs>SAMPLE_INTERVAL_MS)throw new IllegalArgumentException("SAMPLE_INTERVAL_OUT_OF_POLICY");
        if (listener != null && observer != null) throw new IllegalArgumentException("One probe mode only");
        this.sampleIntervalMs=sampleIntervalMs;
        this.context = context;
        this.listener = listener;
        this.observer = observer;
        observationReader = observer == null ? null : new PassiveMediaReader();
        sessions = context.getSystemService(MediaSessionManager.class);
        listenerComponent = NotificationAccess.component(context);
    }

    /** Start the existing sampler once; historical constructors retain the one-second cadence. */
    public void start() {
        if (started) return;
        started = true;
        Log.i(TAG, "Passive MediaSession probe started; intervalMs=" + sampleIntervalMs);
        sampler.run();
    }

    /** Query currently published public MEDIA facts synchronously; never reuse a cached application observation. */
    public void captureNow() { if(started)sample(); }

    /** Attach only after explicit opt-in; null restores the reference probe path. */
    void setControllerObserver(ControllerObserver next) {
        if(controllerObserver==next)return;
        if(controllerObserver!=null)try{controllerObserver.close();}catch(RuntimeException ignored){/* Optional diagnostic only. */}
        controllerObserver=next;
    }

    /** Contain an experimental observer failure without altering playback, identity or scheduling. */
    private void diagnostic(Runnable work) {
        try{work.run();}catch(RuntimeException ignored){Log.w("SceneVibeAdProbe","event=READ_FAILED payloadOmitted=true");}
    }

    /** Stop future sampling; the captured service writer also rejects late callbacks. */
    public void stop() {
        started = false;
        handler.removeCallbacks(sampler);
        setControllerObserver(null);
        Log.i(TAG, "Passive MediaSession probe stopped");
    }

    private String lastProbeState="";
    private Object lastSelectedSession;
    private String lastSelection="";

    /** Log v22 state transitions once; no MEDIA payload or repeated 250 ms absence messages. */
    private void probeState(String code){if(!BuildConfig.ADSYNC_NONDESTRUCTIVE || !code.equals(lastProbeState)){Log.i(TAG,code);lastProbeState=code;}}

    /** Dispatch one exclusive probe branch; the legacy branch below is retained unchanged. */
    private void sample() {
        if (observer != null) {
            observer.onObservation(observationReader.capture(sessions, listenerComponent,
                    NotificationAccess.isGranted(context), android.provider.Settings.canDrawOverlays(context)));
            return;
        }
        if (!NotificationAccess.isGranted(context)) {
            probeState("ACCESS_REQUIRED notificationListener=false");
            unavailable();
            return;
        }
        if (sessions == null) {
            Log.w(TAG, "MEDIA_SESSION_MANAGER_UNAVAILABLE");
            unavailable();
            return;
        }

        final List<MediaController> controllers;
        try {
            controllers = sessions.getActiveSessions(listenerComponent);
        } catch (SecurityException error) {
            Log.w(TAG, "ACCESS_DENIED active sessions unavailable", error);
            unavailable();
            return;
        } catch (RuntimeException error) {
            Log.w(TAG, "PROBE_FAILED active sessions query", error);
            unavailable();
            return;
        }

        if (controllers.isEmpty()) {
            probeState("NO_ACTIVE_SESSIONS");
            unavailable();
            return;
        }

        if(BuildConfig.ADSYNC_NONDESTRUCTIVE)probeState("ACTIVE_SESSIONS_AVAILABLE");
        java.util.ArrayList<Snapshot> candidates=new java.util.ArrayList<>();
        for (int index = 0; index < controllers.size(); index++) {
            try{candidates.add(snapshotController(index,controllers.get(index)));}
            catch(RuntimeException vanished){if(!BuildConfig.ADSYNC_NONDESTRUCTIVE)throw vanished;probeState("SESSION_READ_UNAVAILABLE");}
        }
        Snapshot primary=select(candidates,preferred,BuildConfig.ADSYNC_NONDESTRUCTIVE?lastSelectedSession:null);
        if(controllerObserver!=null)diagnostic(()->controllerObserver.onSelection(primary));
        if(BuildConfig.ADSYNC_NONDESTRUCTIVE && primary!=null){
            int compatible=0;for(Snapshot item:candidates)if(preferred!=null && preferred.test(item))compatible++;
            String selection="active="+candidates.size()+" compatible="+compatible+" selected="+(preferred!=null && preferred.test(primary)?"COMPATIBLE":"INCOMPATIBLE")+" playback="+primary.stateName;
            if(!java.util.Objects.equals(lastSelectedSession,primary.sessionKey) || !selection.equals(lastSelection)){
                Log.i(TAG,"SELECTION "+selection+" rawMs="+primary.positionMs+" estimatedMs="+primary.estimatedPositionMs+" anchorAgeMs="+primary.updateAgeMs+" speed="+primary.speed);
                lastSelection=selection;
            }
            lastSelectedSession=primary.sessionKey;
        }
        if (listener != null && primary != null) listener.onSnapshot(primary);
        else if(primary==null)unavailable();
    }

    /** Keep a compatible active session stable on ties; identity and PLAYING priority remain mandatory. */
    static Snapshot select(java.util.List<Snapshot> candidates,Predicate<Snapshot> preferred,Object previous){
        Snapshot primary=null,compatible=null;
        for(Snapshot snapshot:candidates){
            if(primary==null || primary.state!=PlaybackState.STATE_PLAYING && snapshot.state==PlaybackState.STATE_PLAYING)primary=snapshot;
            if(preferred!=null && preferred.test(snapshot) && (compatible==null
                    || compatible.state!=PlaybackState.STATE_PLAYING && snapshot.state==PlaybackState.STATE_PLAYING
                    || compatible.state==snapshot.state && previous!=null && previous.equals(snapshot.sessionKey)))compatible=snapshot;
        }
        return compatible==null?primary:compatible;
    }

    /** Preserve historical absence forwarding without constructing an observation fallback. */
    private void unavailable() {
        if(controllerObserver!=null)diagnostic(()->controllerObserver.onUnavailable());
        if (listener != null) listener.onUnavailable();
    }

    /** Read the historical scalar snapshot and preserve its original logging and estimate. */
    private Snapshot snapshotController(int index, MediaController controller) {
        PlaybackState state = controller.getPlaybackState();
        MediaMetadata metadata = controller.getMetadata();

        int stateCode = state == null ? PlaybackState.STATE_NONE : state.getState();
        long positionMs = state == null ? -1L : state.getPosition();
        float speed = state == null ? 0f : state.getPlaybackSpeed();
        long updatedAtMs = state == null ? -1L : state.getLastPositionUpdateTime();
        long capturedAtMs=SystemClock.elapsedRealtime();
        long updateAgeMs = updatedAtMs <= 0L ? -1L
                : Math.max(0L, capturedAtMs - updatedAtMs);
        long estimatedPositionMs = estimatePositionMs(state, positionMs, speed, updateAgeMs);

        String mediaId = metadataText(metadata, MediaMetadata.METADATA_KEY_MEDIA_ID);
        String title = metadataText(metadata, MediaMetadata.METADATA_KEY_TITLE);
        if (title.isEmpty()) {
            title = metadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        }
        String subtitle = metadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        long durationMs = metadata == null
                ? -1L : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        String readableState = stateName(state);

        if(!BuildConfig.ADSYNC_PROTOTYPE)Log.i(TAG,
                "SESSION index=" + index
                + " package=" + controller.getPackageName()
                + " state=" + readableState
                + " positionMs=" + positionMs
                + " estimatedMs=" + estimatedPositionMs
                + " speed=" + speed
                + " updateAgeMs=" + updateAgeMs
                + " mediaId=" + safe(mediaId)
                + " durationMs=" + durationMs
                + " title=" + safe(title)
                + " subtitle=" + safe(subtitle));

        Snapshot snapshot = new Snapshot(
                controller.getPackageName(),
                stateCode,
                readableState,
                positionMs,
                estimatedPositionMs,
                speed,
                updateAgeMs,
                mediaId,
                title,
                subtitle,
                durationMs,controller.getSessionToken(),capturedAtMs,updatedAtMs);
        if(controllerObserver!=null)diagnostic(()->controllerObserver.onController(controller,snapshot,metadata,state));
        return snapshot;
    }

    /** Preserve the historic estimate; the independent observation reader retains the raw anchor. */
    private long estimatePositionMs(
            PlaybackState state, long positionMs, float speed, long updateAgeMs) {
        if (state == null || positionMs < 0L || updateAgeMs < 0L) return positionMs;
        int code = state.getState();
        if (code != PlaybackState.STATE_PLAYING
                && code != PlaybackState.STATE_FAST_FORWARDING
                && code != PlaybackState.STATE_REWINDING) {
            return positionMs;
        }
        return Math.max(0L, positionMs + Math.round(updateAgeMs * speed));
    }

    /** Preserve the historic absent-string representation. */
    private String metadataText(MediaMetadata metadata, String key) {
        if (metadata == null) return "";
        CharSequence value = metadata.getText(key);
        return value == null ? "" : value.toString();
    }

    /** Preserve the existing bounded state labels for historical listeners. */
    private String stateName(PlaybackState state) {
        if (state == null) return "NONE";
        switch (state.getState()) {
            case PlaybackState.STATE_NONE: return "NONE";
            case PlaybackState.STATE_STOPPED: return "STOPPED";
            case PlaybackState.STATE_PAUSED: return "PAUSED";
            case PlaybackState.STATE_PLAYING: return "PLAYING";
            case PlaybackState.STATE_FAST_FORWARDING: return "FAST_FORWARDING";
            case PlaybackState.STATE_REWINDING: return "REWINDING";
            case PlaybackState.STATE_BUFFERING: return "BUFFERING";
            case PlaybackState.STATE_ERROR: return "ERROR";
            case PlaybackState.STATE_CONNECTING: return "CONNECTING";
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS: return "SKIPPING_TO_PREVIOUS";
            case PlaybackState.STATE_SKIPPING_TO_NEXT: return "SKIPPING_TO_NEXT";
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM: return "SKIPPING_TO_QUEUE_ITEM";
            default: return "UNKNOWN_" + state.getState();
        }
    }

    /** Preserve the historical single-line diagnostic representation. */
    private String safe(String value) {
        if (value == null || value.isEmpty()) return "-";
        return value.replace('\n', ' ').replace('\r', ' ').replace(' ', '_');
    }
}
