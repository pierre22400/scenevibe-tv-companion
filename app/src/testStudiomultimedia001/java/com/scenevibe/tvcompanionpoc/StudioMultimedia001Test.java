package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.audio.LocalAudioPort;
import com.scenevibe.tvcompanionpoc.calendar.MediaObservation;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * JVM integration of the exact ZIP exported through Studio's UI. FFmpeg really
 * decodes each MP3; the player/drawing ports are explicitly recording fakes.
 * This suite proves software dispatch and policy, never native/Sony audibility.
 */
public final class StudioMultimedia001Test {
    private byte[] zip;
    private StudioMultimediaPackage track;
    private RecordingPort port;
    private Drawing drawing;
    private StudioMultimediaRuntime runtime;

    /** Load mandatory private bytes and compose the actual scheduler/renderers. */
    @Before public void prepare() throws Exception {
        String path=System.getProperty("scenevibe.multimedia.package");
        assertNotNull("Studio UI export path must be supplied",path);
        zip=Files.readAllBytes(Path.of(path));
        track=StudioMultimediaPackage.read(zip,StudioMultimedia001Test::decode);
        port=new RecordingPort(); drawing=new Drawing(); runtime=new StudioMultimediaRuntime(drawing,port);
        runtime.loadForSoftwareQualification(track,"current");
    }

    /** Decode the original MP3 into actual PCM frames, keeping container padding separate. */
    private static long decode(byte[] bytes) throws Exception {
        Path directory=Files.createTempDirectory("studio-tv-decode-"), input=directory.resolve("input.mp3"), errors=directory.resolve("decode.log");
        try {
            Files.write(input,bytes);
            Process process=new ProcessBuilder("ffmpeg","-v","error","-i",input.toString(),"-f","s16le","-ac","1","-ar","44100","pipe:1").redirectError(errors.toFile()).start();
            byte[] pcm=process.getInputStream().readAllBytes();
            if(process.waitFor()!=0 || pcm.length==0) throw new IllegalArgumentException("MP3_DECODE_FAILED");
            return Math.round(pcm.length/2d/44100d*1000d);
        } finally { Files.deleteIfExists(input);Files.deleteIfExists(errors);Files.deleteIfExists(directory); }
    }

    /** Forward one controlled selected observation to the software qualification entry. */
    private void observe(long position,boolean playing) { runtime.observe("current",new MediaObservation(true,position,playing)); }

    /** Assert all three real MP3 resources were preserved in one nineteen-event calendar. */
    @Test public void loadsActualStudioPackageAndRealMp3() throws Exception {
        assertEquals(19,track.calendar.events().size());assertEquals(16,track.originalManifest.scenes.size());assertEquals(3,track.audio.size());
        for(var event:track.audio.values()) { assertEquals(3000,event.durationMs()); assertEquals(3000,decode(track.resource(event.assetRef()))); }
        byte[] resource=track.resource(track.audio.values().iterator().next().assetRef()); resource[0]^=1;
        assertFalse(java.util.Arrays.equals(resource,track.resource(track.audio.values().iterator().next().assetRef())));
    }

    /** Advance the one calendar through all three tone starts without a second scheduler. */
    @Test public void dispatchesThreeIndependentTonesAndOriginalTexts() {
        for(long position=0;position<=1_716_000;position+=500) observe(position,true);
        assertEquals(3,port.starts);assertEquals(3,port.preparedIds.size());assertEquals(16,drawing.shown.size());
        assertEquals(LocalAudioPort.State.IDLE,port.state());
    }

    /** A first eligible observation inside AUDIO starts at the native editorial offset. */
    @Test public void startsAtOffsetInsideFirstWindow() {
        observe(91_000,true);assertEquals(1,port.starts);assertEquals(1000,port.offset);
    }

    /** Pause retains the local player and resume does not prepare/restart a new event. */
    @Test public void freezeAndResumeRetainsCurrentMp3() {
        observe(90_000,true);observe(90_500,true);observe(90_500,false);
        assertEquals(1,port.pauses);assertEquals(LocalAudioPort.State.PAUSED,port.state());
        observe(90_500,true);assertEquals(1,port.resumes);assertEquals(1,port.starts);assertEquals(1,port.preparedIds.size());
    }

