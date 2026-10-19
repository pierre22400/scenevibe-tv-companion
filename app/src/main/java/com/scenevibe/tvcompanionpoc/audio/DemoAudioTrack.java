package com.scenevibe.tvcompanionpoc.audio;

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
}
