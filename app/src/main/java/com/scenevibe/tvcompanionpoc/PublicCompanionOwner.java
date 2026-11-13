package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Propriétaire public Android : l'unique code TV associe le navigateur, puis
 * les commandes téléchargent et appliquent les préférences automatiquement.
 * Réseau/décodage restent hors UI ; service et moteurs restent sur le Looper.
 * Le transport 001-C relit uniquement la possession historique dans Keystore.
 * Ses reçus et préférences de commande restent dans les espaces publics.
 */
final class PublicCompanionOwner implements AutoCloseable {
    private static final long LIBRARY_RECHECK_MS = 120_000L;
    private static final long LIBRARY_OBSERVATION_MS = LIBRARY_RECHECK_MS + 15_000L;
    private final Context context;
    private final TvFirst008State state;
    private final PublicCloudClient client;
    private final boolean historical;
    private final TvFirst008State commandMetadata;
    private String cacheGeneration;
    private final PublicViewerPreferences viewerPreferences;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean closed,revoked;
    private volatile String code="",status="Connexion au catalogue…";
    private volatile long codeExpires;
    private PublicMultimediaCache cache;
    private PublicMultimediaCache.Prepared prepared;
    private PublicRuntimeReference reference;
    private long grantDeadline;
    private int failedPolls;
    private boolean followUp;
    private String qualificationStage = "idle";

    /** Trace une étape fermée sur le seul build debug du banc isolé, sans credential ni contenu. */
    private void qualificationTrace(String stage) {
        if (BuildConfig.DEBUG && "https://scenevibe-product-consolidation001q2-viewe-git-6ef46d-archc-ode.vercel.app"
                .equals(BuildConfig.PUBLIC_VIEWER_ORIGIN)) {
            qualificationStage = stage;
            android.util.Log.i("SceneVibePublic", (historical ? "historical " : "public ") + stage);
        }
    }
    private String reportedWait="";
    private volatile List<PublicLibraryEntry> library = Collections.emptyList();
    private PublicLibraryEntry cachedEntry, pendingEntry;
    private long libraryCheckedAt = -1L;

    /** Compose le stockage réel et le transport HTTPS déjà configuré, sans compte humain. */
    PublicCompanionOwner(Context context) {
        this(context,identityState(context),new PublicCloudClient.Https(
                BuildConfig.PUBLIC_VIEWER_001?BuildConfig.PUBLIC_VIEWER_ORIGIN:BuildConfig.CLOUD_ORIGIN));
    }
    /** Le démarrage et le service utilisent le même espace public de possession chiffrée. */
    static TvFirst008State identityState(Context context) {
        return new TvFirst008State(context.getSharedPreferences("public001b_identity",0),
                new SecretStore.AndroidKeyStoreSecretStore(context));
    }
    /** La qualification native remplace seulement le port HTTP par un serveur de fixture. */
    PublicCompanionOwner(Context context,TvFirst008State state,PublicCloudClient.Transport transport){
        this(context,state,transport,false);
    }

    /** Réutilise le vrai service de réception avec la possession historique, sans chemin d'association. */
    static PublicCompanionOwner historical(Context context) {
        return new PublicCompanionOwner(context,new TvFirst008State(context),
                new PublicCloudClient.Https(BuildConfig.CLOUD_ORIGIN,true),true);
    }

    /** Remplace uniquement le transport pour le banc Android contrôlé. */
    static PublicCompanionOwner ownedHistoricalFixture(Context context,TvFirst008State state,PublicCloudClient.Transport transport){
        if(!BuildConfig.DEBUG || !BuildConfig.CLOUD_ORIGIN.isEmpty())throw new IllegalStateException("OWNED_DEBUG_FIXTURE_ONLY");
        return new PublicCompanionOwner(context,state,transport,true);
    }

    /** Les écritures 001-C sont séparées du namespace privé utilisé pour lire la possession. */
    private PublicCompanionOwner(Context context,TvFirst008State state,PublicCloudClient.Transport transport,boolean historical){
        if(!BuildConfig.PUBLIC_AUTONOMOUS_001B)throw new IllegalStateException("PUBLIC_VARIANT_DISABLED");
        this.context=context.getApplicationContext();this.state=state;this.client=new PublicCloudClient(state,transport);
        this.historical=historical;
        commandMetadata=historical?new TvFirst008State(context.getSharedPreferences("public001c_commands",Context.MODE_PRIVATE),
                new SecretStore.AndroidKeyStoreSecretStore(context)):state;
        viewerPreferences=BuildConfig.PUBLIC_VIEWER_001?new PublicViewerPreferences(this.context):null;
    }

