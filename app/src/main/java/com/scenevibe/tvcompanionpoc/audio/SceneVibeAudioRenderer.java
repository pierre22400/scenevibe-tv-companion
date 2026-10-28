package com.scenevibe.tvcompanionpoc.audio;

import java.util.HashMap;
import java.util.Map;

/**
 * Android-free CORE that restitutes SceneVibe AUDIO comments by orchestrating a
 * {@link LocalAudioPort} PURELY REACTIVELY from the existing scheduler callbacks.
 *
 * <p>Single media-time authority. This class owns NO clock: it has no
 * handler/delayed-post/timer/thread timer of its own and it
 * NEVER re-derives or counts media position. Every transition is driven by the
 * same {@code onDue}/{@code onExpire}/{@code onPlayback}/{@code onEligibility}
 * stream that the TEXT path consumes, so AUDIO fires at the SAME media instant as
 * TEXT. The scheduler already consumes/re-arms on seek/expire using the verified
 * constants on {@code main} (FORWARD_SEEK_THRESHOLD_MS=5000,
 * BACKWARD_SEEK_THRESHOLD_MS=2000, MAX_LATE_MS=2000); this renderer does NOT
 * re-implement any of that logic.</p>
 *
 * <p>Capability profile, mirrored from the A1 {@code VoiceCoexistenceProbe}: NO
 * AudioFocus API anywhere (the CORE has no focus method to call), NO
 * system stream-volume mutation, and NO transport (PLAY/PAUSE/SEEK) toward Prime. The
 * SceneVibe volume is only a LOCAL gain applied to {@link LocalAudioPort#setGain}.</p>
 *
 * <h2>Transition map (which callback drives what)</h2>
 * <ul>
 *   <li>{@code onDue(token, eventId)} for an eligible AUDIO event in PLAYING →
 *       {@code prepare} (if not pre-prepared) + {@code start}; de-duplicated so a
 *       repeated {@code onDue} for the same eventId does not restart it.</li>
 *   <li>{@code onExpire(token, eventId)} → clean {@code stop} + {@code release}.</li>
 *   <li>{@code onEligibility(token, false)} → safe {@code stop} + {@code release},
 *       no residual playback.</li>
 *   <li>{@code onPlayback(token, playing=false, ...)} → apply the active event's
 *       {@link AudioEvent.PausePolicy} (default STOP_ON_PAUSE): {@code stop} +
 *       {@code release}; a consumed phrase is NOT replayed on resume.</li>
 *   <li>Episode change / media mismatch surfaces as {@code onEligibility(false)}
 *       from the scheduler → previous-episode audio stops immediately.</li>
 *   <li>AUDIO OFF (UI) → {@link #setAudioEnabled(boolean) setAudioEnabled(false)}:
 *       immediate {@code stop} + {@code release}, independent of the scheduler.</li>
 *   <li>Resource/decode error from the port → {@link #onError()}: safe stop,
 *       truthful terminal state, never interpreted as coexistence.</li>
 * </ul>
 *
 * <p>Routing by event type is the caller's responsibility and is explicit here:
 * only {@link AudioEvent.Type#AUDIO} events are offered to this renderer; TEXT
 * events keep going to the existing TEXT renderer (this class never sees them).</p>
 *
 * <p>Codec lesson (A1): prefer a bundled ADTS/AAC cue; AVOID the M4A/MP4 container
 * which the Sony/MediaTek OEM extractor rejected. Enforced in the adapter; recorded
 * here for the record.</p>
 */
public final class SceneVibeAudioRenderer {
    /** A renderer request is separate from native PREPARED/STARTED acknowledgements. */
    public enum DueResult { REQUESTED, CHANNEL_OFF, PLAYBACK_PAUSED, STALE_ACTIVATION, UNKNOWN_EVENT, ALREADY_ACTIVE, OTHER_EVENT_ACTIVE }
    private final java.util.Map<String,Long> nativeOffsets = new java.util.HashMap<>();
    private boolean frozen;
    private float localGain=1f;

    private final LocalAudioPort port;

