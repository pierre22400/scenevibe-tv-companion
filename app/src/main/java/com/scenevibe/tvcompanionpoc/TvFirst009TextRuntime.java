package com.scenevibe.tvcompanionpoc;

import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import com.scenevibe.tvcompanionpoc.calendar.MediaCalendarScheduler;
import com.scenevibe.tvcompanionpoc.calendar.MediaObservation;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;

/**
 * Small local composition around the unchanged M5 calendar, matcher and scene regie.
 * Only the separate software-qualification build and an explicit owner decision can arm it.
 * A cache receipt never grants MEDIA. Capture freshness is independent of provider-anchor age:
 * a stable PLAYING anchor is extrapolated by the existing probe, as on the qualified Sony.
 * No transport, Cloud client, historical ACK, installer write or second scheduler exists here.
 * 009-004 also composes this same runtime in the isolated explicit Sony qualification service.
 */
final class TvFirst009TextRuntime implements VideoInstallationRuntimePorts {
    /** Explicitly scoped qualification only; this is not an automatic ad/edition recognizer. */
    enum Decision { CLOSED, OWNER_LOCAL_AD_FREE_QUALIFICATION }
    /** Supply only the Android monotonic clock, or its deterministic software-test counterpart. */
    interface Clock { /** Return milliseconds on the lifetime's monotonic time base. */ long now(); }
    /** Bounded diagnostics are observations only and never an admission authority. */
    interface Observer { /** Receive a closed code, position and lifetime flag, never credentials or text. */ void changed(String code,long position,boolean active); }
    private static final long CAPTURE_TIMEOUT_MS = 2500L;
    private static final long WATCH_INTERVAL_MS = 250L;
    private final Handler owner;
    private final Clock clock;
    private final SceneRuntimeController regie;
    private final Observer observer;
    private final MediaCalendarScheduler calendar;
    private TvFirst009TextCache.Prepared cache, candidate;
    private VideoPreparedState video;
    private long epoch, renderGeneration, lastCapture = -1L, position = -1L;
    private String token;
    private ScheduledTrack.Event visibleWindow;
    private boolean active, playing;
    private Runnable watchdog;
    private MediaSessionProbe.Listener listener;

    /** Retain existing drawing/clock seams; construction never loads a cache or shows a scene. */
    TvFirst009TextRuntime(Handler owner, Clock clock, SceneRuntimeController.SceneSink sink) {
        this(owner,clock,sink,(code,position,active) -> {});
    }

    /** Add diagnostic reporting without changing the existing admission or calendar decisions. */
    TvFirst009TextRuntime(Handler owner, Clock clock, SceneRuntimeController.SceneSink sink,Observer observer) {
        if (owner == null || clock == null) throw new IllegalArgumentException("MISSING_LOCAL_OWNER");
        this.owner=owner; this.clock=clock; this.regie=new SceneRuntimeController(sink);
        this.observer=observer == null ? (code,position,active) -> {} : observer;
        this.calendar=new MediaCalendarScheduler(new MediaCalendarScheduler.Sink() {
            /** Keep activation labels fixed across eligibility callbacks. */
            @Override public void onEligibility(String captured, boolean eligible) {
                if (owns(captured)) {
                    if (!eligible) visibleWindow=null;
                    regie.onEligibility(eligible);
                }
            }
            /** Preserve M5 pause/display behavior without adding a wall-time event clock. */
            @Override public void onPlayback(String captured, boolean nowPlaying, boolean freeze) {
                if (owns(captured)) regie.onPlayback(nowPlaying, freeze);
            }
            /** Recheck the exact display window and cache authority immediately before SHOW. */
            @Override public void onDue(String captured, String id) {
                if (!owns(captured) || !playing || cache == null || !captureFresh()) return;
                ScheduledTrack.Event event=video.eventsById.get(id);
                if (!withinWindow(event,position)) return;
                cache.reference.whileCurrent(clock.now(), () -> {
                    regie.onEventDue(id,renderGeneration); visibleWindow=event;
                    publish("TEXT_RENDER_REQUESTED");
                });
            }
            /** Route expiry only to the producing activation's existing regie generation. */
            @Override public void onExpire(String captured, String id) {
                if (owns(captured)) {
                    regie.onEventExpired(id,renderGeneration);
                    if (visibleWindow != null && visibleWindow.id.equals(id)) visibleWindow=null;
                }
            }
        });
    }

