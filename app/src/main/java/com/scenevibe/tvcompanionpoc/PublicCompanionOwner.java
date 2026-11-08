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
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean closed,revoked;
    private volatile String code="",status="Connexion au catalogue…";
    private volatile long codeExpires;
    private PublicMultimediaCache cache;
    private PublicMultimediaCache.Prepared prepared;
    private PublicRuntimeReference reference;
    private long grantDeadline;

    /** Compose le stockage réel et le transport HTTPS déjà configuré, sans compte humain. */
    PublicCompanionOwner(Context context) {
        this(context,new TvFirst008State(context.getSharedPreferences("public001b_identity",0),new SecretStore.AndroidKeyStoreSecretStore(context)),
                new PublicCloudClient.Https(BuildConfig.CLOUD_ORIGIN));
    }
    /** La qualification native remplace seulement le port HTTP par un serveur de fixture. */
    PublicCompanionOwner(Context context,TvFirst008State state,PublicCloudClient.Transport transport){
        if(!BuildConfig.PUBLIC_AUTONOMOUS_001B)throw new IllegalStateException("PUBLIC_VARIANT_DISABLED");
        this.context=context.getApplicationContext();this.state=state;this.client=new PublicCloudClient(state,transport);
    }

    /** Démarre le suivi du grant existant ou affiche automatiquement un seul code temporaire. */
    void start(){worker.scheduleWithFixedDelay(this::tick,0,1500,TimeUnit.MILLISECONDS);}
    /** Retourne un code d'affichage seulement ; aucun secret de possession n'atteint l'Activity. */
    String code(){return code;}
    /** Retourne l'expiration du code, distincte du grant et du cache. */
    long codeExpires(){return codeExpires;}
    /** Rend un état borné sans contenu, URL de stockage ou credential. */
    String status(){return status;}
    /** Le banc peut déclencher un poll déterministe en gardant les vrais ports Android. */
    synchronized void pollOnce() throws Exception {step();}

    /** Exécute un seul cycle réseau ; une panne temporaire ne détruit ni association ni cache. */
    private synchronized void tick(){
        if(closed)return;
        try{step();}catch(Exception refused){
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
        if(client.finishChallenge()){code="";codeExpires=0;status="Télévision associée · sans compte.";}
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
        if(reference!=null)reference.renewed(grantDeadline);
        if(cache==null){
            cache=new PublicMultimediaCache(context,identity.getString("generation"));prepared=cache.read();
            if(prepared!=null){client.validate(prepared.binding);restorePreferences();}
        }
        JSONArray commands=client.json("poll",new JSONObject()).getJSONArray("commands");
        JSONObject command=null;
        for(int i=commands.length()-1;i>=0;i--)if(!"failed".equals(commands.getJSONObject(i).optString("state"))){command=commands.getJSONObject(i);break;}
        if(command==null){if(prepared==null)status="Télévision associée · choisis une Track depuis le catalogue.";else restorePreferences();return;}
        PublicCatalogueCandidate.Binding binding=new PublicCatalogueCandidate.Binding(command,identity.getString("deviceId"),identity.getString("generation"),System.currentTimeMillis());
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
            try{prepared=cache.download(client,binding,this::sessionBusy);status="Track téléchargée et vérifiée.";}
            catch(Exception refused){try{client.receipt(binding,"failed");}catch(Exception ignored){/* A revoked grant cannot acknowledge even failure. */}throw refused;}
            return;
        }
        if(prepared==null || !binding.same(prepared.binding))return;
        client.validate(binding);
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
        if(apply(text,audio)){
            state.write("publicPreferences",new JSONObject().put("commandId",binding.command).put("text",text).put("audio",audio));
            if(!"armed".equals(command.getString("state")))client.receipt(binding,"armed");
            status="Réglages appliqués · TEXT "+(text?"ON":"OFF")+" · AUDIO "+(audio?"ON":"OFF");
        }
    }

    /** Recontrôle aussi les sessions démarrées pendant le téléchargement ; une UI indisponible ferme le commit. */
    private boolean sessionBusy(){
        if(closed)return true;
        try{return onMain(TvFirst009TextServiceOwner::controlBusy);}catch(Exception unavailable){return true;}
    }

    /** Relit des préférences déjà choisies sur le Web, sans demander d'autorisation après redémarrage. */
    private void restorePreferences() throws Exception {
        JSONObject preferences=state.read("publicPreferences");
        if(preferences!=null && prepared.binding.command.equals(preferences.optString("commandId")))apply(preferences.getBoolean("text"),preferences.getBoolean("audio"));
    }

    /** Applique sur le propriétaire UI réel ; le reçu attend l'état natif effectivement pris en compte. */
    private boolean apply(boolean text,boolean audio) throws Exception {
        if(reference==null || !reference.prepared.binding.same(prepared.binding))reference=new PublicRuntimeReference(prepared,cache,state,grantDeadline,()->!closed && !revoked);
        return onMain(()->{
            String live=TvFirst009TextServiceOwner.publicCommand();
            if(TvFirst009TextServiceOwner.controlBusy() && !prepared.binding.command.equals(live)){status="Session actuelle conservée.";return false;}
            if(!text && !audio && live==null)return true;
            TvFirst009TextServiceOwner.setMultimediaTextEnabled(text);TvFirst009TextServiceOwner.setMultimediaAudioEnabled(audio);
            if(live==null){
                if(!TvFirst009TextServiceOwner.requestPublicStart(context,reference))status="En attente des permissions Android nécessaires.";
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
