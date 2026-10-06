package com.scenevibe.tvcompanionpoc;

import android.media.session.PlaybackState;
import com.scenevibe.tvcompanionpoc.calendar.MediaCalendarScheduler;
import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationStatus;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.installation.PackageInstaller;
import com.scenevibe.tvcompanionpoc.installation.TvCapabilities;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/**
 * Execute the actual prepared state, installer, unique calendar core and service callback router.
 * Both supported Video shapes use the same immutable token and generation handoff. Only native
 * windows/private disk and owner identity are substituted; no temporal algorithm is copied.
 */
@RunWith(Parameterized.class)
public final class M5VideoCutoverTest {
    /** Execute every handoff/clock/eligibility invariant on manifested and legacy Video. */
    @Parameterized.Parameters(name="manifested={0}") public static Collection<Object[]> modes() {
        return Arrays.asList(new Object[][]{{true},{false}});
    }
    private final boolean manifested;
    /** Select the real registry handler shape, without changing its qualified payload codec. */
    public M5VideoCutoverTest(boolean manifested) {this.manifested=manifested;}

    /** Real production composition with a recording native drawing seam. */
    private final class Runtime {
        final M4PhaseFFixtures.Backend backend=new M4PhaseFFixtures.Backend(new HashMap<>());
        final InstallationStore store=new InstallationStore(backend);
        final PackageInstaller installer=new PackageInstaller(store,VideoInstallationHandlers.registry(),TvCapabilities.current());
        final List<String> visual=new ArrayList<>(),eligibility=new ArrayList<>();
        final SceneRuntimeController controller;
        final MediaCalendarScheduler scheduler;
        final OverlayService.LiveVideoRuntimePorts ports;
        VideoPreparedState prepared;
        boolean owner=true,legacyVisible,throwRetireLegacy,throwRetireScenes;
        int playback,legacyRetired,scenesRetired;
        long selected;
        /** Compose the same core-to-router-to-native paths as OverlayService. */
        Runtime() {
            controller=new SceneRuntimeController(new SceneRuntimeController.SceneSink() {
                /** The fixture's qualified text scene has no remote asset requirement. */
                @Override public boolean preflight(OverlayManifest.Scene scene) {visual.add("preflight:"+scene.id);return true;}
                /** Record only an actual controller show, never an ARM operation. */
                @Override public void show(OverlayManifest.Scene scene) {assertFalse(legacyVisible);visual.add("show:"+scene.id);}
                /** Record a matching scene retirement, preserving exact hide-before-show order. */
                @Override public void hide(OverlayManifest.Scene scene) {visual.add("hide:"+scene.id);}
                /** Immediate native removal is a distinct observable transition. */
                @Override public void hideAll() {visual.add("hide-all");}
            });
            scheduler=new MediaCalendarScheduler(new MediaCalendarScheduler.Sink() {
                /** Execute the actual active-token lookup. */
                @Override public void onDue(String token,String id) {ports.onDue(token,id);}
                /** Execute the actual captured-generation expiry route. */
                @Override public void onExpire(String token,String id) {ports.onExpire(token,id);}
                /** Preserve every playback repetition through the actual router. */
                @Override public void onPlayback(String token,boolean playing,boolean freeze) {ports.onPlayback(token,playing,freeze);}
                /** Exercise synchronous old-binding invalidation as well as active transitions. */
                @Override public void onEligibility(String token,boolean eligible) {ports.onEligibility(token,eligible);}
            });
            ports=new OverlayService.LiveVideoRuntimePorts(()->owner,()->scheduler,()->controller,
                    ()->{legacyRetired++;legacyVisible=false;if(throwRetireLegacy)throw new IllegalStateException("Native legacy refusal");},
                    ()->{scenesRetired++;if(throwRetireScenes)throw new IllegalStateException("Native scene refusal");},revision->selected=revision,
                    new OverlayService.LiveVideoRuntimePorts.LegacySink() {
                        /** Exact payload identity is asserted without reconstructing a Video event. */
                        @Override public void due(ScheduledTrack.Event event) {
                            assertSame(prepared.eventsById.get(event.id),event);
                            assertFalse(controller.hasVisibleScene());legacyVisible=true;visual.add("legacy:"+event.id);
                        }
                        /** Record repetitions including pause and the original freeze policy. */
                        @Override public void playback(boolean playing,boolean freeze) {playback++;visual.add("playback:"+playing+":"+freeze);}
                        /** Eligibility is applied only after the diagnostic and controller transition. */
                        @Override public void eligibility(boolean eligible) {assertFalse(eligible);legacyVisible=false;visual.add("legacy-hidden");}
                    },eligible->{eligibility.add(Boolean.toString(eligible));visual.add("diagnostic:"+eligible);});
        }
        /** Use the actual Cloud v1 projection and registry preparation, without changing wire bytes. */
        InstallRequest request(long revision) throws Exception {
            org.json.JSONObject envelope=M4PhaseFFixtures.envelope(manifested,revision);
            return CloudV1InstallationAdapter.adapt(envelope,envelope.getString("deviceId")).request();
        }
        /** Install/prepare the selected actual Video package and retain its trusted active payload. */
        void install(long revision) throws Exception {
            InstallRequest request=request(revision);
            assertEquals(InstallationStatus.ARMED,installer.install(request,ports));
            capturePrepared();
        }
        /** Capture the real active prepared state after install or restore without a copied projection. */
        void capturePrepared() throws Exception {
            Field active=ports.getClass().getDeclaredField("active");active.setAccessible(true);
            Object binding=active.get(ports);Field state=binding.getClass().getDeclaredField("state");state.setAccessible(true);
            prepared=(VideoPreparedState)state.get(binding);
        }
        /** Read an opaque test binding without adding any production diagnostic/token accessor. */
        String token(String slot) throws Exception {
            Field field=ports.getClass().getDeclaredField(slot);field.setAccessible(true);Object binding=field.get(ports);
            if(binding==null)return null;Field token=binding.getClass().getDeclaredField("token");token.setAccessible(true);
            return (String)token.get(binding);
        }
        /** First exact parser event drives media positions; the fixture owns no scheduling rule. */
        ScheduledTrack.Event event() {return prepared.track.comments.get(0);}
        /** Build a passive MediaSession value and run the actual C Video observation adapter. */
        void snapshot(long position,boolean playing,boolean matching) {
            ScheduledTrack track=prepared.track;
            ports.onSnapshot(new MediaSessionProbe.Snapshot(matching?track.targetPackage:"other.app",
                    playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,playing?"PLAYING":"PAUSED",
                    position,position,1f,0L,track.mediaIdentity.videoId,track.mediaIdentity.title,"",track.mediaIdentity.durationMs));
        }
        /** A selected manifested scene or a selected legacy commentary is the sole visible owner. */
        boolean visible() {return controller.hasVisibleScene()||legacyVisible;}
        /** Feed first due at its exact validated start, keeping fixture time independent of wall time. */
        void due() {snapshot(event().startMs,true,true);}
    }