    /** Démarre le suivi du grant existant ou affiche automatiquement un seul code temporaire. */
    void start(){
        qualificationTrace("worker-start");
        if(BuildConfig.PUBLIC_VIEWER_001)worker.execute(this::tickViewer);
        else worker.scheduleWithFixedDelay(this::tick,0,1500,TimeUnit.MILLISECONDS);
    }
    /** Réserve les polls courts au code et à la fin d'une préparation. */
    private void tickViewer(){
        tick();
        if(closed)return;
        long delay=failedPolls>0?Math.min(120000L,10000L*(1L<<Math.min(failedPolls,4)))
                :!code.isEmpty()?1500L:followUp?3000L:10000L;
        try{worker.schedule(this::tickViewer,delay,TimeUnit.MILLISECONDS);}
        catch(java.util.concurrent.RejectedExecutionException stopped){if(!closed)throw stopped;}
    }
    /** Retourne un code d'affichage seulement ; aucun secret de possession n'atteint l'Activity. */
    String code(){return code;}
    /** Retourne l'expiration du code, distincte du grant et du cache. */
    long codeExpires(){return codeExpires;}
    /** Rend un état borné sans contenu, URL de stockage ou credential. */
    String status(){return status;}
    /** Expose les observations du worker sans accès au cache, au réseau ou au Keystore. */
    List<PublicLibraryEntry> library(){return library;}
    /** Les deux commandes locales restent accessibles même lorsque les commentaires sont arrêtés. */
    PublicViewerPreferences viewerPreferences(){return viewerPreferences;}
    /** Le banc peut déclencher un poll déterministe en gardant les vrais ports Android. */
    synchronized void pollOnce() throws Exception {step();}

    /** Exécute un seul cycle réseau ; une panne temporaire ne détruit ni association ni cache. */
    private synchronized void tick(){
        if(closed)return;
        followUp=false;
        try{step();failedPolls=0;}catch(Exception refused){
            failedPolls=Math.min(4,failedPolls+1);
            String reason=refused.getMessage();
            qualificationTrace("retry stage=" + qualificationStage.split(" ", 2)[0] + " count=" + failedPolls
                    + " exception=" + refused.getClass().getSimpleName()
                    + (reason != null && reason.matches("PUBLIC_[A-Z0-9_]+|REVOKED|[A-Z_]+_TIMEOUT") ? " code=" + reason : ""));
            if(reason!=null && (reason.contains("HTTP_401") || reason.contains("HTTP_403") || reason.equals("REVOKED"))){
                revoked=true;status="Association expirée ou révoquée.";
                if(cachedEntry!=null)cachedEntry=cachedEntry.withState(PublicLibraryEntry.State.UNAVAILABLE,0);
                if(pendingEntry!=null)pendingEntry=pendingEntry.withState(PublicLibraryEntry.State.UNAVAILABLE,0);
            }else{
                status="Catalogue momentanément indisponible · cache conservé.";
                if(cachedEntry!=null && cachedEntry.state==PublicLibraryEntry.State.VERIFIED)
                    cachedEntry=cachedEntry.withState(PublicLibraryEntry.State.CHECKING,0);
                libraryCheckedAt=-1L;
                if(pendingEntry!=null && pendingEntry.state==PublicLibraryEntry.State.DOWNLOADING)
                    pendingEntry=pendingEntry.withState(PublicLibraryEntry.State.CHECKING,0);
            }
            publishLibrary();
        }
    }

