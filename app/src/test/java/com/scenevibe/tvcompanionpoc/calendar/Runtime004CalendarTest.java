package com.scenevibe.tvcompanionpoc.calendar;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Actual nineteen-event Columbo coordinates, actual shared scheduler, controlled
 * software captures and recording effects. No Android, acoustic or Sony claim.
 */
public final class Runtime004CalendarTest {
    /** Own one verified source description and record the scheduler's typed effects. */
    private static final class Fixture implements MediaCalendarScheduler.Sink {
        final List<SceneEvent> events=new ArrayList<>();final Set<String> audio=new HashSet<>();
        final List<String> due=new ArrayList<>(),restored=new ArrayList<>();
        final MediaCalendarScheduler scheduler;
        Object session="own-session";long capture=100000;
        /** Read unchanged source coordinates; neither retime events nor generate resources. */
        Fixture() throws Exception {
            Path path=Path.of("src/androidTest/assets/cloudbridge003e/profile.json");if(!Files.exists(path))path=Path.of("app/src/androidTest/assets/cloudbridge003e/profile.json");
            JSONObject profile=new JSONObject(new String(Files.readAllBytes(path),java.nio.charset.StandardCharsets.UTF_8)),track=new JSONObject(new String(Base64.getDecoder().decode(profile.getString("trackBase64")),java.nio.charset.StandardCharsets.UTF_8));
            var source=track.getJSONArray("events");for(int i=0;i<source.length();i++){var event=source.getJSONObject(i);String id=event.getString("id");
                events.add(new SceneEvent(id,event.getLong("startMs"),event.getLong("durationMs")));if("AUDIO".equals(event.getString("type")))audio.add(id);}
            scheduler=new MediaCalendarScheduler(this,true,true);scheduler.load(new MediaCalendar(events,true),"owned",audio);
        }
        /** Deliver a controlled 250-ms capture; this is not an inferred platform position. */
        void at(long position){at(position,true);}
        /** Publish a usable paused or playing observation on the same public-session model. */
        void at(long position,boolean playing){capture+=250;scheduler.onObservation(new MediaObservation(true,position,playing),session,capture,playing?1f:0f);}
        /** Traverse supplied content time continuously without a second scheduler. */
        void through(long start,long end){for(long p=start;p<=end;p+=250)at(p);}
        /** Return a public audio ordinal in the source's real timeline order. */
        String audio(int ordinal){return events.stream().filter(e->audio.contains(e.eventId())).skip(ordinal-1).findFirst().orElseThrow().eventId();}
        /** Count observed DUE edges separately from native STARTED acknowledgements. */
        long count(String id){return due.stream().filter(id::equals).count();}
        /** Eligibility belongs to the native renderer integration suite. */
        @Override public void onEligibility(String token,boolean eligible){}
        /** No recording port owns a player transport or a playback clock. */
        @Override public void onPlayback(String token,boolean playing,boolean freeze){}
        /** Record one traversal; it is deliberately not labelled played. */
        @Override public void onDue(String token,String id){due.add(id);}
        /** Track current-window restoration independently of another event consumption. */
        @Override public void onTextRestore(String token,String id){restored.add(id);}
        /** Window disposal has no acoustic meaning in a pure scheduler test. */
        @Override public void onExpire(String token,String id){}
    }

