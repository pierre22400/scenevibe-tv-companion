package com.scenevibe.tvcompanionpoc;

import java.util.Arrays;
import java.util.Collection;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Reject the same frozen wire/identity/manifest structural faults before any installation exists. */
@RunWith(Parameterized.class)
public final class M4PhaseFAdapterRejectionTest {
    private final String fault;
    /** Select one inert wire field fault. */
    public M4PhaseFAdapterRejectionTest(String fault) {this.fault=fault;}
    /** Enumerate independent Cloud v1 checks, including optional-manifest ambiguity and unsupported wall wire. */
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> faults() {
        return Arrays.asList(new Object[][]{{"type"},{"device"},{"revision-string"},{"revision-zero"},{"revision-negative"},
                {"finalTrackId"},{"trackId"},{"runtime"},{"runtime-type"},{"runtime-trackId"},{"target"},{"media"},
                {"platform"},{"videoId"},{"title"},{"duration"},{"manifest-null"},{"manifest-invalid"},{"source"},{"wall"}});
    }
    /** The adapter delegates wire truth rather than duplicating acceptance or guessing an artifact profile. */
    @Test public void wireFaultIsRejectedByBothExistingProtocolAndAdapter() throws Exception {
        JSONObject envelope=M4PhaseFFixtures.envelope(true,13),runtime=envelope.getJSONObject("runtimeTrack"),media=runtime.getJSONObject("mediaIdentity");
        String device=envelope.getString("deviceId");
        switch(fault) {
            case "type":envelope.put("type","other");break;
            case "device":envelope.put("deviceId","other");break;
            case "revision-string":envelope.put("revision","13");break;
            case "revision-zero":envelope.put("revision",0);break;
            case "revision-negative":envelope.put("revision",-1);break;
            case "finalTrackId":envelope.put("finalTrackId","");break;
            case "trackId":envelope.put("trackId","");break;
            case "runtime":envelope.remove("runtimeTrack");break;
            case "runtime-type":runtime.put("type","other");break;
            case "runtime-trackId":runtime.put("trackId","other");break;
            case "target":runtime.put("targetPackage","other");break;
            case "media":runtime.remove("mediaIdentity");break;
            case "platform":media.put("platform","other");break;
            case "videoId":media.put("videoId","");break;
            case "title":media.put("title","");break;
            case "duration":media.put("durationMs",0);break;
            case "manifest-null":envelope.put("overlayManifest",JSONObject.NULL);break;
            case "manifest-invalid":envelope.put("overlayManifest",new JSONObject());break;
            case "source":envelope.getJSONObject("overlayManifest").getJSONObject("source").put("sourceId","other");break;
            case "wall":envelope.getJSONObject("overlayManifest").getJSONObject("clock").put("mode","wall");break;
            default:throw new AssertionError("Unknown fixture fault");
        }
        assertFalse(CloudProtocol.validAssignment(envelope,device,0));
        try {CloudV1InstallationAdapter.adapt(envelope,device);fail("wire fault accepted");}
        catch(IllegalArgumentException expected) {assertNull(expected.getCause());}
    }
}