    /** Renouvelle uniquement un code encore non associé, jamais une seconde approbation. */
    private void pairing() throws Exception {
        JSONObject pending=state.read("displayChallenge");
        if(pending!=null && Instant.parse(pending.getString("expiresAt")).toEpochMilli()<=System.currentTimeMillis()){
            state.write("challenge",null);state.write("displayChallenge",null);pending=null;
        }
        if(pending==null)pending=client.challenge();
        code=pending.getString("userCode");codeExpires=Instant.parse(pending.getString("expiresAt")).toEpochMilli();status="Saisis ce code depuis le catalogue sur ton téléphone ou PC.";
        if(client.finishChallenge()){
            if(viewerPreferences!=null)viewerPreferences.initialize(null);
            code="";codeExpires=0;followUp=true;status="Télévision associée · sans compte.";
        }
    }

    /** Revalide automatiquement possession et génération avant de traiter les commandes publiques. */
    private void step() throws Exception {
        if(closed)return;
        qualificationTrace("identity");
        JSONObject identity=client.identity();
        if(identity==null){
            cachedEntry=null;pendingEntry=null;publishLibrary();
            if(!historical)pairing();
            else {revoked=true;status="Association historique indisponible.";}
            return;
        }
        qualificationTrace("association-request");
        JSONObject association=client.json("association",new JSONObject());
        qualificationTrace("association-response");
        if(!identity.getString("deviceId").equals(association.optString("deviceId")) || !identity.getString("generation").equals(association.optString("generation")))throw new IllegalArgumentException("PUBLIC_ASSOCIATION_MISMATCH");
        long remaining=Instant.parse(association.getString("consentExpiresAt")).toEpochMilli()-System.currentTimeMillis();
        if(remaining<=0)throw new IllegalArgumentException("REVOKED");
        grantDeadline=SystemClock.elapsedRealtime()+remaining;revoked=false;
        if(viewerPreferences!=null)viewerPreferences.initialize(historical
                ?identityState(context).read("publicPreferences"):commandMetadata.read("publicPreferences"));
        String generation=identity.getString("generation");
        boolean restored=false;
        if(cacheGeneration!=null && !generation.equals(cacheGeneration)){
            if(reference!=null)reference.invalidate();
            reference=null;cache=null;prepared=null;
            cachedEntry=null;pendingEntry=null;publishLibrary();
        }
        if(cache==null){
            qualificationTrace("cache-reopen");
            cache=new PublicMultimediaCache(context,generation);cacheGeneration=generation;prepared=cache.read();
            restored=prepared!=null;
        }
        if(prepared!=null && !cache.matches(prepared.binding)){
            invalidateReference();
            prepared=null;cachedEntry=null;publishLibrary();
        }
        boolean validateForPlayback=reference!=null && reference.viewerPreferences!=null;
        // Le cache inactif est contrôlé sans doubler les contrôles de lecture déjà planifiés.
        boolean validateForLibrary=viewerPreferences!=null && (libraryCheckedAt<0
                || SystemClock.elapsedRealtime()-libraryCheckedAt>=LIBRARY_RECHECK_MS);
        boolean validateBeforePoll=restored || validateForPlayback || validateForLibrary;
        if(prepared!=null && validateBeforePoll){
            if(historical && prepared.binding.expires<=System.currentTimeMillis()){
                observeCache(prepared,PublicLibraryEntry.State.UNAVAILABLE);
                invalidateReference();
                prepared=null;
            }else{
                try{
                    qualificationTrace("cache-validate");
                    validateCached(prepared);
                    if(restored){
                        qualificationTrace("restore-preferences");
                        restorePreferences();
                    }
                }
                catch(Exception refused){
                    if(!cacheRefused(refused))throw refused;
                    invalidateReference();
                    prepared=null;
                }
            }
        }
        if(reference!=null)reference.renewed(effectDeadline(reference.prepared.binding));
        qualificationTrace("poll-request");
        JSONObject polled=client.json("poll",new JSONObject());
        qualificationTrace("poll-response");
        JSONArray commands=polled.getJSONArray("commands");
        JSONObject command=null;
        for(int i=commands.length()-1;i>=0;i--)if(!"failed".equals(commands.getJSONObject(i).optString("state"))){command=commands.getJSONObject(i);break;}
        if(command==null){
            pendingEntry=null;publishLibrary();
            if(prepared==null)status="Télévision associée · choisis une Track depuis le catalogue.";
            else restorePreferences();
            return;
        }
        PublicCatalogueCandidate.Binding binding=new PublicCatalogueCandidate.Binding(command,identity.getString("deviceId"),identity.getString("generation"),System.currentTimeMillis());
        qualificationTrace("selected " + binding.command);
        boolean supportsWait=polled.optBoolean("mediaWaitSupported",false);
        boolean automatic=viewerPreferences!=null && automaticCommand(polled.optJSONArray("automaticCommandIds"),binding.command);
        if(automatic && !hasEffectPermissions()){
            status="Autorisation Android de superposition ou MEDIA indisponible.";
            pendingEntry=null;publishLibrary();
            client.receipt(binding,"failed");
            return;
        }
        if("requested".equals(command.getString("state"))){
            boolean busy=onMain(()->TvFirst009TextServiceOwner.controlBusy());
            if(busy && (historical || BuildConfig.PUBLIC_HISTORICAL_VIEWER_001C || supportsWait && automatic)){
                String[] reason={""};
                onMain(()->{
                    if(!TvFirst009TextServiceOwner.controlBusy())return false;
                    String publicCommand=TvFirst009TextServiceOwner.publicCommand();
                    if(publicCommand!=null && TvFirst009TextServiceOwner.publicMayYield() && prefer(binding.command)){
                        TvFirst009TextServiceOwner.requestStop(context);
                        return true;
                    }
                    reason[0]=publicCommand==null?"private-media":"public-media";
                    return false;
                });
                if(!reason[0].isEmpty())reportWait(binding,reason[0],supportsWait && automatic);
                return;
            }
            if(busy && (prepared==null || prepared.binding.track.equals(binding.track))){
                if(onMain(TvFirst009TextServiceOwner::publicMediaEnded)){
                    onMain(()->{TvFirst009TextServiceOwner.requestStop(context);return true;});reference=null;
                }
                observePending(binding,PublicLibraryEntry.State.WAITING);
                status="Nouvelle révision en attente · lecture actuelle conservée.";return;
            }
            if(busy){
                if(!onMain(()->{if(TvFirst009TextServiceOwner.publicCommand()!=null){TvFirst009TextServiceOwner.requestStop(context);return true;}return false;})){
                    observePending(binding,PublicLibraryEntry.State.WAITING);
                    status="Track publique en attente · session privée conservée.";return;
                }
                reference=null;return;
            }
            reportedWait="";
            if((historical || BuildConfig.PUBLIC_HISTORICAL_VIEWER_001C) && !prefer(binding.command))return;
            qualificationTrace("download-start " + binding.command);
            status="Téléchargement et vérification TEXT/AUDIO…";
            observePending(binding,PublicLibraryEntry.State.DOWNLOADING);
            try{
                if(automatic && cache.matches(binding)){
                    prepared=cache.read();validateCached(prepared);client.receipt(binding,"cached");
                }else prepared=cache.download(client,binding,this::sessionBusy);
                qualificationTrace("cache-receipt " + binding.command);
                pendingEntry=null;
                observeCache(prepared,PublicLibraryEntry.State.VERIFIED);
                reference=null;
                followUp=true;status="Track téléchargée et vérifiée.";
            }catch(Exception refused){
                if(automatic && supportsWait && "PUBLIC_ACTIVE_SESSION".equals(refused.getMessage())){
                    String[] waitReason={null};
                    onMain(()->{
                        if(!TvFirst009TextServiceOwner.controlBusy())return false;
                        waitReason[0]=TvFirst009TextServiceOwner.publicCommand()==null?"private-media":"public-media";
                        return true;
                    });
                    if(waitReason[0]!=null){
                        reportWait(binding,waitReason[0],true);
                        return;
                    }
                }
                if(cacheRefused(refused))observePending(binding,PublicLibraryEntry.State.UNAVAILABLE);
                // Le journal conserve la même demande automatique jusqu'à reprise ou échéance.
                if(!automatic)try{client.receipt(binding,"failed");}catch(Exception ignored){/* A revoked grant cannot acknowledge even failure. */}
                throw refused;
            }
            return;
        }
        if(prepared==null || !binding.same(prepared.binding))return;
        if((historical || supportsWait) && automatic && "cached".equals(command.getString("state"))){
            String[] reason={""};
            if(onMain(()->{
                String active=TvFirst009TextServiceOwner.publicCommand();
                if(!TvFirst009TextServiceOwner.controlBusy() || binding.command.equals(active))return false;
                reason[0]=active==null?"private-media":"public-media";
                return true;
            })){
                reportWait(binding,reason[0],supportsWait);
                return;
            }
        }
        pendingEntry=null;publishLibrary();
        if(!validateBeforePoll && (reference==null || reference.viewerPreferences==null))validateCached(prepared);
        android.content.SharedPreferences playback=context.getSharedPreferences("public001d_playback",0);
        boolean stopped=binding.command.equals(playback.getString("stoppedCommand",""));
        if(stopped && "cached".equals(command.getString("state"))
                && (binding.version<0 || binding.version>playback.getLong("stoppedVersion",-1))){
            playback.edit().remove("stoppedCommand").remove("stoppedVersion").commit();stopped=false;
        }
        if(stopped){
            if("armed".equals(command.getString("state")) && (command.getBoolean("text") || command.getBoolean("audio"))){
                JSONObject preferences=new JSONObject().put("commandId",binding.command).put("text",false).put("audio",false);
                if(binding.version>=0)preferences.put("expectedVersion",binding.version);
                client.json("device-preferences",preferences);
            }
            if(!automatic)commandMetadata.write("publicPreferences",new JSONObject().put("commandId",binding.command).put("text",false).put("audio",false));
            status="Commentaires arrêtés · réactivation disponible depuis le catalogue.";return;
        }
        if(automatic){
            applyAutomatic(command,binding);
            return;
        }
        boolean text=command.getBoolean("text"),audio=command.getBoolean("audio");
        if("armed".equals(command.getString("state"))){
            boolean[] local={text,audio};boolean publicLive=onMain(()->{
                if(!binding.command.equals(TvFirst009TextServiceOwner.publicCommand()))return false;
                local[0]=TvFirst009TextServiceOwner.multimediaTextEnabled();local[1]=TvFirst009TextServiceOwner.multimediaAudioEnabled();return true;
            });
            if(publicLive && (local[0]!=text || local[1]!=audio)){
                JSONObject preferences=new JSONObject().put("commandId",binding.command).put("text",local[0]).put("audio",local[1]);
                if(binding.version>=0)preferences.put("expectedVersion",binding.version);
                client.json("device-preferences",preferences);
                text=local[0];audio=local[1];
            }
        }
        if(apply(text,audio,binding.version,false)){
            commandMetadata.write("publicPreferences",new JSONObject().put("commandId",binding.command).put("text",text).put("audio",audio));
            if(!"armed".equals(command.getString("state")))client.receipt(binding,"armed");
            status="Réglages appliqués · TEXT "+(text?"ON":"OFF")+" · AUDIO "+(audio?"ON":"OFF");
        }
    }

