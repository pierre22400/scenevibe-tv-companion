package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.calendar.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Exceptions interrupt both real engines at the same callback; following inputs expose prior mutations. */
@RunWith(Parameterized.class)
public final class M5SinkFailureDifferentialTest {
    private final String boundary;
    /** Select one immediate historical mutation boundary. */
    public M5SinkFailureDifferentialTest(String boundary){this.boundary=boundary;}
    /** Every required failure boundary is executed, without ignores or exception broadening. */
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> boundaries(){return Arrays.asList(new Object[][]{{"ELIGIBLE"},{"PLAYBACK"},{"DUE"},{"EXPIRE"}});}
    /** A single precise test exception, never caught by production. */
    private static final class SinkFailure extends RuntimeException {}
    /** Invoke only existing private fixture/loader accessors; the frozen sources remain untouched. */
    private static Object frozen(String name,Class<?>[] types,Object... args) throws Exception {Method m=M5FrozenLegacyOracle.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);}
    /** Run the same recovery inputs after a throw, including a partially drained multiwindow expiry. */
    private List<String> run(boolean candidate) throws Exception {
        JSONObject f=new JSONObject("{events:[{id:a,start:0,duration:10},{id:b,start:0,duration:10}]}");
        List<String> log=new ArrayList<>();boolean[] thrown={false};
        ClassLoader loader=candidate?MediaCalendarScheduler.class.getClassLoader():(ClassLoader)frozen("loader",new Class<?>[0]);
        Class<?> sink=candidate?MediaCalendarScheduler.Sink.class:loader.loadClass("com.scenevibe.tvcompanionpoc.MediaSyncedTrackScheduler$Listener");
        Object listener=Proxy.newProxyInstance(loader,new Class<?>[]{sink},(p,m,a)->{
            String kind=m.getName().equals("onEligibility")?((boolean)a[candidate?1:0]?"ELIGIBLE":"INELIGIBLE"):
                    m.getName().equals("onPlayback")?"PLAYBACK":m.getName().equals("onExpire")?"EXPIRE":"DUE";
            String detail="";
            if(kind.equals("DUE")||kind.equals("EXPIRE"))detail=candidate?(String)a[1]:(String)frozen("eventId",new Class<?>[]{Object.class},a[0]);
            if(kind.equals("PLAYBACK"))detail=a[candidate?1:0]+":"+a[candidate?2:1];
            log.add(kind+":"+detail);
            if(kind.equals(boundary)&&!thrown[0]){thrown[0]=true;throw new SinkFailure();}return null;
        });
        Object engine=candidate?new MediaCalendarScheduler((MediaCalendarScheduler.Sink)listener):loader.loadClass("com.scenevibe.tvcompanionpoc.MediaSyncedTrackScheduler").getConstructor(sink).newInstance(listener);
        ScheduledTrack track=M5CDifferentialHarness.track(f,new JSONObject());
        if(candidate)((MediaCalendarScheduler)engine).load(M5VideoTestProjection.project(track),"activation");
        else {Object t=frozen("track",new Class<?>[]{JSONObject.class,JSONObject.class},f,new JSONObject());engine.getClass().getMethod("load",t.getClass()).invoke(engine,t);}
        for(long position:new long[]{0,0,10,10,0,0,10}){
            log.add("INPUT:"+position);
            JSONObject action=new JSONObject().put("position",position);
            try {
                if(candidate)((MediaCalendarScheduler)engine).onObservation(VideoMediaObservationAdapter.observe(track,M5CDifferentialHarness.snapshot(action)));
                else {Object v=frozen("snapshot",new Class<?>[]{JSONObject.class},action);engine.getClass().getMethod("onPlaybackSnapshot",v.getClass()).invoke(engine,v);}
            } catch(InvocationTargetException e){assertEquals(SinkFailure.class,e.getCause().getClass());log.add("THROWN");}
              catch(SinkFailure e){log.add("THROWN");}
        }
        assertTrue(thrown[0]);assertEquals(1,Collections.frequency(log,"THROWN"));return log;
    }
    /** Exact callback and subsequent-input equality proves the mutation is not deferred or retried. */
    @Test public void callbackExceptionAndFollowingInputsMatchFrozenLegacy() throws Exception {assertEquals(run(false),run(true));}
}
