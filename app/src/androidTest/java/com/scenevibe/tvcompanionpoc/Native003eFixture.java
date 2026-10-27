package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import org.json.JSONObject;

/**
 * Fixture native : les octets proviennent du vrai préparateur/publicateur 003-D.
 * Seule la frontière réseau est une fixture déterministe sans socket ; le client,
 * le cache, SharedPreferences et MediaCodec restent les classes Android réelles.
 * Aucun secret fournisseur, permis MEDIA, rendu ou connexion Sony n'est créé.
 */
final class Native003eFixture {
    final Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
    final String generation=UUID.randomUUID().toString();
    final String namespace="multimedia003d_cache_"+generation.replace("-","");
    final File root;
    final JSONObject facts;
    final byte[] profileBytes;
    final MultimediaDeliveryProfile profile;
    MultimediaCloudClient.Binding binding;
    final Lease lease;
    final Port port;
    final MultimediaCloudClient client;
    final InstallationStore store;
    final MultimediaPrivateCache cache;

    /** Keep target-app private files and the actual Android backend, with unique generation isolation. */
    Native003eFixture() throws Exception {
        root=new File(target.getFilesDir(),"cloud003e-"+UUID.randomUUID());
        facts=new JSONObject(new String(asset("facts.json"),StandardCharsets.UTF_8));profileBytes=asset("profile.json");
        profile=new MultimediaDeliveryProfile(profileBytes);
        JSONObject data=new JSONObject(new String(asset("binding.json"),StandardCharsets.UTF_8)).put("generation",generation);
        binding=new MultimediaCloudClient.Binding(data);lease=new Lease(binding);port=new Port(this);client=new MultimediaCloudClient(lease,port);
        store=new InstallationStore(new AndroidInstallationBackend(target,namespace));
        cache=new MultimediaPrivateCache(root,store,new AndroidStudioAudioInspector(target));
    }
    /** Read an exact test-APK asset without any fallback or runtime demonstration catalogue. */
    static byte[] asset(String name) throws Exception {
        try(InputStream stream=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("cloudbridge003e/"+name);
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int count;while((count=stream.read(buffer))!=-1)bytes.write(buffer,0,count);return bytes.toByteArray();
        }
    }
    /** Download through the actual production client and actual Android-backed private cache. */
    MultimediaPrivateCache.Prepared download() throws Exception {return cache.download(client,binding);}
    /** Recreate the Android cache object over the same durable generation and native decoder. */
    MultimediaPrivateCache reopen() throws Exception {
        return new MultimediaPrivateCache(root,new InstallationStore(new AndroidInstallationBackend(target,namespace)),new AndroidStudioAudioInspector(target));
    }
    /** Allocate only test authority coordinates, never an OS or Cloud mutation. */
    void replacement() throws Exception {binding=new MultimediaCloudClient.Binding(binding.json().put("revision",binding.revision+1).put("intentionId",UUID.randomUUID().toString()));}
    /** Remove only this test-owned subtree without following links. */
    void cleanup() throws Exception {remove(root);target.getSharedPreferences(namespace,Context.MODE_PRIVATE).edit().clear().commit();}
    /** Delete only a test-owned tree, refusing traversal through symbolic links. */
    static void remove(File file) throws Exception {
        if(!file.exists())return;
        if(!Files.isSymbolicLink(file.toPath()) && file.isDirectory()) {
            File[] children=file.listFiles();if(children==null)throw new IllegalStateException("TEST_CLEANUP_FAILED");
            for(File child:children)remove(child);
        }
        Files.deleteIfExists(file.toPath());
    }
    /** Synthetic network lease; real TV-FIRST/Keystore lifecycle is qualified separately. */
    static final class Lease implements MultimediaCloudClient.Session {
        final String device,generation;boolean live=true;
        /** Preserve the exact transport coordinates while keeping credentials synthetic. */
        Lease(MultimediaCloudClient.Binding binding){device=binding.device;generation=binding.generation;}
        /** Return fixture device ownership. */ @Override public String deviceId(){return device;}
        /** Return the fixture generation. */ @Override public String generation(){return generation;}
        /** Supply no real credential. */ @Override public String token(){return "synthetic-native-fixture-only";}
        /** Model explicit cancellation at the byte boundary. */ @Override public boolean current(){return live;}
    }
    /** Deterministic response fixture, not a replacement for client/cache/decoder implementations. */
    static final class Port implements MultimediaCloudClient.Port {
        final Native003eFixture fixture;String fault="";int audioRequests,receipts;Runnable onAudio;
        /** Retain only the immutable source bytes and selected test tuple. */ Port(Native003eFixture fixture){this.fixture=fixture;}
        /** Return exact MIME/length and bytes through the production client's real validation path. */
        @Override public MultimediaCloudClient.Reply open(String method,String path,String token,byte[] body) throws Exception {
            String action=path.substring(path.lastIndexOf('/')+1,path.indexOf('?'));byte[] data;String mime="application/json";int status=200;
            switch(action) {
                case "association":data=new JSONObject().put("profile",MultimediaDeliveryProfile.PROFILE).put("deviceId",fixture.binding.device)
                    .put("generation",fixture.binding.generation).put("currentRevision",fixture.binding.revision-1).put("mediaAdmissionAuthorized",false).toString().getBytes(StandardCharsets.UTF_8);break;
                case "intentions":
                    JSONObject command=new JSONObject(new String(body,StandardCharsets.UTF_8));
                    fixture.binding=new MultimediaCloudClient.Binding(fixture.binding.json().put("intentionId",command.getString("intentionId"))
                        .put("publicationId",command.getString("publicationId")).put("generation",command.getString("generation"))
                        .put("revision",command.getLong("expectedRevision")+1));
                    data=fixture.binding.json().toString().getBytes(StandardCharsets.UTF_8);break;
                case "validate":data=fixture.binding.json().toString().getBytes(StandardCharsets.UTF_8);break;
                case "package":data=fixture.profileBytes.clone();mime="application/vnd.scenevibe.multimedia+json";break;
                case "manifest":data=fixture.profile.resourceManifestBytes.clone();break;
                case "resource":
                    audioRequests++;mime="audio/mpeg";data=asset(fixture.profile.resources.get(0).sha256+".mp3");
                    if("corrupt".equals(fault))data[100]^=255;
                    if("missing".equals(fault))status=404;
                    if("mime".equals(fault))mime="image/jpeg";
                    break;
                case "receipt":
                    receipts++;data=new JSONObject().put("binding",fixture.binding.json()).put("status","CACHE_CONFIRMED")
                        .put("mediaAdmissionAuthorized",false).put("installationAcknowledged",false).toString().getBytes(StandardCharsets.UTF_8);break;
                default:throw new IllegalArgumentException("TEST_ROUTE_REFUSED");
            }
            int declared=data.length;if(action.equals("resource") && fault.equals("truncate"))data=java.util.Arrays.copyOf(data,100);
            InputStream input=new ByteArrayInputStream(data);
            if(action.equals("resource") && onAudio!=null)input=new FilterInputStream(input) {
                /** Execute a native lifecycle mutation only after real fixture bytes enter the consumer. */
                @Override public int read(byte[] target,int offset,int length) throws java.io.IOException {
                    int count=super.read(target,offset,length);if(count>0){Runnable action=onAudio;onAudio=null;action.run();}return count;
                }
            };
            return new MultimediaCloudClient.Reply(status,mime,declared,input,()->{});
        }
    }
}