    /** Conserve le motif local et transmet au plus un reçu par version de commande et motif. */
    private void reportWait(PublicCatalogueCandidate.Binding binding,String reason,boolean supported) throws Exception {
        status="private-media".equals(reason)?"Track publique en attente · session privée conservée."
                :"Track publique en attente · lecture publique en cours.";
        observePending(binding,PublicLibraryEntry.State.WAITING);
        String key=binding.command+":"+binding.version+":"+reason;
        if(supported && !key.equals(reportedWait)){
            client.mediaWait(binding,reason);
            reportedWait=key;
        }
    }

    /** L'indication du mode reste en dehors du JSON fermé de la commande v1. */
    private boolean automaticCommand(JSONArray ids,String command){
        if(ids==null)return false;
        for(int i=0;i<ids.length();i++)if(command.equals(ids.optString(i)))return true;
        return false;
    }

    /** L'activation automatique respecte les accès réellement accordés par Android. */
    private boolean hasEffectPermissions(){
        return android.provider.Settings.canDrawOverlays(context) && NotificationAccess.isGranted(context);
    }

    /** Retire la référence en mémoire quand le cache ne permet plus la restitution. */
    private void invalidateReference() {
        if(reference!=null)reference.invalidate();
        reference=null;
    }

    /** Réutilise le contrôle existant de publication, génération et empreinte. */
    private void validateCached(PublicMultimediaCache.Prepared value) throws Exception {
        try {
            client.validate(value.binding);
            observeCache(value,PublicLibraryEntry.State.VERIFIED);
        } catch(Exception refused) {
            observeCache(value,cacheRefused(refused)?PublicLibraryEntry.State.UNAVAILABLE:PublicLibraryEntry.State.CHECKING);
            throw refused;
        }
    }

