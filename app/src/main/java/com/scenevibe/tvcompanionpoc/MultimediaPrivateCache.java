package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.installation.InstallRequest;
import com.scenevibe.tvcompanionpoc.installation.InstallationSnapshot;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONObject;

/**
 * Cache privé transactionnel : un répertoire immuable contient profil et MP3,
 * puis InstallationStore publie atomiquement un seul descripteur. Les ressources
 * ne sont jamais ajoutées aux artifacts Video/Banner. L'ancien ensemble reste
 * lisible si téléchargement, validation, commit ou reçu échouent. Restaurer ces
 * données ne restaure aucun permis MEDIA et ne lance aucun renderer.
 */
final class MultimediaPrivateCache {
    private static final Map<String,Object> MONITORS=new HashMap<>();
    private final File root;private final InstallationStore store;private final StudioMultimediaPackage.Decoder decoder;private final Object monitor;

    /** Inert, fully verified runtime input; no activate/render/ACK method is exposed. */
    static final class Prepared {
        final StudioMultimediaPackage track;final MultimediaCloudClient.Binding binding;final MultimediaDeliveryProfile profile;
        private final File directory;
        /** Retain only an immutable package and its validated private directory. */
        Prepared(StudioMultimediaPackage track,MultimediaCloudClient.Binding binding,MultimediaDeliveryProfile profile,File directory) {
            this.track=track;this.binding=binding;this.profile=profile;this.directory=directory;
        }
        /** Resolve an existing logical MP3 through a content-addressed file and recheck its actual bytes. */
        File resource(String id) throws Exception {
            MultimediaDeliveryProfile.Resource row=null;for(MultimediaDeliveryProfile.Resource value:profile.resources)if(value.id.equals(id))row=value;
            MultimediaDeliveryProfile.require(row!=null,"RESOURCE_NOT_FOUND");File file=new File(directory,row.sha256+".mp3");
            byte[] data=readFile(file,MultimediaDeliveryProfile.MAX_AUDIO);
            MultimediaDeliveryProfile.require(data.length==row.byteLength && MultimediaDeliveryProfile.hash(data).equals(row.sha256),"PRIVATE_MP3_INTEGRITY_REFUSED");return file;
        }
    }

    /** Bind an application-private root and a dedicated existing InstallationStore backend. */
    MultimediaPrivateCache(File privateRoot,InstallationStore store,StudioMultimediaPackage.Decoder decoder) throws Exception {
        if(privateRoot==null || store==null || decoder==null)throw new IllegalArgumentException("PRIVATE_CACHE_PORTS_REQUIRED");
        if(!privateRoot.isDirectory() && !privateRoot.mkdirs())throw new IllegalArgumentException("PRIVATE_CACHE_UNAVAILABLE");
        MultimediaDeliveryProfile.require(!Files.isSymbolicLink(privateRoot.toPath()),"PRIVATE_CACHE_SYMLINK_REFUSED");
        this.root=privateRoot.getCanonicalFile();this.store=store;this.decoder=decoder;
        synchronized(MONITORS){monitor=MONITORS.computeIfAbsent(root.toString(),key -> new Object());}
        InstallationStore.ReadResult before=store.read();
        MultimediaDeliveryProfile.require(before.state()!=InstallationStore.ReadState.CORRUPT && before.acknowledgedRevision()==0
                && (before.snapshot()==null || MultimediaDeliveryProfile.CODEC.equals(before.snapshot().codecId())),"DEDICATED_MULTIMEDIA_STORE_REQUIRED");
    }

    /** Download the whole set, publish one atomic pointer, read back, then emit only a cache receipt. */
    Prepared download(MultimediaCloudClient client,MultimediaCloudClient.Binding binding) throws Exception {
        synchronized(monitor) {
            client.check();InstallationStore.ReadResult previous=store.read();
            MultimediaDeliveryProfile.require(previous.state()!=InstallationStore.ReadState.CORRUPT,"PRIVATE_CACHE_CORRUPT");
            if(previous.snapshot()!=null) {
                MultimediaDeliveryProfile.require(binding.revision>=previous.snapshot().revision(),"STALE_ASSIGNMENT");
                if(binding.revision==previous.snapshot().revision()) {
                    Prepared existing=read();MultimediaDeliveryProfile.require(existing!=null && binding.same(existing.binding),"IMMUTABLE_CACHE_CONFLICT");
                    client.verify(binding);client.receipt(binding);client.check();return existing;
                }
            }
            File stage=new File(root,"stage-"+UUID.randomUUID().toString().replace("-",""));
            File committed=new File(root,"set-"+UUID.randomUUID().toString().replace("-",""));boolean pointed=false,success=false;
            MultimediaDeliveryProfile.require(stage.mkdir(),"STAGING_UNAVAILABLE");
            try {
                MultimediaDeliveryProfile profile=client.profile(binding);Map<String,byte[]> physical=new LinkedHashMap<>();
                for(MultimediaDeliveryProfile.Resource row:profile.resources) {
                    client.check();byte[] bytes=physical.get(row.sha256);
                    if(bytes==null) {bytes=client.resource(binding,row);physical.put(row.sha256,bytes);writeFile(new File(stage,row.sha256+".mp3"),bytes);}
                    MultimediaDeliveryProfile.require(bytes.length==row.byteLength && MultimediaDeliveryProfile.hash(bytes).equals(row.sha256),"MP3_INTEGRITY");
                }
                profile.prepare(physical,decoder);writeFile(new File(stage,"profile.json"),profile.encoded);
                writeFile(new File(stage,"binding.json"),binding.json().toString().getBytes(StandardCharsets.UTF_8));
                client.verify(binding);client.check();
                Files.move(stage.toPath(),committed.toPath(),StandardCopyOption.ATOMIC_MOVE);
                JSONObject descriptor=new JSONObject().put("format","scenevibe.multimedia-cache.v1").put("directory",committed.getName())
                        .put("packageSha256",binding.sha256).put("binding",binding.json());
                InstallRequest request=new InstallRequest(binding.revision,MultimediaDeliveryProfile.CODEC,
                        Collections.singletonMap("cache",descriptor.toString().getBytes(StandardCharsets.UTF_8)));
                InstallationSnapshot snapshot=new InstallationSnapshot(request,MultimediaDeliveryProfile.CODEC);
                MultimediaDeliveryProfile.require(store.commit(snapshot)==InstallationStore.CommitState.COMMITTED,"CACHE_COMMIT_FAILED");pointed=true;
                Prepared readback=read();MultimediaDeliveryProfile.require(readback!=null && binding.same(readback.binding),"CACHE_READBACK_FAILED");
                client.verify(binding);client.receipt(binding);client.check();success=true;return readback;
            } finally {
                if(!success && pointed) {
                    boolean restored=previous.snapshot()==null?store.clearAll():store.commit(previous.snapshot())==InstallationStore.CommitState.COMMITTED;
                    MultimediaDeliveryProfile.require(restored,"CACHE_ROLLBACK_FAILED");pointed=false;
                }
                removeTree(stage);
                if(!success && !pointed)removeTree(committed);
            }
        }
    }

