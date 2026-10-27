package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import com.scenevibe.tvcompanionpoc.installation.AndroidInstallationBackend;
import com.scenevibe.tvcompanionpoc.installation.InstallationStore;
import java.io.File;

/**
 * Façade Companion : réutilise l'association chiffrée TV-FIRST, HTTPS, le cache
 * privé et l'inspecteur MP3 Android existant. Elle rend un paquet inerte au
 * runtime ; aucun Service, Activity, permis MEDIA ou lecteur Prime n'est lancé.
 * L'appel doit appartenir au worker de sélection, jamais au thread d'interface.
 */
final class MultimediaAndroidBridge {
    private final TvFirst008Source source;private final MultimediaCloudClient client;private final MultimediaPrivateCache cache;
    private final MultimediaCloudClient.Session session;
    /** Compose existing Android owners in a dedicated association-generation cache namespace. */
    MultimediaAndroidBridge(Context context,TvFirst008Source source,String origin) throws Exception {
        this(context,source,new MultimediaHttpsPort(origin));
    }
    /** Bind only a qualification network seam while retaining actual Android storage and MediaCodec. */
    MultimediaAndroidBridge(Context context,TvFirst008Source source,MultimediaCloudClient.Port port) throws Exception {
        this.source=source;session=source.multimediaSession();
        client=new MultimediaCloudClient(session,port);
        String generation=TvFirst008Wire.uuid(session.generation()).replace("-","");
        InstallationStore store=new InstallationStore(new AndroidInstallationBackend(context,"multimedia003d_cache_"+generation));
        cache=new MultimediaPrivateCache(new File(context.getFilesDir(),"multimedia003d/"+generation),store,new AndroidStudioAudioInspector(context));
    }
    /** Complete the actual private transfer, then offer the verified calendar without admitting MEDIA. */
    MultimediaPrivateCache.Prepared prepare(String publicationId,StudioMultimediaRuntime runtime) throws Exception {
        MultimediaCloudClient.Binding binding=client.select(source.pendingMultimedia(publicationId,client.currentRevision()));
        MultimediaPrivateCache.Prepared prepared=cache.download(client,binding);
        source.multimediaCached(binding,session,()->runtime.prepareFromVerifiedCloudCache(prepared));return prepared;
    }
    /** Download through the same bridge for the existing TV screen, without constructing a renderer. */
    MultimediaPrivateCache.Prepared fetch(String publicationId) throws Exception {
        MultimediaCloudClient.Binding binding=client.select(source.pendingMultimedia(publicationId,client.currentRevision()));
        MultimediaPrivateCache.Prepared prepared=cache.download(client,binding);
        source.multimediaCached(binding,session,()->{});
        return prepared;
    }
    /** Reopen private data after restart without a credential, HTTP request or playback admission. */
    static MultimediaPrivateCache.Prepared restoreCache(Context context,TvFirst008Source source) throws Exception {
        String generation=source.multimediaCachedGeneration().replace("-","");
        InstallationStore store=new InstallationStore(new AndroidInstallationBackend(context,"multimedia003d_cache_"+generation));
        return new MultimediaPrivateCache(new File(context.getFilesDir(),"multimedia003d/"+generation),store,
                new AndroidStudioAudioInspector(context)).read();
    }
    /** Restore verified inert bytes after restart; no credential, receipt or admission is restored. */
    MultimediaPrivateCache.Prepared restore(StudioMultimediaRuntime runtime) throws Exception {
        MultimediaPrivateCache.Prepared prepared=cache.read();if(prepared!=null)runtime.prepareFromVerifiedCloudCache(prepared);return prepared;
    }
    /** Restore the known generation offline without constructing an authenticated client or extending consent. */
    static MultimediaPrivateCache.Prepared restoreInert(Context context,TvFirst008Source source,StudioMultimediaRuntime runtime) throws Exception {
        String generation=source.multimediaCachedGeneration().replace("-","");
        InstallationStore store=new InstallationStore(new AndroidInstallationBackend(context,"multimedia003d_cache_"+generation));
        MultimediaPrivateCache cache=new MultimediaPrivateCache(new File(context.getFilesDir(),"multimedia003d/"+generation),store,new AndroidStudioAudioInspector(context));
        MultimediaPrivateCache.Prepared prepared=cache.read();if(prepared!=null)runtime.prepareFromVerifiedCloudCache(prepared);return prepared;
    }
}