    /** Reconnaît le refus du cache sans confondre une coupure réseau avec un retrait. */
    private static boolean cacheRefused(Exception refused) {
        return "PUBLIC_HTTP_404".equals(refused.getMessage()) || "PUBLIC_HTTP_409".equals(refused.getMessage())
                || "PUBLIC_CACHE_AUTHORITY_REFUSED".equals(refused.getMessage());
    }

    /** Projette le pointeur complet revalidé sans ajouter de lecture Cloud ni de stockage. */
    private void observeCache(PublicMultimediaCache.Prepared value,PublicLibraryEntry.State observed) {
        long now=SystemClock.elapsedRealtime();
        if(observed==PublicLibraryEntry.State.VERIFIED)libraryCheckedAt=now;
        cachedEntry=new PublicLibraryEntry(value.binding.command,value.track.title(),value.track.revision,
                observed,Math.min(effectDeadline(value.binding),now+LIBRARY_OBSERVATION_MS));
        publishLibrary();
    }

    /** Une commande n'a de titre éditorial qu'une fois son paquet relu et vérifié. */
    private void observePending(PublicCatalogueCandidate.Binding binding,PublicLibraryEntry.State observed) {
        long deadline=Math.min(grantDeadline,SystemClock.elapsedRealtime()+Math.max(0,binding.expires-System.currentTimeMillis()));
        pendingEntry=cachedEntry!=null && binding.command.equals(cachedEntry.command)
                ?cachedEntry.withState(observed,deadline):new PublicLibraryEntry(binding.command,"",0,observed,deadline);
        publishLibrary();
    }

