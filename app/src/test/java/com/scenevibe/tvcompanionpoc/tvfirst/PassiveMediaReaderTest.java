package com.scenevibe.tvcompanionpoc.tvfirst;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;
import static org.junit.Assert.*;

/** Real reader API calls on Android framework shadows; not a Sony or native-player proof. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 35})
public final class PassiveMediaReaderTest {
    private Context context;
    private MediaSessionManager manager;
    private PassiveMediaReader reader;
    private MediaSession session;
    private MediaController controller;

    /** Start each case with a fresh platform-session inventory and a known elapsed clock. */
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication(); manager = context.getSystemService(MediaSessionManager.class);
        Shadows.shadowOf(manager).clearControllers(); reader = new PassiveMediaReader();
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10));
    }

    /** Release test sessions without sending transport commands. */
    @After public void cleanup() {if (session != null) session.release(); Shadows.shadowOf(manager).clearControllers();}

    /** Supply synthetic framework facts through the controller shadow; Robolectric does not propagate session setters. */
    private void publish(String id, long anchor) {
        if (session == null) {
            session = new MediaSession(context, "synthetic-test-session");
            controller = new MediaController(context, session.getSessionToken());
            Shadows.shadowOf(manager).addController(controller);
        }
        Shadows.shadowOf(controller).setPackageName("synthetic.player");
        Shadows.shadowOf(controller).setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id)
                .putString(MediaMetadata.METADATA_KEY_TITLE, "synthetic-title").build());
        Shadows.shadowOf(controller).setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 123, 1, anchor).build());
    }

    /** Read the same public API used by the actual probe. */
    private MediaEvidence capture(boolean access) {
        return reader.capture(manager, new ComponentName(context, "synthetic.Listener"), access, true);
    }

    /** Permission denial cannot fall back to title, cached data or a privileged query. */
    @Test public void permissionDeniedRemainsUnknown() {
        MediaEvidence e = capture(false); assertEquals(MediaEvidence.Availability.ACCESS_REQUIRED, e.availability);
        assertTrue(e.sessions.isEmpty()); assertEquals(MediaEvidence.Proof.UNKNOWN, e.edition);
    }

    /** A missing platform source is explicitly unavailable. */
    @Test public void missingManagerRemainsUnknown() {
        MediaEvidence e = reader.capture(null, null, true, true);
        assertEquals(MediaEvidence.Availability.UNAVAILABLE, e.availability);
    }

    /** Native id and raw anchor are preserved without becoming edition/context proof. */
    @Test public void readsPublishedFactsWithoutAdmission() {
        long anchor = SystemClock.elapsedRealtime() - 2000; publish("synthetic-native-id", anchor);
        MediaEvidence e = capture(true); assertEquals(1, e.sessionCount);
        assertEquals("synthetic-native-id", e.sessions.get(0).mediaId); assertEquals(anchor, e.sessions.get(0).anchorAtMs);
        assertEquals(-1, e.sessions.get(0).durationMs);
        assertEquals(MediaEvidence.Proof.UNKNOWN, e.programme); assertEquals(MediaEvidence.Proof.UNKNOWN, e.edition);
        assertEquals(MediaEvidence.Proof.UNKNOWN, e.foreground); assertEquals(MediaEvidence.Proof.UNKNOWN, e.timeline);
        assertFalse(MediaEvidencePolicy.evaluate(e, e.capturedAtMs).renderingAllowed());
    }

    /** A stable token keeps a local label without serializing the platform token. */
    @Test public void labelStableOnlyWhileSessionPresent() {
        publish("id", SystemClock.elapsedRealtime()); String first = capture(true).sessions.get(0).key;
        assertEquals(first, capture(true).sessions.get(0).key);
        Shadows.shadowOf(manager).clearControllers(); assertTrue(capture(true).sessions.isEmpty());
        Shadows.shadowOf(manager).addController(new MediaController(context, session.getSessionToken()));
        assertNotEquals(first, capture(true).sessions.get(0).key);
    }

    /** Multiple controllers remain observable and ambiguous; no priority/PLAYING shortcut selects one. */
    @Test public void severalSessionsRemainAmbiguous() {
        publish("id", SystemClock.elapsedRealtime());
        Shadows.shadowOf(manager).addController(new MediaController(context, session.getSessionToken()));
        MediaEvidence e = capture(true); assertEquals(2, e.sessionCount); assertEquals(2, e.sessions.size());
        assertEquals(e.sessions.get(0).key, e.sessions.get(1).key);
        assertTrue(MediaEvidencePolicy.evaluate(e, e.capturedAtMs).missing.contains(MediaEvidencePolicy.Reason.SESSION_AMBIGUOUS));
    }

    /** Native future anchors are retained and the descriptive estimate is unavailable. */
    @Test public void futureAnchorIsNotClamped() {
        long future = SystemClock.elapsedRealtime() + 1000; publish("id", future);
        MediaEvidence.Session s = capture(true).sessions.get(0);
        assertEquals(future, s.anchorAtMs); assertEquals(-1, s.estimatedPositionMs);
    }

    /** Losing permission retires all previously readable facts rather than admitting a cached session. */
    @Test public void permissionLossRetiresFacts() {
        publish("id", SystemClock.elapsedRealtime());
        String key = capture(true).sessions.get(0).key;
        assertTrue(capture(false).sessions.isEmpty());
        assertNotEquals(key, capture(true).sessions.get(0).key);
    }

    /** A replacement token, package, wrong id or pause cannot establish programme or visible-application proof. */
    @Test public void replacementAndContradictoryStateStayUnknown() {
        publish("wrong-id-with-correct-title", SystemClock.elapsedRealtime());
        String key = capture(true).sessions.get(0).key;
        session.release(); session = null; Shadows.shadowOf(manager).clearControllers();
        publish(null, SystemClock.elapsedRealtime());
        Shadows.shadowOf(controller).setPackageName("different.player");
        Shadows.shadowOf(controller).setPlaybackState(new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PAUSED, 123, 1, SystemClock.elapsedRealtime()).build());
        MediaEvidence e = capture(true);
        assertNotEquals(key, e.sessions.get(0).key);
        assertEquals("different.player", e.sessions.get(0).packageName);
        assertEquals(MediaEvidence.Proof.UNKNOWN, e.foreground);
        assertEquals(MediaEvidence.Proof.UNKNOWN, e.programme);
        assertTrue(MediaEvidencePolicy.evaluate(e, e.capturedAtMs).missing.contains(MediaEvidencePolicy.Reason.NOT_PLAYING));
    }

    /** Failed arithmetic cannot become a usable position. */
    @Test public void estimatesRejectOverflowAndNonfiniteSpeed() {
        assertEquals(-1, PassiveMediaReader.estimate(Long.MAX_VALUE, 1, Float.MAX_VALUE, 3, 10000));
        assertEquals(-1, PassiveMediaReader.estimate(1, 1, Float.NaN, 3, 10000));
        assertEquals(9, PassiveMediaReader.estimate(9, 1, 1, 2, 10000));
    }
}
