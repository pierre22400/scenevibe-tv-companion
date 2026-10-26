package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import com.scenevibe.tvcompanionpoc.audio.LocalAudioPort;
import com.scenevibe.tvcompanionpoc.calendar.MediaObservation;
import java.io.File;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Vrai trajet Node API 003-D → HTTP loopback → consommateur Java → fichiers
 * privés → InstallationStore → paquet/calendrier existants. Les repositories
 * serveur sont mémoire ; disque, HTTP, SHA-256 et décodage FFmpeg sont réels.
 * Les ports de restitution enregistrent les appels et n'autorisent aucune TV.
 */
public final class MultimediaDeliveryIntegratedTest {
    private JSONObject config;private Path directory;private DiskBackend backend;private MultimediaPrivateCache cache;
    private MultimediaCloudClient client;private Lease lease;private Loopback port;

    /** Read ephemeral synthetic credentials and create a fresh real filesystem cache. */
    @Before public void before() throws Exception {
        config=new JSONObject(Files.readString(Path.of(System.getProperty("scenevibe003d.config"))));
        directory=Files.createTempDirectory("scenevibe003d-cache-");backend=new DiskBackend(directory.resolve("metadata.properties"));
        lease=new Lease(config.getJSONObject("tv"));port=new Loopback(config.getString("origin"));client=new MultimediaCloudClient(lease,port);
        control("restore",null);cache=cache(backend);
    }
    /** Restore only fixture authority and remove this test's disposable private directory. */
    @After public void after() throws Exception {
        if(config!=null)control("restore",null);
        if(directory!=null)try(var files=Files.walk(directory)){for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
    }
    /** Recreate the production cache class over a real disk-backed generic store. */
    private MultimediaPrivateCache cache(DiskBackend value) throws Exception {
        return new MultimediaPrivateCache(directory.resolve("cache").toFile(),new InstallationStore(value),MultimediaDeliveryIntegratedTest::decode);
    }
    /** Decode actual MP3 frames to PCM; the Android MediaCodec adapter is not simulated as qualified. */
    private static long decode(byte[] bytes) throws Exception {
        Path root=Files.createTempDirectory("scenevibe003d-decode-"),input=root.resolve("audio.mp3"),errors=root.resolve("errors.log");
        try {
            Files.write(input,bytes);Process process=new ProcessBuilder("ffmpeg","-v","error","-i",input.toString(),"-f","s16le","-ac","1","-ar","44100","pipe:1").redirectError(errors.toFile()).start();
            byte[] pcm=process.getInputStream().readAllBytes();if(process.waitFor()!=0||pcm.length==0)throw new IllegalArgumentException("MP3_DECODE_FAILED");
            return Math.round(pcm.length/2d/44100d*1000d);
        } finally {Files.deleteIfExists(input);Files.deleteIfExists(errors);Files.deleteIfExists(root);}
    }
    /** Build one explicit persisted-intent equivalent using the actual server OS revision. */
    private JSONObject command() throws Exception {
        return new JSONObject().put("profile",MultimediaDeliveryProfile.PROFILE).put("generation",lease.generation())
                .put("intentionId",UUID.randomUUID().toString()).put("publicationId",config.getString("publicationId"))
                .put("expectedRevision",client.currentRevision()).put("confirmed",true);
    }
    /** Select through the genuine authenticated HTTP intention endpoint. */
    private MultimediaCloudClient.Binding select() throws Exception {return client.select(command());}
    /** Run a complete real download and return its verified inert runtime input. */
    private MultimediaPrivateCache.Prepared download() throws Exception {return cache.download(client,select());}
    /** Address the test-only loopback control surface, absent from all shipped source paths. */
    private JSONObject control(String action,String mode) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)URI.create(config.getString("origin")+"/__control").toURL().openConnection();
        try {
            connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setReadTimeout(10_000);
            JSONObject body=new JSONObject().put("action",action);if(mode!=null)body.put("mode",mode);
            try(var stream=connection.getOutputStream()){stream.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            assertEquals(200,connection.getResponseCode());return new JSONObject(new String(connection.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
        } finally {connection.disconnect();}
    }
    /** Assert a failed replacement leaves exactly the previous coherent pointer and content. */
    private void refusesReplacement(String mode) throws Exception {
        MultimediaPrivateCache.Prepared previous=download();byte[] pointer=Files.readAllBytes(backend.path);
        MultimediaCloudClient.Binding next=select();control("mode",mode);boolean refused=false;
        try {cache.download(client,next);}catch(Exception expected){refused=true;}finally{control("restore",null);}
        assertTrue("fault must refuse: "+mode,refused);assertArrayEquals(pointer,Files.readAllBytes(backend.path));assertTrue(previous.binding.same(cache.read().binding));
    }
    /** Assert full editorial identity and all distinct logical resources after transport. */
    private void assertTrack(MultimediaPrivateCache.Prepared prepared) throws Exception {
        assertEquals(19,prepared.track.calendar.events().size());assertEquals(16,prepared.track.originalManifest.scenes.size());assertEquals(3,prepared.track.audio.size());
        assertEquals(config.getString("snapshotSha256"),prepared.track.trackSha256);
        Set<Long> times=new java.util.HashSet<>();Set<String> ids=new java.util.HashSet<>();
        for(var event:prepared.track.audio.values()){times.add(event.startMs());ids.add(event.eventId());assertEquals(3000,event.durationMs());assertEquals(3000,decode(prepared.track.resource(event.assetRef())));}
        assertEquals(Set.of(90_000L,300_000L,900_000L),times);assertEquals(3,ids.size());
    }

    /** Fetch the authorized listing and consume real API bytes into the shared calendar input. */
    @Test public void transfersRealPrivatePublicationIntoTheExistingCalendar() throws Exception {
        assertTrue(client.publications().toString().contains(config.getString("publicationId")));
        MultimediaPrivateCache.Prepared prepared=download();assertTrack(prepared);assertTrack(cache.read());
        assertEquals(0,new InstallationStore(backend).read().acknowledgedRevision());
    }
    /** Deduplicate the three logical MP3 resources without merging their editorial events. */
    @Test public void threeLogicalResourcesUseOnePhysicalDownloadAndOneFile() throws Exception {
        int before=control("stats",null).getInt("resourceRequests");MultimediaPrivateCache.Prepared prepared=download();
        assertEquals(1,control("stats",null).getInt("resourceRequests")-before);
        try(var files=Files.walk(directory.resolve("cache"))){assertEquals(1,files.filter(path->path.toString().endsWith(".mp3")).count());}
        Set<String> paths=new java.util.HashSet<>();for(var event:prepared.track.audio.values())paths.add(prepared.resource(event.assetRef()).getCanonicalPath());assertEquals(1,paths.size());
    }
    /** A recreated cache/backend restores real files without network, rendering or cached admission. */
    @Test public void restartRestoresTheWholeVerifiedCacheWithoutNetwork() throws Exception {
        MultimediaPrivateCache.Prepared before=download();JSONObject stats=control("stats",null);
        MultimediaPrivateCache.Prepared restored=cache(new DiskBackend(backend.path)).read();assertTrack(restored);assertTrue(before.binding.same(restored.binding));
        assertEquals(stats.toString(),control("stats",null).toString());
    }
    /** Merely offering a verified private cache never activates TEXT, AUDIO or native MEDIA. */
    @Test public void verifiedCacheDoesNotAdmitMedia() throws Exception {
        MultimediaPrivateCache.Prepared prepared=download();var player=new StudioMultimedia001Test.RecordingPort();var drawing=new StudioMultimedia001Test.Drawing();
        StudioMultimediaRuntime runtime=new StudioMultimediaRuntime(drawing,player);runtime.prepareFromVerifiedCloudCache(prepared);
        assertEquals(19,runtime.preparedCloudCalendar().events().size());runtime.observe("unadmitted",new MediaObservation(true,90_000,true));
        assertEquals(0,player.starts);assertTrue(drawing.shown.isEmpty());
        try {runtime.requireNativeIdentityEvidence();fail("native authority missing");}catch(IllegalStateException expected){assertEquals("MEDIA_EDITION_OR_MAPPING_UNPROVEN",expected.getMessage());}
    }
    /** Explicit test-only admission exercises the original calendar and both existing renderers. */
    @Test public void transportedTrackDispatchesExactlySixteenTextsAndThreeAudioEvents() throws Exception {
        var prepared=download();var player=new StudioMultimedia001Test.RecordingPort();var drawing=new StudioMultimedia001Test.Drawing();
        StudioMultimediaRuntime runtime=new StudioMultimediaRuntime(drawing,player);runtime.loadForSoftwareQualification(prepared.track,"test-explicit");
        for(long at=0;at<=1_716_000;at+=500)runtime.observe("test-explicit",new MediaObservation(true,at,true));
        assertEquals(16,drawing.shown.size());assertEquals(3,player.starts);assertEquals(3,player.preparedIds.size());runtime.close();
    }
    /** TEXT OFF leaves the actual transported audio events eligible in the software-only test. */
    @Test public void textAndAudioControlsRemainIndependent() throws Exception {
        var prepared=download();var player=new StudioMultimedia001Test.RecordingPort();var drawing=new StudioMultimedia001Test.Drawing();
        StudioMultimediaRuntime runtime=new StudioMultimediaRuntime(drawing,player);runtime.loadForSoftwareQualification(prepared.track,"test-explicit");
        runtime.setTextEnabled(false);runtime.observe("test-explicit",new MediaObservation(true,90_000,true));assertEquals(1,player.starts);assertTrue(drawing.shown.isEmpty());
        runtime.setAudioEnabled(false);assertEquals(LocalAudioPort.State.IDLE,player.state());runtime.setTextEnabled(true);
        for(long at=91_000;at<=107_000;at+=500)runtime.observe("test-explicit",new MediaObservation(true,at,true));assertFalse(drawing.shown.isEmpty());assertEquals(1,player.starts);runtime.close();
    }
    /** Preserve pause/resume and both seek directions using only the existing shared scheduler. */
    @Test public void transportedAudioPreservesPauseResumeAndSeek() throws Exception {
        var prepared=download();var player=new StudioMultimedia001Test.RecordingPort();var drawing=new StudioMultimedia001Test.Drawing();
        StudioMultimediaRuntime runtime=new StudioMultimediaRuntime(drawing,player);runtime.loadForSoftwareQualification(prepared.track,"test-explicit");
        runtime.observe("test-explicit",new MediaObservation(true,90_000,true));runtime.observe("test-explicit",new MediaObservation(true,90_500,false));
        assertEquals(1,player.pauses);runtime.observe("test-explicit",new MediaObservation(true,90_500,true));assertEquals(1,player.resumes);
        runtime.observe("test-explicit",new MediaObservation(true,301_500,true));assertEquals(1500,player.offset);
        runtime.observe("test-explicit",new MediaObservation(true,91_000,true));assertEquals(1000,player.offset);runtime.close();
    }
    /** Corrupt transport bytes must not replace an earlier coherent cache. */
    @Test public void corruptMp3PreservesOldCache() throws Exception {refusesReplacement("corrupt");}
    /** An absent logical resource must not substitute the former demonstration cue. */
    @Test public void absentMp3PreservesOldCache() throws Exception {refusesReplacement("missing");}
    /** Truncated network output must fail before the metadata pointer changes. */
    @Test public void truncatedMp3PreservesOldCache() throws Exception {refusesReplacement("truncate");}
    /** A wrong declared audio MIME must be refused before it reaches any decoder. */
    @Test public void wrongMimePreservesOldCache() throws Exception {refusesReplacement("mime");}
    /** A revoked current grant interrupts a stream after the first actual MP3 chunk. */
    @Test public void revocationDuringDownloadPreservesOldCache() throws Exception {refusesReplacement("revoke-midstream");}
    /** A replacement assignment invalidates an already opened resource stream. */
    @Test public void replacementDuringDownloadPreservesOldCache() throws Exception {refusesReplacement("replace-midstream");}
    /** An altered package fails its binding digest before resources can be cached. */
    @Test public void corruptPackagePreservesOldCache() throws Exception {refusesReplacement("corrupt-package");}
    /** Retry the exact same intention explicitly after a real socket interruption. */
    @Test public void explicitRetryAfterNetworkCutCompletesWithoutAnotherAssignment() throws Exception {
        JSONObject command=command();MultimediaCloudClient.Binding binding=client.select(command);control("mode","disconnect");
        boolean refused=false;try {cache.download(client,binding);}catch(Exception expected){refused=true;}assertTrue(refused);assertNull(cache.read());
        control("restore",null);assertTrue(binding.same(client.select(command)));assertTrack(cache.download(client,binding));
    }
    /** An expired generation is refused before package acquisition or cache creation. */
    @Test public void staleGenerationCannotDownload() throws Exception {
        MultimediaCloudClient.Binding binding=select();control("generation",null);boolean refused=false;
        try {cache.download(client,binding);}catch(Exception expected){refused=true;}assertTrue(refused);assertNull(cache.read());
    }
    /** A mismatching credential is never treated as an account principal. */
    @Test public void wrongCredentialCannotReadThePrivateCatalogue() throws Exception {
        lease.token="synthetic-wrong-credential";boolean refused=false;try {client.publications();}catch(Exception expected){refused=true;}assertTrue(refused);assertNull(cache.read());
    }
    /** A cancellation while receiving MP3 bytes keeps the previous pointer unchanged. */
    @Test public void cancellationDuringDownloadPreservesOldCache() throws Exception {
        MultimediaPrivateCache.Prepared old=download();byte[] before=Files.readAllBytes(backend.path);MultimediaCloudClient.Binding next=select();port.cancelOnAudio=true;
        boolean refused=false;try {cache.download(client,next);}catch(Exception expected){refused=true;}assertTrue(refused);assertArrayEquals(before,Files.readAllBytes(backend.path));assertTrue(old.binding.same(cache.read().binding));
    }
    /** The existing backend's failed atomic commit leaves the old complete installation visible. */
    @Test public void commitFailurePreservesOldCache() throws Exception {
        MultimediaPrivateCache.Prepared old=download();byte[] before=Files.readAllBytes(backend.path);MultimediaCloudClient.Binding next=select();backend.failNext=true;
        boolean refused=false;try {cache.download(client,next);}catch(Exception expected){refused=true;}assertTrue(refused);assertArrayEquals(before,Files.readAllBytes(backend.path));assertTrue(old.binding.same(cache.read().binding));
    }
    /** Hashes are checked again after restart rather than trusting persisted metadata alone. */
    @Test public void corruptionOnDiskIsRefusedAfterRestart() throws Exception {
        var prepared=download();File file=prepared.resource(prepared.track.audio.values().iterator().next().assetRef());byte[] bytes=Files.readAllBytes(file.toPath());bytes[100]^=255;Files.write(file.toPath(),bytes);
        boolean refused=false;try {cache(new DiskBackend(backend.path)).read();}catch(Exception expected){refused=true;}assertTrue(refused);
    }
    /** Same-revision redelivery reads the committed set without another MP3 GET or write. */
    @Test public void sameBindingRetryReusesVerifiedCache() throws Exception {
        var prepared=download();JSONObject before=control("stats",null);byte[] metadata=Files.readAllBytes(backend.path);
        var again=cache.download(client,prepared.binding);assertTrue(prepared.binding.same(again.binding));assertArrayEquals(metadata,Files.readAllBytes(backend.path));
        assertEquals(before.toString(),control("stats",null).toString());
    }
    /** A valid credential for another account cannot select the owner's private publication. */
    @Test public void validForeignAccountCannotSelectThePublication() throws Exception {
        Lease stranger=new Lease(config.getJSONObject("foreign"));MultimediaCloudClient other=new MultimediaCloudClient(stranger,port);
        assertEquals(0,other.publications().getJSONArray("items").length());
        JSONObject command=command().put("generation",stranger.generation()).put("expectedRevision",other.currentRevision());
        boolean refused=false;try {other.select(command);}catch(Exception expected){refused=true;}assertTrue(refused);assertNull(cache.read());
    }
    /** Revocation at the post-commit receipt boundary rolls the pointer back to the previous coherent set. */
    @Test public void receiptRevocationRollsBackThePublishedCachePointer() throws Exception {
        var old=download();byte[] pointer=new InstallationStore(backend).read().snapshot().canonical().artifact("cache");
        var next=select();control("mode","receipt-revoke");boolean refused=false;
        try {cache.download(client,next);}catch(Exception expected){refused=true;}finally{control("restore",null);}
        assertTrue(refused);assertArrayEquals(pointer,new InstallationStore(backend).read().snapshot().canonical().artifact("cache"));assertTrue(old.binding.same(cache.read().binding));
    }
    /** An orphan stage after process loss cannot supersede the last committed complete set. */
    @Test public void orphanStagingIsInertAfterRestart() throws Exception {
        var previous=download();Path orphan=directory.resolve("cache/stage-"+"0".repeat(32));Files.createDirectory(orphan);Files.write(orphan.resolve("partial.mp3"),new byte[]{1,2,3});
        var restored=cache(new DiskBackend(backend.path)).read();assertTrue(previous.binding.same(restored.binding));assertTrack(restored);
    }

    /** Synthetic lease models only the already-paired external identity, not the new transport logic. */
    private static final class Lease implements MultimediaCloudClient.Session {
        final String device,generation;String token;boolean live=true;
        /** Retain the ephemeral fixture identity; no real authentication secret is used. */
        Lease(JSONObject data) throws Exception {device=data.getString("deviceId");generation=data.getString("generation");token=data.getString("token");}
        /** Return the paired fixture device. */ @Override public String deviceId(){return device;}
        /** Return its initial generation. */ @Override public String generation(){return generation;}
        /** Supply only the synthetic per-run credential. */ @Override public String token(){return token;}
        /** Model explicit cancellation of the local lifetime. */ @Override public boolean current(){return live;}
    }
    /** Real HTTP test port is confined to loopback and never compiled into the APK. */
    private final class Loopback implements MultimediaCloudClient.Port {
        final String origin;boolean cancelOnAudio;
        /** Refuse any non-loopback local target. */ Loopback(String origin){assertTrue(origin.matches("http://127\\.0\\.0\\.1:[0-9]+"));this.origin=origin;}
        /** Send actual request headers/body and return the actual streaming response. */
        @Override public MultimediaCloudClient.Reply open(String method,String path,String token,byte[] body) throws Exception {
            HttpURLConnection connection=(HttpURLConnection)URI.create(origin+path).toURL().openConnection();
            try {
                connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(8_000);connection.setReadTimeout(8_000);connection.setRequestMethod(method);
                connection.setRequestProperty("Authorization","Bearer "+token);
                if(body!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");try(var output=connection.getOutputStream()){output.write(body);}}
                int status=connection.getResponseCode();InputStream input=status==200?connection.getInputStream():null;
                if(cancelOnAudio && path.contains("/resource?"))input=new FilterInputStream(input){
                    /** Cancel after real bytes arrive, exercising the production consumer's chunk check. */
                    @Override public int read(byte[] target,int offset,int length) throws java.io.IOException {int count=super.read(target,offset,length);if(count>0)lease.live=false;return count;}
                };
                return new MultimediaCloudClient.Reply(status,connection.getContentType(),connection.getContentLengthLong(),input,connection::disconnect);
            } catch(Exception failed){connection.disconnect();throw failed;}
        }
    }
    /** Actual durable local implementation of InstallationStore.Backend; SharedPreferences is not claimed as tested here. */
    private static final class DiskBackend implements InstallationStore.Backend {
        final Path path;boolean failNext;
        /** Reopen the same metadata file after a simulated application restart. */ DiskBackend(Path path){this.path=path;}
        /** Tests use one writer; the production cache adds a per-root lifetime lock. */ @Override public Object monitor(){return this;}
        /** Read persisted bytes rather than a retained test Map. */
        @Override public String get(String key){try {Properties values=read();return values.getProperty(key);}catch(Exception failed){throw new IllegalStateException("DISK_READ_FAILED");}}
        /** Read the actual properties representation without modifying it. */
        private Properties read() throws Exception {Properties values=new Properties();if(Files.exists(path))try(var stream=Files.newInputStream(path)){values.load(stream);}return values;}
        /** Write a temp file, flush it and atomically replace metadata, preserving old bytes on refusal. */
        @Override public boolean commit(Map<String,String> values,Set<String> removed,boolean clear){
            if(failNext){failNext=false;return false;}
            Path temporary=path.resolveSibling("metadata-"+UUID.randomUUID()+".partial");
            try {
                Properties data=clear?new Properties():read();for(String key:removed)data.remove(key);data.putAll(values);
                try(var stream=new java.io.FileOutputStream(temporary.toFile())){data.store(stream,"local qualification only");stream.getFD().sync();}
                Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);return true;
            }catch(Exception failed){return false;}finally{try{Files.deleteIfExists(temporary);}catch(Exception ignored){/* Failed staging never becomes current. */}}
        }
    }
}
