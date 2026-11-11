package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.time.Instant;
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
 * Les identités et caches privés historiques ne sont jamais consultés.
 */
final class PublicCompanionOwner implements AutoCloseable {
    private final Context context;
    private final TvFirst008State state;
    private final PublicCloudClient client;
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
        if(!BuildConfig.PUBLIC_AUTONOMOUS_001B)throw new IllegalStateException("PUBLIC_VARIANT_DISABLED");
        this.context=context.getApplicationContext();this.state=state;this.client=new PublicCloudClient(state,transport);
        viewerPreferences=BuildConfig.PUBLIC_VIEWER_001?new PublicViewerPreferences(this.context):null;
    }

    /** Démarre le suivi du grant existant ou affiche automatiquement un seul code temporaire. */
    void start(){
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
            if(reason!=null && (reason.contains("HTTP_401") || reason.contains("HTTP_403") || reason.equals("REVOKED"))){revoked=true;status="Association expirée ou révoquée.";}
            else status="Catalogue momentanément indisponible · cache conservé.";
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
        JSONObject identity=client.identity();if(identity==null){pairing();return;}
        JSONObject association=client.json("association",new JSONObject());
        if(!identity.getString("deviceId").equals(association.optString("deviceId")) || !identity.getString("generation").equals(association.optString("generation")))throw new IllegalArgumentException("PUBLIC_ASSOCIATION_MISMATCH");
        long remaining=Instant.parse(association.getString("consentExpiresAt")).toEpochMilli()-System.currentTimeMillis();
        if(remaining<=0)throw new IllegalArgumentException("REVOKED");
        grantDeadline=SystemClock.elapsedRealtime()+remaining;revoked=false;
        if(viewerPreferences!=null)viewerPreferences.initialize(state.read("publicPreferences"));
        if(reference!=null){
            if(reference.viewerPreferences!=null)validateActive();
            if(reference!=null)reference.renewed(grantDeadline);
        }
        if(cache==null){
            cache=new PublicMultimediaCache(context,identity.getString("generation"));prepared=cache.read();
            if(prepared!=null){client.validate(prepared.binding);restorePreferences();}
        }
        JSONObject polled=client.json("poll",new JSONObject());
        JSONArray commands=polled.getJSONArray("commands");
        JSONObject command=null;
        for(int i=commands.length()-1;i>=0;i--)if(!"failed".equals(commands.getJSONObject(i).optString("state"))){command=commands.getJSONObject(i);break;}
        if(command==null){if(prepared==null)status="Télévision associée · choisis une Track depuis le catalogue.";else restorePreferences();return;}
        PublicCatalogueCandidate.Binding binding=new PublicCatalogueCandidate.Binding(command,identity.getString("deviceId"),identity.getString("generation"),System.currentTimeMillis());
        boolean automatic=viewerPreferences!=null && automaticCommand(polled.optJSONArray("automaticCommandIds"),binding.command);
        if("requested".equals(command.getString("state"))){
            boolean busy=onMain(()->TvFirst009TextServiceOwner.controlBusy());
            if(busy && (prepared==null || prepared.binding.track.equals(binding.track))){
                if(onMain(TvFirst009TextServiceOwner::publicMediaEnded)){
                    onMain(()->{TvFirst009TextServiceOwner.requestStop(context);return true;});reference=null;
                }
                status="Nouvelle révision en attente · lecture actuelle conservée.";return;
            }
            if(busy){
                if(!onMain(()->{if(TvFirst009TextServiceOwner.publicCommand()!=null){TvFirst009TextServiceOwner.requestStop(context);return true;}return false;})){
                    status="Track publique en attente · session privée conservée.";return;
                }
                reference=null;return;
            }
            status="Téléchargement et vérification TEXT/AUDIO…";
            try{
                if(automatic && cache.matches(binding)){
                    prepared=cache.read();client.validate(binding);client.receipt(binding,"cached");
                }else prepared=cache.download(client,binding,this::sessionBusy);
                reference=null;
                followUp=true;status="Track téléchargée et vérifiée.";
            }catch(Exception refused){
                // Le journal conserve la même demande automatique jusqu'à reprise ou échéance.
                if(!automatic)try{client.receipt(binding,"failed");}catch(Exception ignored){/* A revoked grant cannot acknowledge even failure. */}
                throw refused;
            }
            return;
        }
        if(prepared==null || !binding.same(prepared.binding))return;
        if(reference==null || reference.viewerPreferences==null)client.validate(binding);
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
            if(!automatic)state.write("publicPreferences",new JSONObject().put("commandId",binding.command).put("text",false).put("audio",false));
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
            state.write("publicPreferences",new JSONObject().put("commandId",binding.command).put("text",text).put("audio",audio));
            if(!"armed".equals(command.getString("state")))client.receipt(binding,"armed");
            status="Réglages appliqués · TEXT "+(text?"ON":"OFF")+" · AUDIO "+(audio?"ON":"OFF");
        }
    }

    /** L'indication du mode reste en dehors du JSON fermé de la commande v1. */
    private boolean automaticCommand(JSONArray ids,String command){
        if(ids==null)return false;
        for(int i=0;i<ids.length();i++)if(command.equals(ids.optString(i)))return true;
        return false;
    }

    /** Une révocation de cette révision suspend ses effets, même si l'association existe encore. */
    private void validateActive() throws Exception {
        try{client.validate(reference.prepared.binding);}
        catch(Exception failure){
            String reason=failure.getMessage();
            if(reason!=null && (reason.equals("PUBLIC_HTTP_404") || reason.equals("PUBLIC_CACHE_AUTHORITY_REFUSED"))){
                reference.invalidate();reference=null;
                return;
            }
            throw failure;
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
        }else client.receipt(binding,"armed",text,audio);
        JSONObject saved=state.read("publicPreferences");
        if(saved==null || !binding.command.equals(saved.optString("commandId"))
                || !saved.optBoolean("viewer",false) || text!=saved.optBoolean("text") || audio!=saved.optBoolean("audio"))
            state.write("publicPreferences",new JSONObject().put("commandId",binding.command).put("viewer",true).put("text",text).put("audio",audio));
        status="Track prête · TEXT "+(text?"ON":"OFF")+" · AUDIO "+(audio?"ON":"OFF");
    }

    /** Recontrôle aussi les sessions démarrées pendant le téléchargement ; une UI indisponible ferme le commit. */
    private boolean sessionBusy(){
        if(closed)return true;
        try{return onMain(TvFirst009TextServiceOwner::controlBusy);}catch(Exception unavailable){return true;}
    }

    /** Relit des préférences déjà choisies sur le Web, sans demander d'autorisation après redémarrage. */
    private void restorePreferences() throws Exception {
        JSONObject preferences=state.read("publicPreferences");
        if(prepared.binding.command.equals(context.getSharedPreferences("public001d_playback",0).getString("stoppedCommand","")))return;
        if(preferences!=null && prepared.binding.command.equals(preferences.optString("commandId"))){
            boolean automatic=viewerPreferences!=null && preferences.optBoolean("viewer",false);
            apply(automatic?viewerPreferences.text():preferences.getBoolean("text"),
                    automatic?viewerPreferences.audio():preferences.getBoolean("audio"),prepared.binding.version,automatic);
        }
    }

    /** Applique sur le propriétaire UI réel ; le reçu attend l'état natif effectivement pris en compte. */
    private boolean apply(boolean text,boolean audio,long version,boolean automatic) throws Exception {
        if(reference==null || !reference.prepared.binding.same(prepared.binding))reference=new PublicRuntimeReference(
                prepared,cache,state,grantDeadline,()->!closed && !revoked,automatic?viewerPreferences:null);
        return onMain(()->{
            android.content.SharedPreferences playback=context.getSharedPreferences("public001d_playback",0);
            if(prepared.binding.command.equals(playback.getString("stoppedCommand","")))return false;
            long observed=prepared.binding.command.equals(playback.getString("observedCommand",""))
                    ?Math.max(version,playback.getLong("observedVersion",-1)):version;
            playback.edit().putString("observedCommand",prepared.binding.command).putLong("observedVersion",observed).commit();
            String live=TvFirst009TextServiceOwner.publicCommand();
            if(TvFirst009TextServiceOwner.controlBusy() && !prepared.binding.command.equals(live)){status="Session actuelle conservée.";return false;}
            if(automatic && (!android.provider.Settings.canDrawOverlays(context) || !NotificationAccess.isGranted(context))){
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
