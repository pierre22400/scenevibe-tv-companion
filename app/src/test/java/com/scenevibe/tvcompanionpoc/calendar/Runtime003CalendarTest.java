package com.scenevibe.tvcompanionpoc.calendar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/** Reproduce the Sony coordinates on the actual calendar, without inventing MEDIA or rendering evidence. */
public final class Runtime003CalendarTest {
    /** Record typed DUE effects from the actual scheduler, not its implementation's internal sets. */
    private static final class Effects implements MediaCalendarScheduler.Sink {
        final List<String> due=new ArrayList<>();
        /** Eligibility is observed by the native integration suite. */
        @Override public void onEligibility(String token,boolean eligible){}
        /** This fixture never owns a playback clock or a platform command. */
        @Override public void onPlayback(String token,boolean playing,boolean freeze){}
        /** Capture the effect selected at the real calendar boundary. */
        @Override public void onDue(String token,String id){due.add(id);}
        /** Expiration has no drawing side effects in this pure calendar fixture. */
        @Override public void onExpire(String token,String id){}
    }
    /** Keep the observed first TEXT timestamp and distinct logical audio windows. */
    private MediaCalendarScheduler scheduler(Effects effects,boolean recovery){
        var scheduler=new MediaCalendarScheduler(effects,recovery);
        scheduler.load(new MediaCalendar(List.of(new SceneEvent("text",32988,8000),new SceneEvent("audio1",90000,3000),
                new SceneEvent("audio2",300000,3000),new SceneEvent("audio3",900000,3000)),true),"own",Set.of("audio1","audio2","audio3"));return scheduler;
    }
    /** Feed selected compatible positions only; identity and capture validity are tested upstream. */
    private void at(MediaCalendarScheduler scheduler,long position){scheduler.onObservation(new MediaObservation(true,position,true));}
    /** Starting after an expired window is normal silence, not a broken renderer. */
    @Test public void lateInitialStartReportsConsumedTextAndNextAudio(){var effects=new Effects();var s=scheduler(effects,true);at(s,43788);
        assertTrue(effects.due.isEmpty());assertEquals(1,s.diagnostics().consumed);assertEquals(90000,s.diagnostics().nextStartMs);assertEquals(0,s.diagnostics().textDue);}
    /** The unchanged v22 policy reproduces the missing first TEXT after an interrupted rewind. */
    @Test public void legacyPolicyReproducesSonyRewindLoss(){var effects=new Effects();var s=scheduler(effects,false);at(s,43788);s.onInterruption();
        for(long p=20614;p<=39551;p+=250)at(s,p);assertTrue(effects.due.isEmpty());}
    /** Two lower coherent calendar observations recover future TEXT without an explicit re-download or new activation. */
    @Test public void lowerPlaybackRecoversFirstTextAtOriginalTimestamp(){var effects=new Effects();var s=scheduler(effects,true);at(s,43788);s.onInterruption();
        at(s,20614);assertTrue(s.diagnostics().interrupted);at(s,20864);assertFalse(s.diagnostics().interrupted);
        assertEquals(1,s.diagnostics().textRearmed);for(long p=21114;p<=39551;p+=250)at(s,p);
        assertEquals(List.of("text"),effects.due);assertEquals(1,s.diagnostics().textDue);}
    /** A single lower outlier followed by the former position never grants a replay. */
    @Test public void singleRegressingSampleCannotRearm(){var effects=new Effects();var s=scheduler(effects,true);at(s,43788);s.onInterruption();at(s,20614);at(s,44000);
        assertEquals(0,s.diagnostics().textRearmed);assertTrue(effects.due.isEmpty());}
    /** A true interruption return cannot restart consumed speech, even while future TEXT is restored. */
    @Test public void interruptedBackwardReturnRetainsConsumedAudio(){var effects=new Effects();var s=scheduler(effects,true);at(s,90000);assertEquals(List.of("audio1"),effects.due);
        s.onInterruption();at(s,89000);at(s,89250);at(s,90000);assertEquals(List.of("audio1"),effects.due);}
    /** Expired events never form a burst when MEDIA resumes far ahead. */
    @Test public void forwardReturnDoesNotReplayPastEvents(){var effects=new Effects();var s=scheduler(effects,true);at(s,20000);s.onInterruption();at(s,1000000);
        assertTrue(effects.due.isEmpty());assertEquals(4,s.diagnostics().consumed);assertEquals(0,s.diagnostics().future);}
    /** Repeated holds preserve the first accepted coordinate, rather than replacing it with UNKNOWN. */
    @Test public void repeatedInterruptionStillRecoversText(){var effects=new Effects();var s=scheduler(effects,true);at(s,43788);for(int i=0;i<8;i++)s.onInterruption();
        at(s,30009);at(s,30259);at(s,32988);assertEquals(List.of("text"),effects.due);}
    /** Three logical audio resources retain separate native calendar dispatch, despite content-addressed byte reuse. */
    @Test public void threeAudioEventsAreDistinct(){var effects=new Effects();var s=scheduler(effects,true);
        for(long start:new long[]{90000,300000,900000}){s.onInterruption();at(s,start);at(s,start+250);}
        assertEquals(List.of("audio1","audio2","audio3"),effects.due);assertEquals(3,s.diagnostics().audioDue);}
}