    /** Arm immutable prepared values through the qualified Video activation sequence, once explicitly requested. */
    boolean start(TvFirst009TextCache.Prepared prepared, Decision decision) {
        if (!isOwnerThread()) return false;
        close();
        if (!BuildConfig.TVFIRST_TEXT_LOCAL_QUALIFICATION || decision != Decision.OWNER_LOCAL_AD_FREE_QUALIFICATION
                || prepared == null) return false;
        if (!prepared.reference.isCurrent(clock.now())) return false;
        candidate=prepared; token="tvfirst009-" + epoch;
        InstallationStatus result=new VideoManifestInstallationHandler().arm(prepared.installation,this);
        candidate=null;
        if (result != InstallationStatus.ARMED) { close(); return false; }
        final long captured=epoch;
        listener=new MediaSessionProbe.Listener() {
            /** Forward only a callback belonging to this explicit activation. */
            @Override public void onSnapshot(MediaSessionProbe.Snapshot snapshot) { observe(captured,snapshot); }
            /** Loss of a usable native session immediately removes any visible scene. */
            @Override public void onUnavailable() { unavailable(captured); }
        };
        watchdog=new Runnable() {
            /** Expire missing captures even if no MediaSession callback ever arrives again. */
            @Override public void run() {
                if (!active || captured != epoch) return;
                if (!cache.reference.isCurrent(clock.now())) { close(); publish("CACHE_OR_CONSENT_INVALID"); return; }
                if (!captureFresh()) unavailable(captured,"CAPTURE_TIMEOUT");
                if (active && captured == epoch) owner.postDelayed(this,WATCH_INTERVAL_MS);
            }
        };
        owner.postDelayed(watchdog,WATCH_INTERVAL_MS);
        publish("WAITING_MEDIA");
        return true;
    }

    /** Return the captured existing-probe listener, never a transport-capable player object. */
    MediaSessionProbe.Listener listener() { return listener; }

    /** Scope the inherited identity/position policy to one fresh local capture and safe playback states. */
    private void observe(long captured, MediaSessionProbe.Snapshot snapshot) {
        if (!active || captured != epoch || !isOwnerThread()) return;
        if (!cache.reference.isCurrent(clock.now())) { close(); publish("CACHE_OR_CONSENT_INVALID"); return; }
        lastCapture=clock.now();
        MediaObservation observation=VideoMediaObservationAdapter.observe(video.track,snapshot);
        boolean usable=snapshot != null && observation != null && observation.eligible()
                && snapshot.positionMs >= 0 && snapshot.updateAgeMs >= 0
                && Float.isFinite(snapshot.speed)
                && (snapshot.state == PlaybackState.STATE_PLAYING || snapshot.state == PlaybackState.STATE_PAUSED)
                && (snapshot.state != PlaybackState.STATE_PLAYING || snapshot.speed > 0f);
        if (!usable) {
            String code=snapshot == null ? "SESSION_UNAVAILABLE"
                    : observation == null || !observation.eligible() ? "MEDIA_IDENTITY_BLOCKED"
                    : snapshot.positionMs < 0 ? "POSITION_UNKNOWN" : "MEDIA_CLOCK_UNUSABLE";
            unavailable(captured,code); return;
        }
        playing=snapshot.state == PlaybackState.STATE_PLAYING;
        position=playing ? observation.positionMs() : snapshot.positionMs;
        if (position < 0L) { unavailable(captured,"POSITION_UNKNOWN"); return; }
        if (visibleWindow != null && !withinWindow(visibleWindow,position)) {
            regie.onEventExpired(visibleWindow.id,renderGeneration); visibleWindow=null;
        }
        try { calendar.onObservation(new MediaObservation(true,position,playing)); publish(playing ? "MEDIA_PLAYING" : "MEDIA_PAUSED"); }
        catch (RuntimeException failed) { close(); publish("RUNTIME_FAILED"); }
    }