    /** Catalog of eligible AUDIO events, keyed by eventId, for the active token. */
    private final Map<String, AudioEvent> catalog = new HashMap<>();

    /** Opaque scheduler activation token the catalog belongs to; null when none. */
    private String catalogToken;

    /** eventId currently prepared/started by the port; null when the port is idle. */
    private String activeEventId;

    /** True once {@link #prepare} has been issued for {@link #activeEventId}. */
    private boolean activePrepared;

    /** True once {@link #start} has been issued for {@link #activeEventId}. */
    private boolean activeStarted;

    /** AUDIO ON/OFF UI toggle. When false the renderer stays silent. */
    private boolean audioEnabled = true;

    /** Last observed PLAYING state from {@code onPlayback}; AUDIO fires only PLAYING. */
    private boolean playing;

    /** Monotonic counter of {@link #tick()} samples, for FEAT-004 timing analysis. */
    private long sampleCount;

    /** Sample index captured when {@code prepare} was last issued; -1 if none. */
    private long prepareSample = -1L;

    /** Sample index captured when {@code start} was last issued; -1 if none. */
    private long startSample = -1L;

    /** Construct the renderer over its local audio seam. */
    public SceneVibeAudioRenderer(LocalAudioPort port) {
        if (port == null) {
            throw new IllegalArgumentException("port");
        }
        this.port = port;
    }

    /**
     * Register the eligible AUDIO events for a scheduler token. ADDITIVE routing:
     * only {@link AudioEvent.Type#AUDIO} events belong here. Replacing the token's
     * catalog (e.g. a new episode's Track) does not by itself stop playback; the
     * scheduler drives stop via {@code onEligibility(false)}.
     */
    public void setCatalog(String token, Iterable<AudioEvent> events) {
        // C2 (pre-preparation invalidation): a Track change replaces the catalog, so a
        // resource pre-prepared for the OLD track must be released rather than left held
        // or, worse, started later against the wrong catalog. If something was merely
        // pre-prepared (prepared, not yet audibly started) release it now so no stale
        // prepared asset survives the Track change. An already-playing comment is left to
        // the scheduler's onEligibility(false) stop (episode change path).
        if (activeEventId != null && activePrepared && !activeStarted) {
            teardown();
        }
        catalog.clear();
        nativeOffsets.clear();
        catalogToken = token;
        if (events != null) {
            for (AudioEvent event : events) {
                if (event != null && event.type() == AudioEvent.Type.AUDIO) {
                    if (event.pausePolicy() == AudioEvent.PausePolicy.FREEZE_AND_RESUME && !port.supportsNativeOffsets())
                        throw new IllegalArgumentException("NATIVE_OFFSET_PROFILE_UNSUPPORTED");
                    catalog.put(event.eventId(), event);
                }
            }
        }
    }

    /**
     * Pre-prepare an eligible AUDIO asset AHEAD of its due instant WITHOUT emitting
     * sound ({@code prepare != start}), to reduce player-prep startup latency
     * (design section 6). No-op if AUDIO is OFF, the event is unknown, or something
     * is already active. Does not change the 1s sampling cadence.
     *
     * <p>C2 (AUDIO 002B) decision: pre-preparation is NOT auto-wired into the live
     * OverlayService lifecycle. The only scheduler edge that could trigger it in-app
     * is a per-position "about to be due" signal, which the passive
     * {@link com.scenevibe.tvcompanionpoc.calendar.MediaCalendarScheduler} does NOT
     * emit (it emits {@code onDue} exactly AT the media instant). Driving a look-ahead
     * from the renderer would require a second clock / position counter, which the
     * single-media-time-authority constraint forbids. Per the mandate's "only if a
     * demonstrated benefit" clause, {@code prePrepare} therefore remains a
     * spec/latency-optimization hook exercised by tests only. Whichever path a future
     * adapter takes, this hook is correctly INVALIDATED by the existing lifecycle
     * edges: a Track change ({@link #setCatalog}) releases a pre-prepared-not-started
     * asset, eligibility loss ({@code onEligibility(token,false)}) and AUDIO disable
     * ({@link #setAudioEnabled(boolean) setAudioEnabled(false)}) both {@code teardown}.
     */
    public void prePrepare(String eventId) {
        if (!audioEnabled || activeEventId != null) {
            return;
        }
        AudioEvent event = catalog.get(eventId);
        if (event == null) {
            return;
        }
        activeEventId = eventId;
        activePrepared = true;
        activeStarted = false;
        prepareSample = sampleCount;
        startSample = -1L;
        port.prepare(event.assetRef());
    }

