package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.calendar.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercise pure inputs and opaque binding tokens independently of Video and the frozen journal. */
public final class M5SchedulerDirectTest {
    /** Record synchronous effects and allow a test to reenter at an exact callback boundary. */
    private static final class Recorder implements MediaCalendarScheduler.Sink {
        final List<String> effects=new ArrayList<>(); Runnable eligibleHook,dueHook;
        /** Record the producing activation before any reentrant operation. */
        public void onEligibility(String t,boolean e){effects.add(t+":E:"+e);if(e && eligibleHook!=null){Runnable h=eligibleHook;eligibleHook=null;h.run();}}
        /** Keep repetitions and both supplied flags. */
        public void onPlayback(String t,boolean p,boolean f){effects.add(t+":P:"+p+":"+f);}
        /** Reenter only after recording the consumed event. */
        public void onDue(String t,String id){effects.add(t+":D:"+id);if(dueHook!=null){Runnable h=dueHook;dueHook=null;h.run();}}
        /** Preserve immediate removal order. */
        public void onExpire(String t,String id){effects.add(t+":X:"+id);}
    }
    /** Build immutable synthetic events without Video payload or parser. */
    private static MediaCalendar calendar(long... values){List<SceneEvent> e=new ArrayList<>();for(int i=0;i<values.length;i+=2)e.add(new SceneEvent("e"+(i/2),values[i],values[i+1]));return new MediaCalendar(e,true);}
    /** Advance exclusively from a supplied observation. */
    private static void observe(MediaCalendarScheduler s,long p,boolean playing){s.onObservation(new MediaObservation(true,p,playing));}
    /** Null means no-op even after eligibility and a window exist. */
    @Test public void nullAndEmptyInputsAreNoOp(){Recorder r=new Recorder();MediaCalendarScheduler s=new MediaCalendarScheduler(r);s.onUnavailable();s.onObservation(null);observe(s,0,true);assertTrue(r.effects.isEmpty());s.load(calendar(0,100),"a");observe(s,0,true);r.effects.clear();s.onObservation(null);assertTrue(r.effects.isEmpty());observe(s,100,true);assertEquals(Arrays.asList("a:P:true:true","a:X:e0"),r.effects);}
    /** Loss drops windows silently, retains consumption and emits false only once. */
    @Test public void repeatedLossAndRecoveryPreserveConsumption(){Recorder r=new Recorder();MediaCalendarScheduler s=new MediaCalendarScheduler(r);s.load(calendar(0,100),"a");observe(s,0,true);r.effects.clear();s.onUnavailable();s.onUnavailable();s.onObservation(new MediaObservation(false,-1,false));assertEquals(Arrays.asList("a:E:false"),r.effects);observe(s,50,true);assertEquals(Arrays.asList("a:E:false","a:E:true","a:P:true:true"),r.effects);}
    /** Unknown position forwards playback but cannot anchor, consume or expire. */
    @Test public void unknownPositionAndPausedAnchor(){Recorder r=new Recorder();MediaCalendarScheduler s=new MediaCalendarScheduler(r);s.load(calendar(0,10),null);observe(s,-7,true);observe(s,0,false);assertFalse(r.effects.contains("null:D:e0"));observe(s,0,true);assertTrue(r.effects.contains("null:D:e0"));}
    /** The late boundary is inclusive; strictly older events are silently consumed. */
    @Test public void lateness1999And2000And2001(){for(long late:new long[]{1999,2000,2001}){Recorder r=new Recorder();MediaCalendarScheduler s=new MediaCalendarScheduler(r);s.load(calendar(1000,1),"a");observe(s,1000+late,true);assertEquals(late<=2000,r.effects.contains("a:D:e0"));if(late<=2000)assertTrue(r.effects.indexOf("a:D:e0")<r.effects.indexOf("a:X:e0"));}}
    /** Forward strict threshold applies even while paused and consumes the landing start. */
    @Test public void forward5000And5001(){for(long delta:new long[]{4999,5000,5001}){Recorder r=new Recorder();MediaCalendarScheduler s=new MediaCalendarScheduler(r);s.load(calendar(delta,100),"a");observe(s,0,false);observe(s,delta,true);assertEquals(delta<=5000,r.effects.contains("a:D:e0"));}}
    /** Only a displacement below -2000 rearms already consumed IDs. */
    @Test public void backward2000And2001(){for(long delta:new long[]{1999,2000,2001}){Recorder r=new Recorder();MediaCalendarScheduler s=new MediaCalendarScheduler(r);s.load(calendar(1000,10000),"a");observe(s,1000,true);observe(s,3001,true);r.effects.clear();observe(s,3001-delta,true);observe(s,1001,true);assertEquals(delta==2001,r.effects.contains("a:D:e0"));}}
    /** Equal-start stable ordering remains one event per playing observation. */
    @Test public void oneDueStableOrderAndNonpositiveDurations(){Recorder r=new Recorder();MediaCalendarScheduler s=new MediaCalendarScheduler(r);s.load(calendar(0,0,0,-9),"a");observe(s,0,true);assertTrue(r.effects.contains("a:D:e0"));assertFalse(r.effects.contains("a:D:e1"));observe(s,0,true);observe(s,4000,true);assertTrue(r.effects.contains("a:D:e1"));assertFalse(r.effects.stream().anyMatch(x->x.contains(":X:")));}
    /** Multiple positive windows drain only on a playing advance or strict forward seek. */
    @Test public void simultaneousWindowsAndPausedForwardExpiry(){Recorder r=new Recorder();MediaCalendarScheduler s=new MediaCalendarScheduler(r);s.load(calendar(0,10000,0,10000),"a");observe(s,0,true);observe(s,0,true);r.effects.clear();observe(s,10000,false);assertEquals(3,r.effects.size());assertEquals("a:P:false:true",r.effects.get(0));assertTrue(r.effects.contains("a:X:e0"));assertTrue(r.effects.contains("a:X:e1"));}
    /** Replacement and clear invalidate the old binding and reset consumption. */
    @Test public void loadClearOldBindingAndNoCursor(){Recorder r=new Recorder();MediaCalendarScheduler s=new MediaCalendarScheduler(r);s.load(calendar(0,0),"a");observe(s,0,true);s.load(calendar(0,0),"b");observe(s,0,true);s.clear();s.clear();assertEquals(Arrays.asList("null:E:false","a:E:true","a:P:true:true","a:D:e0","a:E:false","b:E:true","b:P:true:true","b:D:e0","b:E:false","null:E:false"),r.effects);}
    /** A reentrant load cannot retag subsequent callbacks of the enclosing transition. */
    @Test public void reentrantEligibilityLoadCapturesEntryToken(){Recorder r=new Recorder();MediaCalendarScheduler s=new MediaCalendarScheduler(r);s.load(calendar(0,0),"a");r.effects.clear();r.eligibleHook=()->s.load(calendar(0,0),"b");observe(s,0,true);assertEquals(Arrays.asList("a:E:true","a:E:false","a:P:true:true","a:D:e0"),r.effects);observe(s,0,true);assertTrue(r.effects.contains("b:E:true"));}
    /** DUE reentrance preserves the outer token on expiry after a nested observation. */
    @Test public void reentrantDueLoadAndNestedObservation(){Recorder r=new Recorder();MediaCalendarScheduler s=new MediaCalendarScheduler(r);s.load(calendar(0,1),"a");r.effects.clear();r.dueHook=()->{s.load(calendar(0,1),"b");observe(s,1,true);};observe(s,1,true);assertTrue(r.effects.contains("a:D:e0"));assertTrue(r.effects.contains("b:D:e0"));assertTrue(r.effects.contains("b:X:e0"));}
}
