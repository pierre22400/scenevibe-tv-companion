package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.calendar.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Compile temporary mutated copies of the real candidate; no production hook or fixture edit exists. */
public final class M5NegativeSensitivityTest {
    /** Compile just the candidate against the unchanged three value classes. */
    private static URLClassLoader mutant(String before,String after) throws Exception {
        Path source=Path.of("src/main/java/com/scenevibe/tvcompanionpoc/calendar/MediaCalendarScheduler.java");
        if(!Files.exists(source))source=Path.of("app").resolve(source);
        String text=Files.readString(source);assertEquals(1,text.split(java.util.regex.Pattern.quote(before),-1).length-1);
        Path temp=Files.createTempDirectory("m5c-mutant-");Path file=temp.resolve("MediaCalendarScheduler.java");Files.writeString(file,text.replace(before,after));
        Path classes=Files.createDirectory(temp.resolve("classes"));
        String cp=Path.of(MediaCalendar.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        Process process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-m","jdk.compiler/com.sun.tools.javac.Main","-cp",cp,"-d",classes.toString(),file.toString()).redirectErrorStream(true).redirectOutput(temp.resolve("compile.log").toFile()).start();
        assertTrue(process.waitFor(30,TimeUnit.SECONDS));assertEquals(0,process.exitValue());
        return new URLClassLoader(new URL[]{classes.toUri().toURL()},MediaCalendarScheduler.class.getClassLoader()){
            /** Isolate scheduler and its sink while retaining exact production model identity. */
            @Override protected synchronized Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {if(!name.startsWith(MediaCalendarScheduler.class.getName()))return super.loadClass(name,resolve);Class<?> c=findLoadedClass(name);if(c==null)c=findClass(name);if(resolve)resolveClass(c);return c;}
        };
    }
    /** Execute the temporary compiled scheduler against real immutable values and record raw effects. */
    private static M5TemporalJournal execute(JSONObject f,URLClassLoader loader) throws Exception {
        M5TemporalJournal j=new M5TemporalJournal();Class<?> type=loader.loadClass(MediaCalendarScheduler.class.getName());Class<?> sink=loader.loadClass(MediaCalendarScheduler.Sink.class.getName());
        Object listener=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{sink},(p,m,a)->{switch(m.getName()){case "onEligibility":j.append((boolean)a[1]?M5TemporalJournal.Kind.ELIGIBLE:M5TemporalJournal.Kind.INELIGIBLE,null,false,false);break;case "onPlayback":j.append(M5TemporalJournal.Kind.PLAYBACK,null,(boolean)a[1],(boolean)a[2]);break;case "onDue":j.append(M5TemporalJournal.Kind.DUE,(String)a[1],false,false);break;case "onExpire":j.append(M5TemporalJournal.Kind.EXPIRE,(String)a[1],false,false);break;default:throw new AssertionError();}return null;});
        Object e=type.getConstructor(sink).newInstance(listener);ScheduledTrack track=null;String token=null;JSONArray actions=f.getJSONArray("actions");
        for(int i=0;i<actions.length();i++){JSONObject a=actions.getJSONObject(i);if(a.has("token"))token=a.isNull("token")?null:a.getString("token");j.begin(token);switch(a.getString("op")){case "load":track=M5CDifferentialHarness.track(f,a);type.getMethod("load",MediaCalendar.class,String.class).invoke(e,M5VideoTestProjection.project(track),token);break;case "snapshot":type.getMethod("onObservation",MediaObservation.class).invoke(e,VideoMediaObservationAdapter.observe(track,M5CDifferentialHarness.snapshot(a)));break;default:throw new AssertionError("Unexpected targeted fixture operation");}j.end();}return j;
    }
    /** A deliberately simple trace is still compared to the true frozen engine, never to a hand-coded scheduler. */
    private static void rejects(String before,String after,String fixtureId) throws Exception {JSONObject f=null;JSONArray cases=new JSONObject(new String(M5FrozenLegacyOracle.resource("corpus.json"),StandardCharsets.UTF_8)).getJSONArray("cases");for(int i=0;i<cases.length();i++){JSONObject item=cases.getJSONObject(i);if(item.getString("id").equals(fixtureId))f=item;}assertNotNull(f);M5TemporalJournal oracle=M5FrozenLegacyOracle.execute(f);assertTrue(M5TemporalJournal.exactlyEqual(oracle.frames(),M5CDifferentialHarness.execute(f,true).frames()));try(URLClassLoader loader=mutant(before,after)){assertFalse("Comparator must reject the source mutation",M5TemporalJournal.exactlyEqual(oracle.frames(),execute(f,loader).frames()));}}
    /** Changing strict greater-than to greater-or-equal must fail at exactly +5000. */
    @Test public void rejectsInclusiveForwardThreshold() throws Exception {rejects("deltaMs > FORWARD_SEEK_THRESHOLD_MS","deltaMs >= FORWARD_SEEK_THRESHOLD_MS","forward-5000-state-3");}
    /** Removing the first-DUE return must expose the second callback in the same input. */
    @Test public void rejectsMultipleDuePerObservation() throws Exception {rejects("sink.onDue(token, event.eventId());\n            return;","sink.onDue(token, event.eventId());","stable-equal-starts");}
}