    /** Backward seek before the cue rearms it while keeping its original identity. */
    @Test public void backwardSeekBeforeCueRearmsIt() {
        observe(90_000,true);observe(91_000,true);observe(88_000,true);observe(89_000,true);observe(90_000,true);
        assertEquals(2,port.starts);assertEquals(port.preparedIds.get(0),port.preparedIds.get(1));
    }

    /** Even a sub-threshold backward AUDIO seek applies the new native offset. */
    @Test public void backwardSeekInsideCueUsesExactNativeOffset() {
        observe(90_000,true);observe(91_200,true);observe(90_700,true);
        assertEquals(1,port.starts);assertEquals(700,port.offset);
    }

    /** Forward seek beyond the end stops immediately and cannot play an expired tone. */
    @Test public void forwardSeekPastCueStopsIt() {
        observe(90_000,true);observe(120_000,true);
        assertEquals(LocalAudioPort.State.IDLE,port.state());assertEquals(1,port.starts);
    }

    /** A seek landing inside a different AUDIO is admitted by the same calendar. */
    @Test public void forwardSeekIntoAudioUsesOffsetWithoutLateTextReplay() {
        observe(0,true);observe(301_500,true);
        assertEquals(1,port.starts);assertEquals(1500,port.offset);assertEquals(0,drawing.shown.size());
    }

    /** Backward seek into a previously expired AUDIO reopens just that audio window. */
    @Test public void seekBackInsideAnExpiredCueReplaysItsRemainingPart() {
        observe(90_000,true);observe(94_000,true);observe(91_000,true);
        assertEquals(2,port.starts);assertEquals(1000,port.offset);
    }

    /** AUDIO OFF never disables the existing TEXT regie. */
    @Test public void audioOffStopsImmediatelyAndTextContinues() {
        observe(90_000,true);runtime.setAudioEnabled(false);
        assertEquals(LocalAudioPort.State.IDLE,port.state());
        for(long p=91_000;p<=107_000;p+=500) observe(p,true);
        assertTrue(drawing.shown.size()>0);assertEquals(1,port.starts);
    }

    /** TEXT OFF leaves the AUDIO renderer eligible. */
    @Test public void textOffDoesNotStopAudio() {
        runtime.setTextEnabled(false);observe(90_000,true);
        assertEquals(1,port.starts);assertEquals(0,drawing.shown.size());
    }

    /** Unknown position and identity loss both close any audible local resource. */
    @Test public void unavailablePositionAndIdentityStopAll() {
        observe(90_000,true);runtime.observe("current",new MediaObservation(true,-1,true));
        assertEquals(LocalAudioPort.State.IDLE,port.state());
        observe(90_000,true);runtime.observe("current",new MediaObservation(false,90_000,true));
        assertEquals(LocalAudioPort.State.IDLE,port.state());
    }

    /** A stale activation cannot start or interrupt a current event. */
    @Test public void oldActivationAndEpisodeReplacementCannotLeakAudio() {
        observe(90_000,true);runtime.loadForSoftwareQualification(track,"next");
        assertEquals(LocalAudioPort.State.IDLE,port.state());
        runtime.observe("current",new MediaObservation(true,300_000,true));assertEquals(1,port.starts);
        runtime.observe("next",new MediaObservation(true,300_000,true));assertEquals(2,port.starts);
        runtime.observe("current",new MediaObservation(false,-1,false));assertEquals(LocalAudioPort.State.STARTED,port.state());
        runtime.close();assertEquals(LocalAudioPort.State.IDLE,port.state());
    }

    /** Local gain persists across later comments and never invokes a system volume API. */
    @Test public void sceneVibeGainIsLocalAndPersistsAcrossEvents() {
        runtime.setGain(0.3f);observe(90_000,true);assertEquals(0.3f,port.gain,0.00001f);
        observe(95_000,true);observe(300_000,true);assertEquals(0.3f,port.gain,0.00001f);
    }

    /** The real track's UNKNOWN edition/mapping prevents any native activation claim. */
    @Test public void unknownEditionAndMappingAreRefusedForNativeMedia() {
        assertFalse(track.hasReferencedIdentity());
        try { runtime.requireNativeIdentityEvidence(); fail("MEDIA must be refused"); }
        catch(IllegalStateException refused) { assertEquals("MEDIA_EDITION_OR_MAPPING_UNPROVEN",refused.getMessage()); }
    }