    /** Restore only verified data, never a runtime activation, permission, network request or ACK. */
    Prepared read() throws Exception {
        synchronized(monitor) {
            InstallationStore.ReadResult read=store.read();
            MultimediaDeliveryProfile.require(read.state()!=InstallationStore.ReadState.CORRUPT && read.acknowledgedRevision()==0,"PRIVATE_CACHE_CORRUPT");
            if(read.state()==InstallationStore.ReadState.EMPTY)return null;
            InstallationSnapshot snapshot=read.snapshot();
            MultimediaDeliveryProfile.require(MultimediaDeliveryProfile.CODEC.equals(snapshot.codecId()) && MultimediaDeliveryProfile.CODEC.equals(snapshot.handlerId())
                    && snapshot.canonical().artifactCount()==1,"CACHE_DESCRIPTOR_REFUSED");
            JSONObject descriptor=new JSONObject(TvFirst008Wire.utf8(snapshot.canonical().artifact("cache")));
            MultimediaDeliveryProfile.closed(descriptor,"format","directory","packageSha256","binding");
            String name=descriptor.getString("directory");MultimediaDeliveryProfile.require("scenevibe.multimedia-cache.v1".equals(descriptor.get("format")) && name.matches("set-[a-f0-9]{32}"),"CACHE_DIRECTORY_REFUSED");
            File directory=new File(root,name);MultimediaDeliveryProfile.require(!Files.isSymbolicLink(directory.toPath()) && directory.isDirectory(),"CACHE_DIRECTORY_REFUSED");
            MultimediaCloudClient.Binding binding=new MultimediaCloudClient.Binding(descriptor.getJSONObject("binding"));
            MultimediaDeliveryProfile.require(binding.revision==snapshot.revision() && binding.sha256.equals(descriptor.get("packageSha256")),"CACHE_BINDING_MISMATCH");
            MultimediaCloudClient.Binding persisted=new MultimediaCloudClient.Binding(new JSONObject(TvFirst008Wire.utf8(readFile(new File(directory,"binding.json"),16_384))));
            MultimediaDeliveryProfile.require(binding.same(persisted),"CACHE_BINDING_MISMATCH");
            MultimediaDeliveryProfile profile=new MultimediaDeliveryProfile(readFile(new File(directory,"profile.json"),MultimediaDeliveryProfile.MAX_PROFILE));
            MultimediaDeliveryProfile.require(profile.packageSha256.equals(binding.sha256),"CACHE_PACKAGE_INTEGRITY");Map<String,byte[]> physical=new LinkedHashMap<>();
            for(MultimediaDeliveryProfile.Resource row:profile.resources)if(!physical.containsKey(row.sha256))physical.put(row.sha256,readFile(new File(directory,row.sha256+".mp3"),MultimediaDeliveryProfile.MAX_AUDIO));
            StudioMultimediaPackage track=profile.prepare(physical,decoder);return new Prepared(track,binding,profile,directory);
        }
    }

    /** Flush a new immutable file; no existing committed content is ever overwritten. */
    private static void writeFile(File file,byte[] bytes) throws Exception {
        MultimediaDeliveryProfile.require(!file.exists(),"IMMUTABLE_CACHE_FILE_EXISTS");
        try(FileOutputStream stream=new FileOutputStream(file)){stream.write(bytes);stream.getFD().sync();}
    }
    /** Check symlink/type/size before allocating memory for a private cache object. */
    private static byte[] readFile(File file,int maximum) throws Exception {
        MultimediaDeliveryProfile.require(!Files.isSymbolicLink(file.toPath()) && Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS)
                && file.length()>0 && file.length()<=maximum,"PRIVATE_CACHE_FILE_REFUSED");
        byte[] bytes=Files.readAllBytes(file.toPath());MultimediaDeliveryProfile.require(bytes.length<=maximum,"PRIVATE_CACHE_FILE_REFUSED");return bytes;
    }
    /** Remove only the adapter-owned new directory, without following a symlink into another tree. */
    private static void removeTree(File directory) throws Exception {
        if(!directory.exists())return;
        if(!Files.isSymbolicLink(directory.toPath()) && directory.isDirectory()) {
            File[] children=directory.listFiles();if(children==null)throw new IllegalArgumentException("CACHE_CLEANUP_FAILED");
            for(File child:children)removeTree(child);
        }
        Files.deleteIfExists(directory.toPath());
    }
}
