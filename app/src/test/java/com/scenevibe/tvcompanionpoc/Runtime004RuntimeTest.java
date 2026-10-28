package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.audio.LocalAudioPort;
import com.scenevibe.tvcompanionpoc.calendar.MediaObservation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Real nineteen-event parser, AD-SYNC, calendar and renderers with recording
 * drawing/audio ports. Native decoding/STARTED is qualified separately on TV.
 */
public final class Runtime004RuntimeTest {
    /** Compose actual core components without granting Android or Cloud authority. */
    private static final class Fixture implements AutoCloseable,SceneRuntimeController.SceneSink {
        final Port port=new Port();final AdSyncController guard=new AdSyncController(true);
        final StudioMultimediaRuntime runtime;long capture=100000;int shows;String visible;
        /** Verify unchanged resource bytes; duration is an explicit software decoder seam here. */
        Fixture() throws Exception {
            Path root=Path.of("src/androidTest/assets/cloudbridge003e");if(!Files.exists(root))root=Path.of("app/src/androidTest/assets/cloudbridge003e");
            var profile=new MultimediaDeliveryProfile(Files.readAllBytes(root.resolve("profile.json")));Map<String,byte[]> resources=new HashMap<>();
            for(var resource:profile.resources)resources.put(resource.sha256,Files.readAllBytes(root.resolve(resource.sha256+".mp3")));
            runtime=new StudioMultimediaRuntime(this,port,true,true,null);runtime.loadForSoftwareQualification(profile.prepare(resources,bytes->3000),"owned");
        }
        /** Publish compatible software capture facts; the guard never supplies an independent playback clock. */
        void at(long position){capture+=250;var s=new AdSyncController.Sample("own",true,true,position,position,capture,capture,1f,5884768);
            var d=guard.observe(s,capture);if(d.allow)runtime.observe("owned",new MediaObservation(true,d.contentMs,true),s.session,s.capturedMs,s.speed);else runtime.suspendComments(d.reason.name());}
        /** Feed the actual timestamp cadence through the same core pipeline. */
        void through(long start,long end){for(long p=start;p<=end;p+=250)at(p);}
        /** A mode change changes policy only; the calendar remains loaded. */
        void toggle(boolean on){guard.setEnabled(on);runtime.policyChanged();}
        /** Model invalid public clock facts, not an alleged advertisement. */
        void invalid(){capture+=250;var d=guard.observe(new AdSyncController.Sample("own",false,true,-1,-1,capture,capture,1f,5884768,true),capture);assertFalse(d.allow);runtime.suspendComments(d.reason.name());}
        /** Recording preflight does not claim a real WindowManager or permission. */
        @Override public boolean preflight(OverlayManifest.Scene scene){return true;}
        /** Count actual core render requests separately from consumption. */
        @Override public void show(OverlayManifest.Scene scene){visible=scene.id;shows++;}
        /** Hide only the recording SceneVibe window. */
        @Override public void hide(OverlayManifest.Scene scene){visible=null;}
        /** Eligibility loss clears the visible effect, not the loaded package. */
        @Override public void hideAll(){visible=null;}
        /** Revoke only this software fixture. */
        @Override public void close(){runtime.close();}
    }
    /** Recording native-offset-capable port; no acoustic/native STARTED claim. */
    private static final class Port implements LocalAudioPort {
        State state=State.IDLE;int starts;
        /** Record local preparation only. */ @Override public void prepare(String id){state=State.PREPARED;}
        /** Count renderer start calls, not a platform decoder acknowledgement. */ @Override public void start(){state=State.STARTED;starts++;}
        /** Stop only this recording port. */ @Override public void stop(){state=State.IDLE;}
        /** Release only recording state. */ @Override public void release(){state=State.IDLE;}
        /** Retain gain semantics in the renderer; no system volume exists. */ @Override public void setGain(float gain){}
        /** Report only the fixture's state. */ @Override public State state(){return state;}
        /** The actual package uses FREEZE_AND_RESUME. */ @Override public boolean supportsNativeOffsets(){return true;}
        /** Record a local freeze without re-preparation. */ @Override public void pause(){state=State.PAUSED;}
        /** Resume the same recording resource. */ @Override public void resume(){state=State.STARTED;}
        /** Offsets come solely from the scheduler. */ @Override public void seekTo(long offset){}
    }
    /** Before a scheduled TEXT, mode toggles preserve its future availability. */
    @Test public void toggleBeforeTextKeepsItDue() throws Exception {try(var f=new Fixture()){f.at(32000);f.toggle(true);f.at(32250);f.toggle(false);f.through(32500,35000);assertEquals(1,f.shows);assertEquals(1,f.runtime.diagnostics().textDue);}}
    /** Both directions inside the original eight-second window preserve the already-visible scene. */
    @Test public void togglesInsideTextDoNotHideOrConsumeAgain() throws Exception {try(var f=new Fixture()){f.through(32000,35000);String visible=f.visible;int consumed=f.runtime.diagnostics().consumed;
        f.toggle(true);f.at(35250);assertEquals(visible,f.visible);f.toggle(false);f.at(35500);assertEquals(visible,f.visible);assertEquals(1,f.shows);assertEquals(consumed,f.runtime.diagnostics().consumed);assertEquals(52988,f.runtime.diagnostics().nextStartMs);}}
    /** A mode switch during a genuine MEDIA hold cannot bypass identity/clock or permanently lose a current TEXT. */
    @Test public void invalidClockAndToggleRestoreTheCurrentWindow() throws Exception {try(var f=new Fixture()){f.through(32000,35000);f.invalid();assertNull(f.visible);f.toggle(true);f.at(35250);assertNull(f.visible);f.at(35500);
        assertNotNull(f.visible);assertEquals(2,f.shows);assertEquals(1,f.runtime.diagnostics().textDue);assertEquals(1,f.runtime.diagnostics().textRestoreRequests);f.at(41250);assertNull(f.visible);}}
    /** Between scheduled events, toggling never creates a spurious render or erases the next TEXT. */
    @Test public void toggleBetweenEventsRetainsFutureText() throws Exception {try(var f=new Fixture()){f.through(32000,42000);assertNull(f.visible);f.toggle(true);f.through(42250,54000);assertEquals(2,f.runtime.diagnostics().textDue);assertNotNull(f.visible);}}
    /** An active local MP3 is not torn down or prepared again merely because policy changes. */
    @Test public void toggleDuringAudioDoesNotRestartIt() throws Exception {try(var f=new Fixture()){f.through(89000,90500);assertEquals(1,f.port.starts);f.toggle(true);f.at(90750);f.toggle(false);f.at(91000);assertEquals(1,f.port.starts);assertEquals(LocalAudioPort.State.STARTED,f.port.state());}}
    /** A played MP3 remains consumed across OFF/ON, independent of TEXT visibility. */
    @Test public void togglingAfterAudioDoesNotReplayIt() throws Exception {try(var f=new Fixture()){f.through(89000,94000);f.toggle(true);f.at(94250);f.toggle(false);f.at(94500);assertEquals(1,f.port.starts);}}
    /** A real manual pause survives preference changes and has a reversible, non-destructive resume. */
    @Test public void manualPauseSurvivesModeChangesAndResumesText() throws Exception {try(var f=new Fixture()){f.through(32000,35000);f.guard.suspend();f.runtime.suspendComments("USER_SUSPENDED");f.toggle(true);f.at(35250);f.toggle(false);f.at(35500);assertNull(f.visible);assertTrue(f.guard.manuallySuspended());
        f.guard.requestResume();f.at(35750);assertNull(f.visible);f.at(36000);assertNotNull(f.visible);assertFalse(f.guard.manuallySuspended());assertEquals(1,f.runtime.diagnostics().textDue);}}
    /** Switching a calibrated mode does not masquerade as a user-requested backward replay. */
    @Test public void calibrationModeChangeDoesNotRearmConsumedAudio() throws Exception {try(var f=new Fixture()){f.toggle(true);f.through(89000,94000);assertEquals(1,f.port.starts);assertTrue(f.guard.calibrate(104000,f.capture));f.runtime.policyChanged();f.at(94250);
        f.toggle(false);f.through(94500,96000);assertEquals(1,f.port.starts);}}
}