    /** ARM only selects ownership; the first valid passive media observation makes it visible. */
    @Test public void installArmsThenDueRendersOneOwner() throws Exception {
        Runtime r=new Runtime();r.install(12);assertEquals(12,r.selected);assertFalse(r.visible());r.due();assertTrue(r.visible());
    }
    /** Canonical binding shares the exact calendar/index created once by Video preparation. */
    @Test public void bindRetainsCalendarAndPayloadIdentity() throws Exception {
        Runtime r=new Runtime();r.install(12);VideoPreparedState bound=r.prepared.bind(r.request(13),"test",null);
        assertSame(r.prepared.calendar,bound.calendar);assertSame(r.prepared.eventsById,bound.eventsById);
        assertEquals(r.event().id,bound.calendar.events().get(0).eventId());
        assertEquals(r.event().startMs,bound.calendar.events().get(0).startMs());
        assertEquals(r.event().durationMs,bound.calendar.events().get(0).durationMs());
        assertEquals(r.prepared.track.pauseFreezesDisplay,bound.calendar.freezeOnPause());
        try {bound.eventsById.clear();fail("immutable payload index");}catch(UnsupportedOperationException expected) {}
    }
    /** Durable restore reconstructs a fresh calendar and arms without any candidate/ACK write. */
    @Test public void restoreReconstructsWithoutWriteOrShow() throws Exception {
        Runtime r=new Runtime();r.install(12);r.store.markAcknowledged(12);int writes=r.backend.candidateWrites,acks=r.backend.ackWrites;
        VideoPreparedState previous=r.prepared;String old=r.token("active");r.ports.abortActivation();
        assertEquals(InstallationStatus.ARMED,OverlayService.restoreInstalledPackage(r.store,r.installer,r.ports,DiagnosticsStore.INSTANCE));
        assertEquals(writes,r.backend.candidateWrites);assertEquals(acks,r.backend.ackWrites);assertFalse(r.visible());
        assertNotNull(r.token("active"));assertNotEquals(old,r.token("active"));assertEquals(12,r.selected);
        r.capturePrepared();assertNotSame(previous.calendar,r.prepared.calendar);r.due();assertTrue(r.visible());
        List<String> before=new ArrayList<>(r.visual);r.ports.onDue(old,r.event().id);r.ports.onExpire(old,r.event().id);
        assertEquals(before,r.visual);assertTrue(r.visible());
    }
    /** Every old-token callback after replacement is inert, including eligibility and playback. */
    @Test public void replacementRejectsEveryOldCallbackWithoutHidingB() throws Exception {
        Runtime r=new Runtime();r.install(12);r.due();String old=r.token("active");r.install(13);assertFalse(r.visible());r.due();
        assertTrue(r.visible());assertNotEquals(old,r.token("active"));List<String> before=new ArrayList<>(r.visual);
        r.ports.onDue(old,r.event().id);r.ports.onExpire(old,r.event().id);r.ports.onPlayback(old,false,true);
        r.ports.onEligibility(old,false);assertEquals(before,r.visual);assertTrue(r.visible());
    }
    /** Pending callbacks cannot render, hide, change playback or affect eligibility before selection. */
    @Test public void pendingBindingCannotConsumeCallbacks() throws Exception {
        Runtime r=new Runtime();r.install(12);r.due();r.ports.retireManifestedVisualOwner();r.ports.retireLegacyVisualOwner();
        assertTrue(r.ports.loadPreparedVideo(r.prepared));String pending=r.token("pending");assertNull(r.token("active"));
        List<String> before=new ArrayList<>(r.visual);r.ports.onDue(pending,r.event().id);r.ports.onExpire(pending,r.event().id);
        r.ports.onPlayback(pending,true,true);r.ports.onEligibility(pending,true);assertEquals(before,r.visual);assertFalse(r.visible());
    }
    /** Abort clears pending and active, then rejects both callback identities idempotently. */
    @Test public void abortInvalidatesEveryBindingAndSelection() throws Exception {
        Runtime r=new Runtime();r.install(12);String old=r.token("active");r.ports.loadPreparedVideo(r.prepared);
        String pending=r.token("pending");r.ports.abortActivation();r.ports.abortActivation();assertNull(r.token("pending"));assertNull(r.token("active"));
        assertEquals(0,r.selected);List<String> before=new ArrayList<>(r.visual);
        for(String token:Arrays.asList(old,pending)) {r.ports.onDue(token,r.event().id);r.ports.onExpire(token,r.event().id);r.ports.onPlayback(token,true,true);}
        assertEquals(before,r.visual);assertFalse(r.visible());
    }
    /** Active-token due/expiry carry stored generation X even if an external regie load advances it to Y. */
    @Test public void generationIsCapturedAtArmNeverRetagged() throws Exception {
        Runtime r=new Runtime();r.install(12);r.due();String token=r.token("active");
        if(manifested) {
            long original=r.controller.currentGeneration();r.controller.replaceRevision(13,r.prepared.manifest);
            assertNotEquals(original,r.controller.currentGeneration());r.ports.onDue(token,r.event().id);assertFalse(r.visible());
            r.controller.onEligibility(true);r.controller.onEventDue(r.event().id);assertTrue(r.visible());
            List<String> before=new ArrayList<>(r.visual);r.ports.onExpire(token,r.event().id);assertEquals(before,r.visual);assertTrue(r.visible());
        } else {r.ports.onExpire(token,r.event().id);assertTrue(r.visible());}
    }
    /** Missing/null event IDs fail closed without payload reconstruction or a native show/hide. */
    @Test public void unknownAndNullIdsAreInert() throws Exception {
        Runtime r=new Runtime();r.install(12);List<String> before=new ArrayList<>(r.visual);String token=r.token("active");
        r.ports.onDue(token,"unknown");r.ports.onDue(token,null);r.ports.onExpire(token,"unknown");r.ports.onExpire(token,null);
        assertEquals(before,r.visual);assertFalse(r.visible());
    }
    /** Paused media anchors without DUE, then resume renders the same eligible event. */
    @Test public void pausedAnchorThenResumePreservesDue() throws Exception {
        Runtime r=new Runtime();r.install(12);r.snapshot(r.event().startMs,false,true);assertFalse(r.visible());r.due();assertTrue(r.visible());
    }
    /** Normal expiry drives manifested hides while leaving the legacy freeze-aware countdown alone. */
    @Test public void expiryUsesOnlySelectedVisualPolicy() throws Exception {
        Runtime r=new Runtime();r.install(12);r.due();String token=r.token("active");r.ports.onExpire(token,r.event().id);
        assertEquals(!manifested,r.visible());
    }
    /** Backward seek rearms at the next observation and never shows during the seek input itself. */
    @Test public void backwardSeekRearmsForReplay() throws Exception {
        Runtime r=new Runtime();r.install(12);r.due();long start=r.event().startMs;
        r.snapshot(start+4000,true,true);r.snapshot(Math.max(0,start-3000),true,true);int shows=r.visual.size();r.due();
        assertTrue(r.visible());assertTrue(r.visual.size()>shows);
    }
    /** A large forward seek consumes skipped events and does not show the event it lands past. */
    @Test public void forwardSeekSkipsWithoutStaleDue() throws Exception {
        Runtime r=new Runtime();r.install(12);r.snapshot(0,false,true);r.snapshot(r.event().startMs+7000,true,true);
        assertFalse(r.visible());r.snapshot(r.event().startMs+7001,true,true);assertFalse(r.visible());
    }
    /** Eligibility loss hides immediately; recovery alone while paused cannot resurrect a comment. */
    @Test public void eligibilityLossAndPausedRecoveryDoNotResurrect() throws Exception {
        Runtime r=new Runtime();r.install(12);r.due();r.snapshot(r.event().startMs,true,false);assertFalse(r.visible());
        assertEquals("false",r.eligibility.get(r.eligibility.size()-1));r.snapshot(r.event().startMs,false,true);assertFalse(r.visible());
    }
    /** Unavailable uses the distinct generic entry and keeps the active prepared installation for recovery. */
    @Test public void unavailableHidesWithoutClearingActiveBinding() throws Exception {
        Runtime r=new Runtime();r.install(12);r.due();String token=r.token("active");r.scheduler.onUnavailable();
        assertFalse(r.visible());assertEquals(token,r.token("active"));assertEquals(12,r.selected);
    }
    /** Same-revision restore receives a new activation token without an additional durable write. */
    @Test public void sameRevisionUsesNewTokenAndNoWrite() throws Exception {
        Runtime r=new Runtime();r.install(12);String token=r.token("active");int writes=r.backend.candidateWrites;
        r.install(12);assertNotEquals(token,r.token("active"));assertEquals(writes,r.backend.candidateWrites);assertFalse(r.visible());
    }
    /** Stale delivery is refused by the unchanged installer before temporal ownership can change. */
    @Test public void staleRevisionCannotReplaceActivation() throws Exception {
        Runtime r=new Runtime();r.install(12);String token=r.token("active");
        assertEquals(InstallationStatus.STALE,r.installer.install(r.request(11),r.ports));assertEquals(token,r.token("active"));assertEquals(12,r.selected);
    }
    /** A worker cannot load, select, route callbacks or clean up the main owner's activation. */
    @Test public void nonOwnerCannotMutateAnyBinding() throws Exception {
        Runtime r=new Runtime();r.install(12);r.due();String token=r.token("active");List<String> before=new ArrayList<>(r.visual);
        r.owner=false;assertFalse(r.ports.loadPreparedVideo(r.prepared));assertFalse(r.ports.selectActiveRevision(13,manifested));
        r.ports.abortActivation();r.ports.onDue(token,r.event().id);r.ports.onExpire(token,r.event().id);r.ports.onPlayback(token,false,true);
        assertEquals(before,r.visual);assertEquals(token,r.token("active"));assertEquals(12,r.selected);
    }
    /** Unarmed/mismatched selection cannot promote a pending manifested binding. */
    @Test public void failedSelectionCannotPromotePending() throws Exception {
        Runtime r=new Runtime();r.install(12);r.ports.retireManifestedVisualOwner();r.ports.retireLegacyVisualOwner();
        assertTrue(r.ports.loadPreparedVideo(r.prepared));assertFalse(r.ports.selectActiveRevision(0,manifested));
        assertFalse(r.ports.selectActiveRevision(13,!manifested));assertNull(r.token("active"));r.ports.abortActivation();assertNull(r.token("pending"));
    }
    /** A null load refuses before mutating an active binding or selected revision. */
    @Test public void nullPreparedLoadDoesNotMutateActivation() throws Exception {
        Runtime r=new Runtime();r.install(12);String token=r.token("active");assertFalse(r.ports.loadPreparedVideo(null));
        assertEquals(token,r.token("active"));assertEquals(12,r.selected);
    }
    /** Playback repetitions remain observable for the legacy renderer; manifested playback owns no clock/show. */
    @Test public void playbackRepetitionsPreserveSelectedOwner() throws Exception {
        Runtime r=new Runtime();r.install(12);r.due();String token=r.token("active");int before=r.playback;
        r.ports.onPlayback(token,false,true);r.ports.onPlayback(token,false,true);assertEquals(manifested?before:before+2,r.playback);assertTrue(r.visible());
    }
    /** Actual media-window expiry traverses the qualified generic core and the selected router path. */
    @Test public void mediaWindowExpiryTraversesActualCore() throws Exception {
        Runtime r=new Runtime();r.install(12);r.due();r.snapshot(r.event().startMs+r.event().durationMs,true,true);
        assertEquals(!manifested,r.visible());
    }
    /** Native retirement refusals cannot skip the other owner, token invalidation or generic clear. */
    @Test public void abortAttemptsAllCleanupAfterNativeRefusals() throws Exception {
        Runtime r=new Runtime();r.install(12);r.due();String token=r.token("active");
        int legacy=r.legacyRetired,scenes=r.scenesRetired;r.throwRetireLegacy=true;r.throwRetireScenes=true;
        r.ports.abortActivation();assertTrue(r.legacyRetired>legacy);assertTrue(r.scenesRetired>scenes);
        assertNull(r.token("active"));assertNull(r.token("pending"));assertEquals(0,r.selected);
        Field calendar=r.scheduler.getClass().getDeclaredField("calendar");calendar.setAccessible(true);assertNull(calendar.get(r.scheduler));
        List<String> before=new ArrayList<>(r.visual);r.ports.onDue(token,r.event().id);r.ports.onExpire(token,r.event().id);
        assertEquals(before,r.visual);assertFalse(r.visible());
    }

}