    /** Envoie un instantané immuable. Un différé du cache courant s'affiche sur sa seule ligne. */
    private void publishLibrary() {
        if(pendingEntry==null){
            library=cachedEntry==null?Collections.emptyList():Collections.singletonList(cachedEntry);
        }else if(cachedEntry==null || pendingEntry.command.equals(cachedEntry.command)){
            library=Collections.singletonList(pendingEntry);
        }else{
            library=Collections.unmodifiableList(Arrays.asList(cachedEntry,pendingEntry));
        }
    }

    /** Arme avec les choix TV réels puis renvoie le reçu, ou synchronise leur changement. */
    private void applyAutomatic(JSONObject command,PublicCatalogueCandidate.Binding binding) throws Exception {
        boolean text=viewerPreferences.text(),audio=viewerPreferences.audio();
        if(!apply(text,audio,binding.version,true))return;
        if("armed".equals(command.getString("state"))){
            if(text!=command.getBoolean("text") || audio!=command.getBoolean("audio")){
                JSONObject update=new JSONObject().put("commandId",binding.command).put("text",text).put("audio",audio);
                if(binding.version>=0)update.put("expectedVersion",binding.version);
                client.json("device-preferences",update);
            }
        }else {
            client.receipt(binding,"armed",text,audio);
            qualificationTrace("armed-receipt " + binding.command);
        }
        JSONObject saved=commandMetadata.read("publicPreferences");
        if(saved==null || !binding.command.equals(saved.optString("commandId"))
                || !saved.optBoolean("viewer",false) || text!=saved.optBoolean("text") || audio!=saved.optBoolean("audio"))
            commandMetadata.write("publicPreferences",new JSONObject().put("commandId",binding.command).put("viewer",true).put("text",text).put("audio",audio));
        status="Track prête · TEXT "+(text?"ON":"OFF")+" · AUDIO "+(audio?"ON":"OFF");
    }

    /** Recontrôle aussi les sessions démarrées pendant le téléchargement ; une UI indisponible ferme le commit. */
    private boolean sessionBusy(){
        if(closed)return true;
        try{return onMain(TvFirst009TextServiceOwner::controlBusy);}catch(Exception unavailable){return true;}
    }