    /** Never convert a missing position/session into retained eligibility or a frozen stale scene. */
    private void unavailable(long captured) {
        unavailable(captured,"SESSION_UNAVAILABLE");
    }

    /** Retain the specific refusal category while applying exactly the same ineligibility effect. */
    private void unavailable(long captured,String code) {
        if (!active || captured != epoch || !isOwnerThread()) return;
        position=-1L; playing=false; calendar.onUnavailable();
        publish(code);
    }

    /** A diagnostic failure cannot change rendering authority or expose exception contents. */
    private void publish(String code) {
        try { observer.changed(code,position,active); } catch (RuntimeException ignored) { /* Observational only. */ }
    }

    /** Let the service retire its notification after the runtime has closed itself. */
    boolean isActive() { return active; }

    /** Measure capture delivery, not how long a valid PLAYING provider anchor has been unchanged. */
    private boolean captureFresh() {
        long age=clock.now()-lastCapture;
        return lastCapture >= 0 && age >= 0 && age <= CAPTURE_TIMEOUT_MS;
    }

    /** A known editorial position must belong to the half-open original display window. */
    static boolean withinWindow(ScheduledTrack.Event event,long at) {
        return event != null && at >= event.startMs && event.durationMs > 0
                && at - event.startMs < event.durationMs;
    }

    /** Compare the calendar's captured activation label rather than adopting the current generation. */
    private boolean owns(String captured) { return token != null && token.equals(captured); }

    /** Invalidate queued callbacks before destroying current drawing and temporal ownership. */
    void close() {
        if (!isOwnerThread()) throw new IllegalStateException("OWNER_THREAD_REQUIRED");
        ++epoch; active=false; listener=null;
        if (watchdog != null) owner.removeCallbacks(watchdog);
        watchdog=null; token=null; cache=null; candidate=null; video=null; visibleWindow=null; position=-1; lastCapture=-1;
        calendar.clear(); regie.unload();
        publish("STOPPED");
    }

    /** Unknown advertising/content-time must stop this explicitly ad-free qualification, never retime a Track. */
    void timelineBecameUnknown() { close(); }

    /** Preserve the original main-thread runtime ownership boundary. */
    @Override public boolean isOwnerThread() { return Looper.myLooper() == owner.getLooper(); }
    /** This composition never constructs a legacy visual owner. */
    @Override public boolean retireLegacyVisualOwner() { return true; }
    /** Retire the existing manifested owner before any replacement preparation becomes active. */
    @Override public boolean retireManifestedVisualOwner() { regie.unload(); return true; }
    /** Load M5's exact prepared calendar, without editing event times or contents. */
    @Override public boolean loadPreparedVideo(VideoPreparedState prepared) {
        if (candidate == null || candidate.video != prepared) return false;
        video=prepared; calendar.load(prepared.calendar,token); return true;
    }
    /** Reuse the same manifest regie and keep it invisible until a real eligible observation. */
    @Override public boolean armPreparedManifest(long revision,OverlayManifest manifest) {
        if (candidate == null || candidate.installation.revision() != revision || video.manifest != manifest) return false;
        regie.loadManifest(revision,manifest); regie.onEligibility(false);
        renderGeneration=regie.currentGeneration(); return true;
    }
    /** Publish local ownership only after every original Video ARM stage succeeded. */
    @Override public boolean selectActiveRevision(long revision,boolean manifested) {
        if (!manifested || candidate == null || revision != candidate.installation.revision()) return false;
        cache=candidate; active=true; return true;
    }
    /** Partial ARM failure never leaves a visible or eligible owner. */
    @Override public void abortActivation() { close(); }
}