    /** A legacy STOP-only player cannot silently accept the FREEZE_AND_RESUME profile. */
    @Test public void unsupportedNativeOffsetsRefuseWholeProfile() {
        RecordingPort legacy=new RecordingPort() { /** Deliberately deny native offsets. */ @Override public boolean supportsNativeOffsets() { return false; } };
        try { new StudioMultimediaRuntime(new Drawing(),legacy).loadForSoftwareQualification(track,"legacy");fail("profile must be refused"); }
        catch(IllegalArgumentException refused) { assertEquals("NATIVE_OFFSET_PROFILE_UNSUPPORTED",refused.getMessage()); }
    }

    /** Actual corrupt MP3 bytes fail their hash before any decoder/player activation. */
    @Test public void corruptResourceIsRejected() throws Exception {
        Map<String,byte[]> files=entries(zip);String path=files.keySet().stream().filter(key->key.endsWith(".mp3")).findFirst().orElseThrow();
        files.get(path)[100]^=1;refuses(pack(files),"RESOURCE_HASH_OR_SIZE_MISMATCH");
    }

    /** A missing MP3 cannot fall back to the former demo cue. */
    @Test public void missingResourceIsRejected() throws Exception {
        Map<String,byte[]> files=entries(zip);files.remove(files.keySet().stream().filter(key->key.endsWith(".mp3")).findFirst().orElseThrow());
        refuses(pack(files),"UNEXPECTED_OR_MISSING_PACKAGE_FILE");
    }

    /** Policy names are validated even when document hashes were recomputed. */
    @Test public void stopOnPausePolicyCannotReplaceFreezeAndResume() throws Exception {
        refuses(changeTrack(t->t.getJSONObject("synchronization").put("pause","STOP_ON_PAUSE")),"PLAYBACK_POLICY_UNSUPPORTED");
    }

    /** Unsupported types fail closed without keeping a TEXT subset. */
    @Test public void unknownEventTypeIsRejected() throws Exception {
        refuses(changeTrack(t->t.getJSONArray("events").getJSONObject(0).put("type","IMAGE")),"EVENT_TYPE_UNSUPPORTED");
    }

    /** Canonical millisecond values are never truncated from fractional JSON numbers. */
    @Test public void fractionalTimecodeIsRejected() throws Exception {
        refuses(changeTrack(t->t.getJSONArray("events").getJSONObject(0).put("startMs",32988.5)),"EXACT_INTEGER_REQUIRED");
    }

    /** A rewritten original text cannot be hidden behind a valid package hash. */
    @Test public void changedOriginalTextIsRejected() throws Exception {
        refuses(changeTrack(t->t.getJSONArray("events").getJSONObject(0).getJSONObject("content").put("text","altered")),"ORIGINAL_TEXT_CHANGED");
    }

    /** Revision binding participates in package identity. */
    @Test public void mismatchedRevisionIsRejected() throws Exception {
        refuses(changeTrack(t->t.put("revision",999)),"REVISION_MISMATCH");
    }

    /** A different valid document cannot replace an already observed immutable revision. */
    @Test public void sameRevisionWithDifferentBytesCannotReplaceCurrentTrack() throws Exception {
        byte[] altered=changeTrack(t -> {
            for(int i=0;i<t.getJSONArray("events").length();i++) {
                JSONObject event=t.getJSONArray("events").getJSONObject(i);
                if("AUDIO".equals(event.getString("type"))) { event.put("startMs",91_000);break; }
            }
        });
        StudioMultimediaPackage candidate=StudioMultimediaPackage.read(altered,StudioMultimedia001Test::decode);
        observe(90_000,true);
        try { runtime.loadForSoftwareQualification(candidate,"candidate");fail("immutable revision must refuse replacement"); }
        catch(IllegalArgumentException refused) { assertEquals("REVISION_CONTENT_CONFLICT",refused.getMessage()); }
        assertEquals(LocalAudioPort.State.STARTED,port.state());
    }

    /** An old activation label cannot be reused to admit queued callbacks into a new lifetime. */
    @Test public void activationTokensCannotBeReused() {
        try { runtime.loadForSoftwareQualification(track,"current");fail("activation must be unique"); }
        catch(IllegalArgumentException refused) { assertEquals("ACTIVATION_TOKEN_REUSED",refused.getMessage()); }
    }

