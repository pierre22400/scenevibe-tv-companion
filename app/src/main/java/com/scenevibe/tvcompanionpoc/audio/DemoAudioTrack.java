package com.scenevibe.tvcompanionpoc.audio;

import com.scenevibe.tvcompanionpoc.calendar.MediaCalendar;
import com.scenevibe.tvcompanionpoc.calendar.SceneEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LOCAL demonstration AUDIO track: a tiny, Android-free dev/demo fixture that binds
 * a small set of {@link AudioEvent}s to an opaque local media identity so the
 * {@link SceneVibeAudioRenderer} can be exercised end to end (due / pause / seek /
 * episode-change) without any Cloud ingestion path.
 *
 * <p>This is explicitly NOT a Cloud/production contract: it is never serialized into
 * the FinalTrack/Cloud JSON schema and it does not reuse
 * {@code ScheduledTrack.MediaIdentity} (which is Android-coupled via {@code Bitmap}).
 * The media identity here is a bounded, opaque triple used only to key the demo so
 * the renderer's catalog token/eligibility transitions can be driven locally.</p>
 *
 * <p>All assets reference the single bundled ADTS/AAC cue
 * ({@code res/raw/scenevibe_cue.aac}); the M4A/MP4 container is avoided per the A1
 * physical codec lesson.</p>
 */
public final class DemoAudioTrack {

    /** Opaque local id for the bundled demo cue; resolved by the Android adapter. */
    public static final String CUE_ASSET_REF = "scenevibe_cue";

    /** Scheduler token used by the demo wiring to key eligibility/catalog for episode A. */
    public static final String DEMO_TOKEN = "demo-audio-episode-a";

    /** A second token to demonstrate an episode change (previous-episode audio stops). */
    public static final String DEMO_TOKEN_EPISODE_B = "demo-audio-episode-b";

    /** Default SceneVibe LOCAL gain used by the mapped demo AUDIO cues. */
    public static final float DEMO_GAIN = 0.8f;

    /** Fallback audible window (ms) for a mapped cue when the TEXT event has no window. */
    private static final long DEMO_WINDOW_MS = 6_000L;

    /** Upper bound of {@link AudioEvent}'s duration field; the mapping clamps to it. */
    private static final long MAX_AUDIO_WINDOW_MS = 60_000L;

    private final String platform;
    private final String videoId;
    private final long durationMs;
    private final List<AudioEvent> events;

    /** Construct a bounded demo track; {@code events} are copied and left immutable. */
    public DemoAudioTrack(String platform, String videoId, long durationMs,
                          List<AudioEvent> events) {
        if (platform == null || platform.isEmpty()
                || videoId == null || videoId.isEmpty()
                || durationMs <= 0 || events == null) {
            throw new IllegalArgumentException("Invalid demo audio track");
        }
        this.platform = platform;
        this.videoId = videoId;
        this.durationMs = durationMs;
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
    }

    /**
     * Build the default local demonstration episode: three short spoken comments
     * spread across the media timeline, all using the bundled cue, at a moderate
     * SceneVibe gain. Enough to exercise due (each start), pause (STOP_ON_PAUSE on an
     * in-progress comment), seek (scheduler re-arm around a boundary) and
     * episode-change (eligibility loss stops the previous episode's audio).
     */
    public static DemoAudioTrack defaultEpisode() {
        List<AudioEvent> events = new ArrayList<>();
        // Opening cue a few seconds in.
        events.add(AudioEvent.audio("demo-audio-1", CUE_ASSET_REF, 5_000L, 6_000L, 0.8f));
        // Mid-scene cue (good target for a forward/backward seek demo).
        events.add(AudioEvent.audio("demo-audio-2", CUE_ASSET_REF, 45_000L, 6_000L, 0.8f));
        // Later cue (good target for a pause/STOP_ON_PAUSE demo).
        events.add(AudioEvent.audio("demo-audio-3", CUE_ASSET_REF, 120_000L, 6_000L, 0.8f));
        return new DemoAudioTrack("demo-local", "episode-a", 1_800_000L, events);
    }

    /** Opaque local platform tag for the demo (never a Cloud platform contract). */
    public String platform() {
        return platform;
    }

    /** Opaque local video id for the demo. */
    public String videoId() {
        return videoId;
    }

    /** Nominal media duration for the demo timeline. */
    public long durationMs() {
        return durationMs;
    }

    /** The immutable list of demo AUDIO events, sorted by the caller's input order. */
    public List<AudioEvent> events() {
        return events;
    }

    /**
     * C1 (AUDIO 002B) eventId alignment. Build the demo AUDIO catalog from the SAME
     * eventIds the REAL {@code MediaCalendarScheduler} actually emits via
     * {@code onDue(token, eventId)}.
     *
     * <p>ROOT CAUSE this fixes: the live scheduler emits {@code onDue} with the active
     * track's {@code SceneEvent.eventId} (= {@code ScheduledTrack.Event.id}, built in
     * {@code VideoPreparedState} from {@code track.comments}), NOT the demo's
     * {@code demo-audio-1/2/3} ids. Keying the AUDIO catalog on the demo ids made
     * {@code catalog.get(eventId)} always null, so AUDIO never started end to end. This
     * explicit, testable mapping takes the exact {@link SceneEvent}s of the active
     * calendar and produces one {@link AudioEvent} per event, keyed to the SAME
     * {@code eventId}, all referencing the single bundled cue {@link #CUE_ASSET_REF} at
     * {@link #DEMO_GAIN}. It does NOT invent or mutate any TEXT eventId: it only READS
     * the TEXT ids and emits a parallel AUDIO value keyed to them.</p>
     *
     * <p>Window handling: {@link AudioEvent} bounds a cue to {@code [0, 60_000] ms}. A
     * TEXT event with no window ({@code durationMs <= 0}) maps to a bounded
     * {@link #DEMO_WINDOW_MS} audible window; a larger TEXT window is clamped to
     * {@link #MAX_AUDIO_WINDOW_MS}. A {@code startMs} outside the AUDIO bound is skipped
     * (the TEXT path is unaffected).</p>
     *
     * @param calendar the active media calendar whose event ids drive the real onDue
     * @return a bounded immutable list of AUDIO events keyed to the real event ids
     */
    public static List<AudioEvent> catalogForCalendar(MediaCalendar calendar) {
        if (calendar == null) {
            return Collections.emptyList();
        }
        return catalogForEvents(calendar.events());
    }

    /**
     * C1 mapping over a raw list of TEXT {@link SceneEvent}s (same semantics as
     * {@link #catalogForCalendar(MediaCalendar)}; exposed for direct, order-preserving
     * tests). Each produced {@link AudioEvent} reuses the source {@code eventId} and
     * {@code startMs} verbatim so a scheduler {@code onDue} for that id resolves.
     */
    public static List<AudioEvent> catalogForEvents(List<SceneEvent> sceneEvents) {
        List<AudioEvent> mapped = new ArrayList<>();
        if (sceneEvents == null) {
            return mapped;
        }
        for (SceneEvent event : sceneEvents) {
            if (event == null) {
                continue;
            }
            if (event.startMs() < 0L || event.startMs() > 43_200_000L) {
                // Outside the AUDIO value's bound; leave the TEXT event untouched.
                continue;
            }
            long window = event.durationMs() > 0L
                    ? Math.min(event.durationMs(), MAX_AUDIO_WINDOW_MS)
                    : DEMO_WINDOW_MS;
            mapped.add(AudioEvent.audio(event.eventId(), CUE_ASSET_REF,
                    event.startMs(), window, DEMO_GAIN));
        }
        return Collections.unmodifiableList(mapped);
    }
}
