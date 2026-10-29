package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector.Marker;
import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector.Sample;
import com.scenevibe.tvcompanionpoc.ad.AdProtectionController;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Faithfully replay exported Sony scalars; owner annotations are comparison labels, never detector input. */
public final class AdProtectionSonyReplayTest {
    /** Locate only the committed, redacted fixture in the Gradle app or repository working directory. */
    private JSONObject fixture(String name) throws Exception {Path p=Path.of("qualification/ad-detection002/fixtures/"+name+".json");
        if(!Files.exists(p))p=Path.of("..").resolve(p);return new JSONObject(Files.readString(p));}
    /** Replay actual records at their exported elapsedMs; do not interpolate the unlogged 250ms observations. */
    private void replay(String name,boolean appearance) throws Exception {
        JSONObject fixture=fixture(name);AdProtectionController guard=new AdProtectionController();guard.setEnabled(true);
        int edges=0;boolean sawGuard=false;long operationalEnd=-1,lastPositive=-1,firstPositive=-1,firstAbsentAfterAd=-1;
        for(Object item:fixture.getJSONArray("inputs")){JSONObject row=(JSONObject)item;long time=row.getLong("elapsedMs");
            Marker marker=Marker.valueOf(row.getString("marker"));Object session=row.getInt("session");
            AdProtectionController.Decision decision;
            if(row.getString("event").equals("METADATA_CALLBACK")){
                if(marker!=Marker.AD)continue;decision=guard.positive(session,time);
            }else{
                String state=row.getString("state");boolean playing="PLAYING".equals(state);long raw=row.getLong("rawMs"),position=row.getLong("selectedMs");float speed=(float)row.getDouble("speed");
                decision=guard.observe(new Sample(session,time,position,speed,true,row.getBoolean("identityCompatible"),playing,
                    raw>=0 && position>=0 && row.getLong("anchorAgeMs")>=0 && Float.isFinite(speed) && speed>0,marker),time);
            }
            if(marker==Marker.AD){if(firstPositive<0)firstPositive=time;lastPositive=time;assertFalse("Positive public flag did not inhibit",decision.allow);}
            if(firstPositive>=0 && marker==Marker.ABSENT && firstAbsentAfterAd<0)firstAbsentAfterAd=time;
            if(decision.state==AdProtectionController.State.AD_GUARD)sawGuard=true;
            if(decision.edge==AdProtectionController.Edge.AD_START)edges++;
            if(decision.edge==AdProtectionController.Edge.AD_END){operationalEnd=time;assertTrue(time>lastPositive);}
        }
        assertTrue(sawGuard);assertEquals(appearance?1:0,edges);assertTrue("No stable operational return",operationalEnd>0);
        assertEquals(1,guard.status().adEnds);assertTrue(guard.status().allow);
        assertTrue("Owner annotations must remain available separately",fixture.getJSONArray("owner_annotations").length()>0);
        JSONObject proof=new JSONObject().put("scope","SONY_SCALAR_REPLAY_NOT_NEW_PHYSICAL_PROTECTION_PASS").put("source",fixture.getJSONObject("source"))
            .put("firstPositiveObservedElapsedMs",firstPositive).put("markerDisappearanceElapsedMs",firstAbsentAfterAd)
            .put("operationalExitElapsedMs",operationalEnd).put("observedStarts",edges).put("operationalEnds",guard.status().adEnds)
            .put("ownerAnnotationsComparisonOnly",fixture.getJSONArray("owner_annotations"));
        Path output=Path.of("build/reports/ad002/sony-replay-"+name+".json");Files.createDirectories(output.getParent());Files.writeString(output,proof.toString(2));
    }
    /** The first capture begins during advertising: presence is protective even without an observed onset. */
    @Test public void firstSonyCaptureShowsPresenceAndOperationalReturn() throws Exception {replay("215514",false);}
    /** The main capture contains a genuine observed marker appearance followed by a separately verified return. */
    @Test public void secondSonyCaptureHasObservedAppearanceAndStableReturn() throws Exception {replay("222334",true);}
    /** The 53-byte header is no campaign and supplies zero automatic or owner events. */
    @Test public void tinyHeaderNeverBecomesInventedEvidence() throws Exception {JSONObject f=fixture("empty");assertEquals(0,f.getJSONArray("inputs").length());assertEquals(0,f.getJSONArray("owner_annotations").length());}
}