    /** Relit des préférences déjà choisies sur le Web, sans demander d'autorisation après redémarrage. */
    private void restorePreferences() throws Exception {
        JSONObject preferences=commandMetadata.read("publicPreferences");
        if(prepared.binding.command.equals(context.getSharedPreferences("public001d_playback",0).getString("stoppedCommand","")))return;
        if(preferences!=null && prepared.binding.command.equals(preferences.optString("commandId"))){
            boolean automatic=viewerPreferences!=null && preferences.optBoolean("viewer",false);
            apply(automatic?viewerPreferences.text():preferences.getBoolean("text"),
                    automatic?viewerPreferences.audio():preferences.getBoolean("audio"),prepared.binding.version,automatic);
        }
    }

    /** Mémorise le dernier nouvel envoi pour éviter qu'un autre transport ne réarme un ancien reçu. */
    private boolean prefer(String command){
        return context.getSharedPreferences("public001d_playback",Context.MODE_PRIVATE).edit()
                .putString("preferredCommand",command).commit();
    }

    /** L'échéance de la commande historique borne les effets, indépendamment du grant. */
    private long effectDeadline(PublicCatalogueCandidate.Binding binding){
        if(!historical)return grantDeadline;
        return Math.min(grantDeadline,SystemClock.elapsedRealtime()+Math.max(0,binding.expires-System.currentTimeMillis()));
    }

    /** Applique sur le propriétaire UI réel ; le reçu attend l'état natif effectivement pris en compte. */
    private boolean apply(boolean text,boolean audio,long version,boolean automatic) throws Exception {
        if(reference==null || !reference.prepared.binding.same(prepared.binding))reference=new PublicRuntimeReference(
                prepared,cache,state,effectDeadline(prepared.binding),()->!closed && !revoked,automatic?viewerPreferences:null);
        return onMain(()->{
            android.content.SharedPreferences playback=context.getSharedPreferences("public001d_playback",0);
            if(prepared.binding.command.equals(playback.getString("stoppedCommand","")))return false;
            if(BuildConfig.PUBLIC_HISTORICAL_VIEWER_001C &&
                    !prepared.binding.command.equals(playback.getString("preferredCommand",prepared.binding.command)))return false;
            long observed=prepared.binding.command.equals(playback.getString("observedCommand",""))
                    ?Math.max(version,playback.getLong("observedVersion",-1)):version;
            if(!automatic || !prepared.binding.command.equals(playback.getString("observedCommand",""))
                    || observed!=playback.getLong("observedVersion",-1))
                playback.edit().putString("observedCommand",prepared.binding.command).putLong("observedVersion",observed).commit();
            String live=TvFirst009TextServiceOwner.publicCommand();
            if(TvFirst009TextServiceOwner.controlBusy() && !prepared.binding.command.equals(live)){status="Session actuelle conservée.";return false;}
            if(automatic && !hasEffectPermissions()){
                status="En attente des permissions Android nécessaires.";return false;
            }
            if(!text && !audio && live==null)return true;
            if(automatic)TvFirst009TextServiceOwner.applyViewerPreferences();
            else{
                TvFirst009TextServiceOwner.setMultimediaTextEnabled(text);
                TvFirst009TextServiceOwner.setMultimediaAudioEnabled(audio);
            }
            if(live==null){
                if(TvFirst009TextServiceOwner.requestPublicStart(context,reference))followUp=true;
                else status="En attente des permissions Android nécessaires.";
                return false;
            }
            return prepared.binding.command.equals(live) && TvFirst009TextServiceOwner.multimediaTextEnabled()==text && TvFirst009TextServiceOwner.multimediaAudioEnabled()==audio;
        });
    }

    /** Exécute une mutation bornée sur le vrai Looper, sans action tardive après timeout. */
    private boolean onMain(java.util.function.BooleanSupplier action) throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper())return action.getAsBoolean();
        CountDownLatch done=new CountDownLatch(1);boolean[] value={false};java.util.concurrent.atomic.AtomicBoolean live=new java.util.concurrent.atomic.AtomicBoolean(true);
        main.post(()->{try{if(live.get() && !closed)value[0]=action.getAsBoolean();}finally{done.countDown();}});
        if(!done.await(5,TimeUnit.SECONDS)){live.set(false);throw new IllegalStateException("PUBLIC_UI_TIMEOUT");}return value[0];
    }
    /** Invalide les callbacks de ce seul propriétaire, en laissant tous les fichiers de cache. */
    @Override public void close(){closed=true;worker.shutdownNow();}
}