    // ----- Scheduler callback seam (same stream the TEXT path consumes) -----

    /**
     * Eligibility edge from the scheduler. {@code eligible=false} (session lost,
     * media mismatch, episode change) → safe stop + release, no residual playback.
     * {@code eligible=true} simply records the active token.
     */
    public void onEligibility(String token, boolean eligible) {
        if (!eligible) {
            teardown();
            return;
        }
        catalogToken = token;
    }

    /**
     * Observed PLAYING/paused edge. On pause, apply the active event's pause policy
     * (default STOP_ON_PAUSE): stop + release. A consumed phrase is NOT replayed on
     * resume because the scheduler does not re-emit {@code onDue} for it.
     */
    public void onPlayback(String token, boolean playing, boolean freezeOnPause) {
        if (catalogToken != null && !catalogToken.equals(token)) return;
        this.playing = playing;
        if (playing && frozen && activeEventId != null) {
            port.resume(); frozen=false;
        }
        if (playing || activeEventId == null) {
            return;
        }
        AudioEvent event = catalog.get(activeEventId);
        if (event == null || event.pausePolicy() == AudioEvent.PausePolicy.STOP_ON_PAUSE) {
            teardown();
        } else if (!frozen) {
            port.pause(); frozen=true;
        }
    }

    /** Apply only offsets selected by the active calendar; no autonomous audio timeline exists. */
    public void onAudioPosition(String token,String eventId,long offsetMs,boolean discontinuity) {
        if (catalogToken != null && !catalogToken.equals(token)) return;
        AudioEvent event=catalog.get(eventId);
        if (event == null || offsetMs < 0 || offsetMs >= event.durationMs()) return;
        nativeOffsets.put(eventId,offsetMs);
        if (discontinuity && eventId.equals(activeEventId) && port.supportsNativeOffsets()) port.seekTo(offsetMs);
    }

    /**
     * Due edge for {@code eventId} at the current media position. For an eligible
     * AUDIO event while PLAYING: prepare (unless pre-prepared) then start. Repeated
     * {@code onDue} for the same already-active eventId is a no-op (no duplicates,
     * no restart). An {@code onDue} for a different event while one is active is
     * ignored (the scheduler owns window arbitration).
     */
    public void onDue(String token, String eventId) {
        onDueResult(token,eventId);
    }

    /** Return the precise routing refusal while retaining the original onDue entry and native player policy. */
    public DueResult onDueResult(String token,String eventId) {
        if(!audioEnabled)return DueResult.CHANNEL_OFF;
        if(!playing)return DueResult.PLAYBACK_PAUSED;
        // C3 (session tokens): a due bearing a token that is NOT the current catalog
        // token comes from a stale/previous activation. The scheduler only emits the
        // active activation's token on main, but we defend against a late/reordered
        // callback: a stale-token due must neither start a new comment nor restart or
        // disturb one already playing under the current (live) token. Ignore it.
        if (catalogToken != null && !catalogToken.equals(token)) {
            return DueResult.STALE_ACTIVATION;
        }
        AudioEvent event = catalog.get(eventId);
        if (event == null) {
            return DueResult.UNKNOWN_EVENT;
        }
        if (eventId.equals(activeEventId)) {
            if (!activeStarted) {
                startActive(event);
                return DueResult.REQUESTED;
            }
            return DueResult.ALREADY_ACTIVE;
        }
        if (activeEventId != null) {
            return DueResult.OTHER_EVENT_ACTIVE;
        }
        activeEventId = eventId;
        activePrepared = true;
        activeStarted = false;
        prepareSample = sampleCount;
        port.prepare(event.assetRef());
        startActive(event);
        return DueResult.REQUESTED;
    }

