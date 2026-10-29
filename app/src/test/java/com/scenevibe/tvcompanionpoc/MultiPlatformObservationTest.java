package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector.Marker;
import com.scenevibe.tvcompanionpoc.ad.AdProtectionController;
import com.scenevibe.tvcompanionpoc.ad.AdProtectionPlatformScope;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog.Frame;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog.Platform;
import com.scenevibe.tvcompanionpoc.ad.MultiPlatformObservationLog.ValueType;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic public-scalar campaigns test the recorder, never Netflix/Disney+ publication on Sony. */
public final class MultiPlatformObservationTest {
    /** Build a deliberately synthetic scalar fixture with no programme title, identity or token. */
    private Frame frame(int session,long time,long position,int state,Marker marker){return new Frame(session,state,time,position,position,1f,0,900000,marker,marker==Marker.ABSENT?ValueType.ABSENT:ValueType.LONG);}
    /** Deliver one synthetic poll; sample time is not a reconstructed film clock. */
    private JSONObject record(MultiPlatformObservationLog log,Frame frame){log.capture(List.of(frame),1,1,frame.capturedMs,100000+frame.capturedMs);return log.status(frame.capturedMs);}
    /** Netflix publishes only its supplied synthetic flag; the record remains insufficient physical evidence. */
    @Test public void netflixFlagRecordedWithoutProtection() throws Exception {var log=new MultiPlatformObservationLog(Platform.NETFLIX);var status=record(log,frame(1,1000,90000,3,Marker.AD));
        assertEquals("AD",status.getJSONArray("samples").getJSONObject(0).getString("marker"));assertEquals("NETFLIX",status.getString("platform"));assertEquals("INSUFFICIENT_EVIDENCE",status.getString("conclusion"));}
    /** Disney+ remains its own campaign rather than an alias for Netflix observations. */
    @Test public void disneyFlagRecordedSeparately() throws Exception {var log=new MultiPlatformObservationLog(Platform.DISNEY_PLUS);var status=record(log,frame(1,1000,90000,3,Marker.AD));assertEquals("DISNEY_PLUS",status.getString("platform"));assertEquals("com.disney.disneyplus",status.getString("package"));}
    /** An absent Netflix key is recorded as absent, never as non-advertising content. */
    @Test public void netflixAbsentDoesNotProveContent() throws Exception {var log=new MultiPlatformObservationLog(Platform.NETFLIX);assertEquals("ABSENT",record(log,frame(1,1000,1,3,Marker.ABSENT)).getJSONArray("samples").getJSONObject(0).getString("marker"));}
    /** Absence on Disney+ is equally inconclusive even during progressive playback. */
    @Test public void disneyAbsentDoesNotProveContent() throws Exception {var log=new MultiPlatformObservationLog(Platform.DISNEY_PLUS);for(int i=0;i<20;i++)record(log,frame(1,1000+i*1000,1000+i*1000,3,Marker.ABSENT));assertEquals("INSUFFICIENT_EVIDENCE",log.status(20000).getString("conclusion"));}
    /** Malformed marker types do not silently become numeric zero. */
    @Test public void malformedTypeRemainsInvalid() throws Exception {var log=new MultiPlatformObservationLog(Platform.NETFLIX);var f=new Frame(1,3,1000,1,1,1f,0,1,Marker.INVALID,ValueType.STRING);assertEquals("INVALID",record(log,f).getJSONArray("samples").getJSONObject(0).getString("marker"));}
    /** Explicit zero, missing metadata and absent keys remain three separately observable facts. */
    @Test public void zeroIsNotAbsence() throws Exception {var log=new MultiPlatformObservationLog(Platform.DISNEY_PLUS);var f=frame(1,1000,1,3,Marker.CONTENT);f.metadataPresent=true;
        JSONObject row=record(log,f).getJSONArray("samples").getJSONObject(0);assertEquals("CONTENT",row.getString("marker"));assertTrue(row.getBoolean("metadataPresent"));}
    /** Normal pause, SEEK and buffering never invent a confirmed advertising event. */
    @Test public void ordinaryPlaybackIsOnlyDiagnostic() throws Exception {var log=new MultiPlatformObservationLog(Platform.NETFLIX);for(int state:new int[]{3,2,6,1,4,5,7,8,9,10,11}){
        String events=log.capture(List.of(frame(1,1000+state*100,900000-state*50000,state,Marker.ABSENT)),1,1,5000,100000).toString();assertFalse(events.contains("AD_START"));assertFalse(events.contains("AD_END"));}}
    /** Two concurrent sessions are exposed as ambiguous rather than silently collapsed. */
    @Test public void concurrentSessionsStayDistinct() throws Exception {var log=new MultiPlatformObservationLog(Platform.NETFLIX);log.capture(List.of(frame(1,1000,1,3,Marker.AD),frame(2,1000,2,2,Marker.ABSENT)),2,2,1000,100000);
        assertEquals("MULTIPLE_TARGET_SESSIONS",log.status(1000).getString("selection"));assertEquals(2,log.status(1000).getJSONArray("samples").length());}
    /** The full count remains visible when bounded retention cannot keep more than four callbacks. */
    @Test public void overflowIsExplicit() throws Exception {var log=new MultiPlatformObservationLog(Platform.DISNEY_PLUS);List<Frame> frames=new ArrayList<>();for(int i=1;i<=6;i++)frames.add(frame(i,1000,i,3,Marker.AD));log.capture(frames,6,6,1000,100000);
        assertTrue(log.status(1000).getBoolean("truncated"));assertEquals(4,log.status(1000).getJSONArray("samples").length());}
    /** Replaced sessions have distinct ephemeral ordinals and do not inherit the previous marker. */
    @Test public void replacedSessionDoesNotInheritFlag() throws Exception {var log=new MultiPlatformObservationLog(Platform.NETFLIX);record(log,frame(1,1000,1,3,Marker.AD));var row=record(log,frame(2,2000,2,3,Marker.ABSENT)).getJSONArray("samples").getJSONObject(0);assertEquals(2,row.getInt("session"));assertEquals("ABSENT",row.getString("marker"));}
    /** Lost MEDIA yields explicit unavailability; a later observation recovers the journal, not playback authority. */
    @Test public void lossAndRecoveryDoNotFabricateEnd() throws Exception {var log=new MultiPlatformObservationLog(Platform.NETFLIX);record(log,frame(1,1000,1,3,Marker.AD));assertEquals("UNAVAILABLE",log.unavailable(2000,200000).getString("event"));assertFalse(log.status(2000).getBoolean("fresh"));assertTrue(record(log,frame(2,3000,2,3,Marker.ABSENT)).getBoolean("fresh"));}
    /** A status read cannot renew the freshness of an old sample. */
    @Test public void staleStatusIsHonest() throws Exception {var log=new MultiPlatformObservationLog(Platform.NETFLIX);record(log,frame(1,1000,1,3,Marker.AD));assertFalse(log.status(4000).getBoolean("fresh"));assertEquals(3000,log.status(4000).getLong("sampleAgeMs"));}
    /** Owner annotations change only their independent count, never automatic samples. */
    @Test public void labelsNeverAlterSamples() throws Exception {var log=new MultiPlatformObservationLog(Platform.DISNEY_PLUS);record(log,frame(1,1000,1,3,Marker.ABSENT));String before=log.status(1000).getJSONArray("samples").toString();
        for(String label:List.of("AD_VISIBLE","CONTENT_VISIBLE","NORMAL_SEEK","NORMAL_PAUSE"))assertEquals("OWNER_ANNOTATION",log.annotate(label,1000,100000).getString("event"));assertEquals(before,log.status(1000).getJSONArray("samples").toString());assertEquals(4,log.status(1000).getInt("annotations"));}
    /** Labels outside the closed vocabulary cannot inject free text into logs. */
    @Test public void privateAnnotationIsRefused(){var log=new MultiPlatformObservationLog(Platform.NETFLIX);try{log.annotate("PRIVATE_TITLE_TOKEN",1000,100000);fail();}catch(IllegalArgumentException expected){assertEquals("ANNOTATION_REFUSED",expected.getMessage());}}
    /** OFF releases the observation journal and rejects future owner labels. */
    @Test public void disabledRecorderCannotContinue() throws Exception {var log=new MultiPlatformObservationLog(Platform.NETFLIX);log.close(1000,100000);assertTrue(log.capture(List.of(frame(1,2000,1,3,Marker.AD)),1,1,2000,200000).isEmpty());assertFalse(log.status(2000).getBoolean("enabled"));try{log.annotate("AD_VISIBLE",2000,200000);fail();}catch(IllegalStateException expected){assertEquals("OBSERVATION_DISABLED",expected.getMessage());}}
    /** Exact package allowlists exclude other providers and cannot be widened through a phone value. */
    @Test public void packageScopeIsExact(){var n=new MultiPlatformObservationLog(Platform.NETFLIX);assertTrue(n.accepts("com.netflix.ninja"));assertFalse(n.accepts("com.disney.disneyplus"));assertFalse(n.accepts("com.netflix.ninja.evil"));}
    /** No target session is still a logged observation, including full API counts and later count changes. */
    @Test public void noTargetAndCountChangesAreJournaled() throws Exception {var log=new MultiPlatformObservationLog(Platform.NETFLIX);
        var first=log.capture(List.of(),2,0,1000,100000);assertEquals(1,first.size());assertEquals(2,first.get(0).getInt("activeSessionCount"));
        assertTrue(log.capture(List.of(),2,0,1250,100250).isEmpty());assertEquals(1,log.capture(List.of(),3,0,1500,100500).size());assertFalse(log.status(1500).getBoolean("fresh"));}
    /** Neither platform can reach Prime's guard, even in a disposable debug build. */
    @Test public void protectionScopeRejectsBothPlatforms(){for(Platform p:Platform.values()){assertFalse(AdProtectionPlatformScope.permits(p.packageName,false));assertFalse(AdProtectionPlatformScope.permits(p.packageName,true));}assertTrue(AdProtectionPlatformScope.permits(AdProtectionPlatformScope.PRIME,false));assertFalse(AdProtectionPlatformScope.permits("com.scenevibe.tvcompanionpoc",false));assertTrue(AdProtectionPlatformScope.permits("com.scenevibe.tvcompanionpoc",true));}
    /** A populated Netflix journal cannot change an independent Prime machine's reference decision. */
    @Test public void noImplicitPrimeGuardConnection(){var prime=new AdProtectionController();var log=new MultiPlatformObservationLog(Platform.NETFLIX);record(log,frame(1,1000,1,3,Marker.AD));assertTrue(prime.status().allow);assertFalse(prime.status().enabled);}
    /** Public inventories are allowlisted even if a caller supplies a string containing credentials. */
    @Test public void arbitraryInventoryTextIsRemoved() throws Exception {var log=new MultiPlatformObservationLog(Platform.DISNEY_PLUS);Frame f=frame(1,1000,1,3,Marker.AD);f.metadataKeys="PRIVATE_TITLE TOKEN";f.sessionExtras="Bearer PRIVATE_SECRET";String text=record(log,f).toString();assertFalse(text.contains("PRIVATE"));assertFalse(text.contains("Bearer"));assertTrue(text.contains("OMITTED"));}
    /** A caller cannot change retained samples by editing the returned phone projection. */
    @Test public void statusIsDetached() throws Exception {var log=new MultiPlatformObservationLog(Platform.NETFLIX);var status=record(log,frame(1,1000,1,3,Marker.AD));status.getJSONArray("samples").getJSONObject(0).put("marker","CONTENT");assertEquals("AD",log.status(1000).getJSONArray("samples").getJSONObject(0).getString("marker"));}
    /** Execute the archived synthetic cases; no real platform publication is inferred from their success. */
    @Test public void archivedSyntheticFixturesReplayExactly() throws Exception {
        java.nio.file.Path path=java.nio.file.Path.of("qualification/multiplatform001/cases.json");if(!java.nio.file.Files.exists(path))path=java.nio.file.Path.of("..").resolve(path);
        var cases=new JSONObject(new String(java.nio.file.Files.readAllBytes(path),java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("cases");
        for(int i=0;i<cases.length();i++){var item=cases.getJSONObject(i);var log=new MultiPlatformObservationLog(Platform.valueOf(item.getString("platform")));
            var frames=item.getJSONArray("frames");for(int j=0;j<frames.length();j++){var row=frames.getJSONObject(j);Frame f=new Frame(row.getInt("session"),row.getInt("state"),row.getLong("capturedMs"),row.getLong("rawMs"),row.getLong("rawMs"),1f,0,900000,
                    Marker.valueOf(row.getString("marker")),ValueType.valueOf(row.getString("type")));
                assertEquals(row.getString("marker"),record(log,f).getJSONArray("samples").getJSONObject(0).getString("marker"));}
            assertEquals("INSUFFICIENT_EVIDENCE",log.status(2000).getString("conclusion"));}
    }
}