    /** The replacement exported from Studio r3 loads without changing scheduler/player code. */
    @Test public void replacementRevisionLoadsWithNewNativeDuration() throws Exception {
        String replacement=System.getProperty("scenevibe.multimedia.replacement");assertNotNull(replacement);
        StudioMultimediaPackage next=StudioMultimediaPackage.read(Files.readAllBytes(Path.of(replacement)),StudioMultimedia001Test::decode);
        assertEquals(3,next.revision);assertEquals(3,next.audio.size());
        assertTrue(next.audio.values().stream().anyMatch(event->event.durationMs()==4000));
        assertNotEquals(track.trackSha256,next.trackSha256);
    }

    /** Assert a bounded rejection from the actual package parser. */
    private static void refuses(byte[] bytes,String code) throws Exception {
        try { StudioMultimediaPackage.read(bytes,StudioMultimedia001Test::decode);fail("Package should be refused: "+code); }
        catch(IllegalArgumentException refused) { assertEquals(code,refused.getMessage()); }
    }

    /** Recompute the outer hash so negative tests exercise semantic validation. */
    private byte[] changeTrack(TrackMutation change) throws Exception {
        Map<String,byte[]> files=entries(zip);JSONObject track=new JSONObject(new String(files.get("track.json"),StandardCharsets.UTF_8));
        change.accept(track);byte[] changed=track.toString().getBytes(StandardCharsets.UTF_8);files.put("track.json",changed);
        JSONObject manifest=new JSONObject(new String(files.get("manifest.json"),StandardCharsets.UTF_8));manifest.getJSONObject("track").put("sha256",StudioMultimediaPackage.sha256(changed));
        files.put("manifest.json",manifest.toString().getBytes(StandardCharsets.UTF_8));return pack(files);
    }

    /** Android's JSON API declares checked exceptions; preserve the same test mutations under Gradle and plain JVM. */
    private interface TrackMutation {
        /** Change only the test-owned detached document and propagate JSON contract errors. */
        void accept(JSONObject document) throws Exception;
    }

    /** Read test ZIP entries without modifying the exported artifact. */
    private static Map<String,byte[]> entries(byte[] bytes) throws Exception {
        Map<String,byte[]> result=new LinkedHashMap<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)) { ZipEntry entry;while((entry=zip.getNextEntry())!=null) result.put(entry.getName(),zip.readAllBytes()); }
        return result;
    }

    /** Build a test-only ZIP mutation using the standard JVM writer and valid CRCs. */
    private static byte[] pack(Map<String,byte[]> entries) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)) {
            for(var entry:entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry(); }
        }
        return bytes.toByteArray();
    }

    /** Recording local player: lifecycle/offset assertions, explicitly no audible native output. */
    static class RecordingPort implements LocalAudioPort {
        final List<String> preparedIds=new ArrayList<>();int starts,pauses,resumes;long offset;float gain;State state=State.IDLE;
        /** Record the stable imported resource selected by the renderer. */
        @Override public void prepare(String id) { preparedIds.add(id);state=State.PREPARED;offset=0; }
        /** Record an audible-start request, without simulating physical sound. */
        @Override public void start() { starts++;state=State.STARTED; }
        /** Record immediate local stop. */
        @Override public void stop() { state=State.IDLE; }
        /** Record native-resource release. */
        @Override public void release() { state=State.IDLE; }
        /** Retain local SceneVibe gain. */
        @Override public void setGain(float value) { gain=value; }
        /** Return the test port lifecycle state. */
        @Override public State state() { return state; }
        /** Advertise the offset operations implemented by this recording seam. */
        @Override public boolean supportsNativeOffsets() { return true; }
        /** Record a freeze while retaining the resource. */
        @Override public void pause() { pauses++;state=State.PAUSED; }
        /** Record resume without an extra prepare/start event. */
        @Override public void resume() { resumes++;state=State.STARTED; }
        /** Retain the exact offset supplied by the calendar. */
        @Override public void seekTo(long value) { offset=value; }
    }

    /** Recording SceneSink for the existing M5 regie, not a replacement TEXT scheduler. */
    static final class Drawing implements SceneRuntimeController.SceneSink {
        final List<String> shown=new ArrayList<>();
        /** Accept the already validated original scene. */
        @Override public boolean preflight(OverlayManifest.Scene scene) { return true; }
        /** Record the exact original scene id. */
        @Override public void show(OverlayManifest.Scene scene) { shown.add(scene.id); }
        /** Observe scene expiry without an Android window. */
        @Override public void hide(OverlayManifest.Scene scene) {}
        /** Observe immediate visual teardown. */
        @Override public void hideAll() {}
    }
}