    /**
     * Window-end edge for {@code eventId}. Clean stop + release when it matches the
     * active comment; otherwise ignored (the scheduler already closed the window).
     */
    public void onExpire(String token, String eventId) {
        // C3 (session tokens): an expire bearing a token that is NOT the current
        // catalog token belongs to a stale/previous activation. It must never
        // interrupt/expire a comment that is active in the NEW session, so ignore it.
        if (catalogToken != null && !catalogToken.equals(token)) {
            return;
        }
        if (eventId != null && eventId.equals(activeEventId)) {
            teardown();
        }
    }

    // ----- UI + error seams -----

    /**
     * AUDIO ON/OFF toggle. Turning AUDIO OFF stops + releases IMMEDIATELY,
     * independent of the scheduler (the TEXT path may stay active).
     */
    public void setAudioEnabled(boolean enabled) {
        this.audioEnabled = enabled;
        if (!enabled) {
            teardown();
        }
    }

    /**
     * Apply the SceneVibe LOCAL gain to the port's own stream only. Never touches
     * system/Prime volume (no global stream-volume mutation).
     */
    public void setGain(float gain) {
        if (Float.isNaN(gain) || gain < 0.0f || gain > 1.0f) {
            throw new IllegalArgumentException("gain");
        }
        localGain=gain;
        AudioEvent event=catalog.get(activeEventId);
        port.setGain(gain * (event==null ? 1f : event.gain()));
    }

    /**
     * A resource/decode error reported by the port. Safe stop + release; the port's
     * truthful terminal state is left to the port. An error is NEVER interpreted as
     * coexistence (mirrors {@code VoiceCoexistenceProbe.onError}).
     */
    public void onError() {
        teardown();
    }

    /**
     * Record one passive sampling tick (the existing 1s cadence). Used only to let
     * FEAT-004 measure sampling + prep + start delays; it drives NO transition and
     * creates NO clock.
     */
    public void tick() {
        sampleCount++;
    }

    // ----- Diagnostics / timing hooks (FEAT-004) -----

    /** Return the eventId currently held by the port, or null when idle. */
    public String activeEventId() {
        return activeEventId;
    }

    /** True while a comment is audibly started. */
    public boolean isStarted() {
        return activeStarted && !frozen;
    }

    /** True while a comment is prepared (possibly not yet started). */
    public boolean isPrepared() {
        return activePrepared;
    }

    /** True while AUDIO is enabled by the UI toggle. */
    public boolean isAudioEnabled() {
        return audioEnabled;
    }

    /** Monotonic count of sampling ticks observed so far. */
    public long sampleCount() {
        return sampleCount;
    }

    /**
     * Sample index at which {@code prepare} was last issued, or {@code -1}. Combined
     * with {@link #startSample()} this exposes prep-vs-start latency for analysis.
     */
    public long prepareSample() {
        return prepareSample;
    }

    /** Sample index at which {@code start} was last issued, or {@code -1}. */
    public long startSample() {
        return startSample;
    }

    // ----- internals -----

    /** Issue gain + start for the active event exactly once. */
    private void startActive(AudioEvent event) {
        port.setGain(event.gain() * localGain);
        if (event.pausePolicy() == AudioEvent.PausePolicy.FREEZE_AND_RESUME)
            port.seekTo(nativeOffsets.getOrDefault(event.eventId(),0L));
        startSample = sampleCount;
        activeStarted = true;
        frozen=false;
        port.start();
    }

    /** Deterministic stop + release that clears all active bookkeeping. */
    private void teardown() {
        frozen=false;
        if (activeEventId == null && port.state() == LocalAudioPort.State.IDLE) {
            activePrepared = false;
            activeStarted = false;
            return;
        }
        activeEventId = null;
        activePrepared = false;
        activeStarted = false;
        prepareSample = -1L;
        startSample = -1L;
        port.stop();
        port.release();
    }
}
