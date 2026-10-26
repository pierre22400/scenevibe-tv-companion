package com.scenevibe.tvcompanionpoc;

import com.scenevibe.tvcompanionpoc.audio.AndroidLocalAudioPort;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Private, immutable resource materialization for the experimental Android port.
 * Only content-addressed MP3 paths from a validated Studio package can resolve.
 * Each playback rechecks the actual bytes, preventing a stale/corrupt cache from
 * becoming a fallback demo sound. No download or untrusted path is accepted.
 */
final class StudioMultimediaFiles implements AndroidLocalAudioPort.Resources {
    private final Map<String,File> files;
    private final Map<String,String> hashes;

    /** Materialize validated bytes atomically in a caller-supplied application-private directory. */
    StudioMultimediaFiles(File privateRoot,StudioMultimediaPackage track) throws Exception {
        File directory=new File(privateRoot,track.trackSha256);
        if(!directory.isDirectory() && !directory.mkdirs()) throw new IOException("PRIVATE_CACHE_UNAVAILABLE");
        Map<String,File> paths=new HashMap<>(); Map<String,String> digests=new HashMap<>();
        for(com.scenevibe.tvcompanionpoc.audio.AudioEvent event:track.audio.values()) {
            String id=event.assetRef(); if(paths.containsKey(id)) continue;
            byte[] bytes=track.resource(id); String hash=StudioMultimediaPackage.sha256(bytes);
            File target=new File(directory,hash+".mp3");
            if(!target.isFile() || !hash.equals(StudioMultimediaPackage.sha256(Files.readAllBytes(target.toPath())))) {
                File temporary=File.createTempFile("mp3-",".partial",directory);
                try {
                    Files.write(temporary.toPath(),bytes);
                    Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
                } finally { Files.deleteIfExists(temporary.toPath()); }
            }
            paths.put(id,target); digests.put(id,hash);
        }
        files=Collections.unmodifiableMap(paths); hashes=Collections.unmodifiableMap(digests);
    }

    /** Resolve a stable id only after revalidating the private cached bytes. */
    @Override public File resolve(String id) throws IOException {
        File file=files.get(id);
        try {
            if(file==null || !file.isFile() || file.length()>50L*1024*1024
                    || !hashes.get(id).equals(StudioMultimediaPackage.sha256(Files.readAllBytes(file.toPath())))) throw new IOException("PRIVATE_MP3_INTEGRITY_REFUSED");
            return file;
        } catch(IOException failure) { throw failure; }
        catch(Exception failure) { throw new IOException("PRIVATE_MP3_INTEGRITY_REFUSED"); }
    }
}
