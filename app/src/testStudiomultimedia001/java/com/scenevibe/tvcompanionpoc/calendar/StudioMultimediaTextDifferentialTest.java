package com.scenevibe.tvcompanionpoc.calendar;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Black-box comparison against the frozen, Git-verified M5 temporal core.
 * The oracle is qualification-only, never an additional runtime scheduler.
 * It checks callback order, event consumption, pause, unavailable and seek traces
 * for TEXT calendars, whose existing behavior must remain byte-for-byte equivalent.
 */
public final class StudioMultimediaTextDifferentialTest {
    /** Compare 100 reproducible temporal traces including exact historical boundaries. */
    @Test public void preservesHistoricalTextCallbackTraces() {
        for(int seed=0;seed<100;seed++) {
            List<String> before=new ArrayList<>(),after=new ArrayList<>();
            HistoricalMediaCalendarScheduler original=new HistoricalMediaCalendarScheduler(new HistoricalMediaCalendarScheduler.Sink() {
                /** Record eligibility in original callback order. */
                @Override public void onEligibility(String token,boolean value) { before.add("e:"+token+":"+value); }
                /** Record the original pause policy and playback classification. */
                @Override public void onPlayback(String token,boolean playing,boolean freeze) { before.add("p:"+token+":"+playing+":"+freeze); }
                /** Record exact event identities without normalization. */
                @Override public void onDue(String token,String id) { before.add("d:"+token+":"+id); }
                /** Record each original window expiry. */
                @Override public void onExpire(String token,String id) { before.add("x:"+token+":"+id); }
            });
            MediaCalendarScheduler candidate=new MediaCalendarScheduler(new MediaCalendarScheduler.Sink() {
                /** Record candidate eligibility in callback order. */
                @Override public void onEligibility(String token,boolean value) { after.add("e:"+token+":"+value); }
                /** Record candidate playback without deriving time. */
                @Override public void onPlayback(String token,boolean playing,boolean freeze) { after.add("p:"+token+":"+playing+":"+freeze); }
                /** Record candidate event identities. */
                @Override public void onDue(String token,String id) { after.add("d:"+token+":"+id); }
                /** Record candidate expiry order. */
                @Override public void onExpire(String token,String id) { after.add("x:"+token+":"+id); }
            });
            List<SceneEvent> events=new ArrayList<>();
            for(int index=0;index<40;index++) events.add(new SceneEvent("text-"+index,(index/2)*2500L,(index%4)*1000L));
            MediaCalendar calendar=new MediaCalendar(events,seed%2==0);
            original.load(calendar,"token");candidate.load(calendar,"token");
            Random random=new Random(seed);long position=0;
            for(int observation=0;observation<150;observation++) {
                if(observation%19==0) { original.onUnavailable();candidate.onUnavailable(); }
                long[] jumps={0,500,1000,2000,2001,5000,5001,-2000,-2001,20_000,-30_000};
                position=Math.max(0,position+jumps[random.nextInt(jumps.length)]);
                MediaObservation selected=new MediaObservation(observation%23!=0,observation%31==0?-1:position,random.nextBoolean());
                original.onObservation(selected);candidate.onObservation(selected);
                assertEquals("seed="+seed+" observation="+observation,before,after);
            }
            original.clear();candidate.clear();assertEquals(before,after);
        }
    }
}