    /** The real track contains eleven TEXT by 15:05; five later events are not artificially moved. */
    @Test public void continuousFifteenMinutesRetainActualEditorialTimes() throws Exception {var f=new Fixture();f.through(0,905000);
        assertEquals(11,f.due.stream().filter(id->!f.audio.contains(id)).count());for(int i=1;i<=3;i++)assertEquals(1,f.count(f.audio(i)));assertEquals(5,f.scheduler.diagnostics().future);}
    /** Extending to the actual final window qualifies all sixteen TEXT and three AUDIO DUE. */
    @Test public void completeOriginalTimelineHasNineteenDueEvents() throws Exception {var f=new Fixture();f.through(0,1714000);assertEquals(19,f.due.size());assertEquals(16,f.scheduler.diagnostics().textDue);assertEquals(3,f.scheduler.diagnostics().audioDue);}
    /** Each original MP3 becomes due once per stable lower passage, without three-at-once replay. */
    @Test public void eachAudioRearmsAfterConfirmedTenSecondRewind() throws Exception {
        for(int ordinal=1;ordinal<=3;ordinal++){var f=new Fixture();long start=new long[]{90000,300000,900000}[ordinal-1];
            f.through(start-1000,start+4000);assertEquals(1,f.count(f.audio(ordinal)));f.scheduler.onInterruption();
            f.through(start-10000,start+4000);assertEquals(2,f.count(f.audio(ordinal)));assertEquals(1,f.scheduler.audioDiagnostics(f.audio(ordinal)).rearmed);
            for(int i=0;i<8;i++)f.at(start+4000);assertEquals(2,f.count(f.audio(ordinal)));}
    }
    /** A completed phrase is not restarted by a normal pause/resume. */
    @Test public void normalPauseDoesNotRearm() throws Exception {var f=new Fixture();f.through(89000,94000);f.at(94000,false);f.at(94000,false);f.at(94000);assertEquals(1,f.count(f.audio(1)));assertEquals(0,f.scheduler.audioDiagnostics(f.audio(1)).rearmed);}
    /** Session loss with forward progress preserves consumed audio. */
    @Test public void absenceWithoutRewindDoesNotReplay() throws Exception {var f=new Fixture();f.through(89000,94000);f.scheduler.onInterruption();f.capture+=8000;f.through(102000,104000);assertEquals(1,f.count(f.audio(1)));assertEquals(0,f.scheduler.audioDiagnostics(f.audio(1)).rearmed);}
    /** Single lower outliers and alternating positions cannot satisfy stable rewind evidence. */
    @Test public void oscillationsDoNotRearmAudio() throws Exception {var f=new Fixture();f.through(89000,94000);for(int i=0;i<5;i++){f.at(80000);f.at(94000);}assertEquals(1,f.count(f.audio(1)));assertEquals(0,f.scheduler.audioDiagnostics(f.audio(1)).rearmed);}
    /** A changed session alone is ambiguous, even when its lower clock progresses. */
    @Test public void newLowerSessionNeedsOptionalExplicitReplay() throws Exception {var f=new Fixture();f.through(89000,94000);f.scheduler.onInterruption();f.session="replacement";f.through(80000,94000);assertEquals(1,f.count(f.audio(1)));
        f.at(80000);f.scheduler.onConfirmedSeek();f.through(80250,94000);assertEquals(2,f.count(f.audio(1)));}
    /** Revisiting the episode beginning makes both event types available at their unchanged times. */
    @Test public void confirmedRestartReplaysWholeTrack() throws Exception {var f=new Fixture();f.through(0,1714000);f.scheduler.onInterruption();f.through(0,1714000);
        assertEquals(38,f.due.size());for(int i=1;i<=3;i++)assertEquals(2,f.count(f.audio(i)));}
    /** A forward jump skips missed windows, with an explicit non-playback outcome. */
    @Test public void forwardSeekNeverBurstsSkippedAudio() throws Exception {var f=new Fixture();f.at(0);f.at(905000);assertTrue(f.due.isEmpty());
        for(int i=1;i<=3;i++){var a=f.scheduler.audioDiagnostics(f.audio(i));assertEquals(0,a.due);assertEquals(1,a.skipped);assertEquals(0,a.started);assertTrue(a.consumedWithoutStart);}}
    /** Missing MEDIA at 15:00 cannot count a native playback; a later confirmed return makes it due again. */
    @Test public void missedFifteenMinuteWindowCanBeRevisited() throws Exception {var f=new Fixture();f.through(889000,899000);f.scheduler.onInterruption();f.capture+=6000;f.at(905000);
        var missed=f.scheduler.audioDiagnostics(f.audio(3));assertEquals(0,missed.due);assertEquals(0,missed.started);assertTrue(missed.consumedWithoutStart);
        f.through(890000,905000);assertEquals(1,f.count(f.audio(3)));assertEquals(1,f.scheduler.audioDiagnostics(f.audio(3)).rearmed);}
    /** A preference change on a compatible current TEXT preserves consumption, visibility contract and the next window. */
    @Test public void policyToggleInsideTextDoesNotConsumeAgain() throws Exception {var f=new Fixture();f.through(32000,35000);int consumed=f.scheduler.diagnostics().consumed;f.scheduler.onPolicyChanged();f.at(35250);
        assertEquals(consumed,f.scheduler.diagnostics().consumed);assertEquals(1,f.scheduler.diagnostics().textDue);assertTrue(f.restored.isEmpty());assertEquals(52988,f.scheduler.diagnostics().nextStartMs);}
    /** Returning inside the original eight-second window restores it without replaying AUDIO or another DUE. */
    @Test public void invalidMediaThenPolicyToggleRestoresCurrentText() throws Exception {var f=new Fixture();f.through(32000,35000);f.scheduler.onInterruption();f.scheduler.onPolicyChanged();f.at(36000);
        assertEquals(1,f.restored.size());assertEquals(1,f.scheduler.diagnostics().textDue);assertEquals(1,f.scheduler.diagnostics().consumed);assertEquals(52988,f.scheduler.diagnostics().nextStartMs);}
    /** An expired TEXT is never restored late after uncertainty. */
    @Test public void expiredTextIsNotRestored() throws Exception {var f=new Fixture();f.through(32000,35000);f.scheduler.onInterruption();f.at(42000);assertTrue(f.restored.isEmpty());assertEquals(52988,f.scheduler.diagnostics().nextStartMs);}
    /** Native preparation/refusal acknowledgements do not magically count as STARTED. */
    @Test public void duePreparedAndStartedRemainSeparateFacts() throws Exception {var f=new Fixture();f.through(89000,90000);String id=f.audio(1);
        f.scheduler.recordAudio(id,"AUDIO_PREPARE_REQUESTED","NATIVE_PLAYER");f.scheduler.recordAudio(id,"AUDIO_PREPARED","NATIVE_PLAYER");f.scheduler.recordAudio(id,"AUDIO_START_REFUSED","MEDIA_UNAVAILABLE");
        var a=f.scheduler.audioDiagnostics(id);assertEquals(1,a.due);assertEquals(1,a.prepared);assertEquals(0,a.started);assertEquals(1,a.refused);assertTrue(a.consumedWithoutStart);
        f.scheduler.recordAudio(id,"AUDIO_RESOURCE_STARTED","NATIVE_PLAYER");assertFalse(f.scheduler.audioDiagnostics(id).consumedWithoutStart);}
}
