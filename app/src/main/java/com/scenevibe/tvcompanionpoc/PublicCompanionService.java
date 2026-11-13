package com.scenevibe.tvcompanionpoc;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.IBinder;
import java.util.ArrayList;
import java.util.List;

/**
 * Propriétaire persistant des commandes publiques après fermeture de l'Activity.
 * Notification passive et même permission FGS déjà présente ; aucun lecteur
 * tiers n'est lancé, pausé ou contrôlé. Le renderer reste OverlayService v31.
 */
public final class PublicCompanionService extends Service {
    private static volatile PublicCompanionOwner current;
    private static volatile List<PublicCompanionOwner> libraryOwners=List.of();
    private static volatile String failure="";
    private PublicCompanionOwner owner;
    private PublicCompanionOwner historical;
    static final String SHOW_PUBLIC_PAIRING="com.scenevibe.tvcompanionpoc.SHOW_PUBLIC_PAIRING";
    private static PublicCompanionOwner ownedFixture;
    /** Le banc utilise son seul réseau synthétique ; l'APK signé interdit cette substitution. */
    static void useOwnedFixture(PublicCompanionOwner fixture){
        if(!BuildConfig.DEBUG || !BuildConfig.PUBLIC_AUTONOMOUS_001B || !BuildConfig.CLOUD_ORIGIN.isEmpty())throw new IllegalStateException("OWNED_DEBUG_FIXTURE_ONLY");
        ownedFixture=fixture;
    }
    /** Expose seulement le code et l'état d'affichage du propriétaire public. */
    static PublicCompanionOwner current(){return current;}
    /** Lit les deux récepteurs sans confondre leurs générations ou prendre leur possession. */
    static List<PublicLibraryEntry> library(){
        List<PublicLibraryEntry> entries=new ArrayList<>();
        for(PublicCompanionOwner receiver:libraryOwners)entries.addAll(receiver.library());
        return entries;
    }
    /** Retourne un échec borné si l'origine Cloud publique n'est pas configurée. */
    static String failure(){return failure;}
    /** Vérifie la présence locale sans lire le credential ni démarrer un écran. */
    static boolean hasHistoricalIdentity(android.content.Context context){
        return BuildConfig.PUBLIC_HISTORICAL_VIEWER_001C && new TvFirst008State(context).read("identity")!=null;
    }
    /** Reprend uniquement le service depuis le parcours historique déjà associé. */
    static void resumeHistorical(android.content.Context context){
        if(!BuildConfig.PUBLIC_HISTORICAL_VIEWER_001C)return;
        try{
            if(hasHistoricalIdentity(context) && android.provider.Settings.canDrawOverlays(context)
                    && NotificationAccess.isGranted(context))
                context.startForegroundService(new Intent(context,PublicCompanionService.class));
        }catch(RuntimeException unavailable){
            android.util.Log.i("SceneVibePublic","HISTORICAL_RECEIVER_START_UNAVAILABLE");
        }
    }
    /** Démarre les transports présents et n'ouvre un code public que sur demande explicite. */
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!BuildConfig.PUBLIC_AUTONOMOUS_001B){stopSelf();return START_NOT_STICKY;}
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("public001b","SceneVibe · catalogue public",NotificationManager.IMPORTANCE_LOW));
        startForeground(1032,new Notification.Builder(this,"public001b").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("SceneVibe · télévision associée").setContentText("Réception des commentaires du catalogue").setOngoing(true).build());
        boolean pair=intent!=null && SHOW_PUBLIC_PAIRING.equals(intent.getAction());
        try{
            if(historical==null && hasHistoricalIdentity(this)){
                historical=PublicCompanionOwner.historical(this);
                historical.start();
            }
        }catch(RuntimeException refused){
            failure="Réception historique indisponible.";
        }
        try{
            if(owner==null && (!BuildConfig.PUBLIC_HISTORICAL_VIEWER_001C || pair || ownedFixture!=null
                    || PublicCompanionOwner.identityState(this).read("identity")!=null)){
                owner=ownedFixture!=null?ownedFixture:new PublicCompanionOwner(this);
                owner.start();
            }
        }catch(RuntimeException refused){
            failure="Catalogue public non configuré dans cet environnement.";
        }
        current=owner!=null?owner:historical;
        if(current==null){stopSelf();return START_NOT_STICKY;}
        libraryOwners=owner!=null && historical!=null?List.of(owner,historical):List.of(current);
        failure="";
        return START_STICKY;
    }
    /** Aucun client externe ne peut se lier au service ou récupérer son credential. */
    @Override public IBinder onBind(Intent intent){return null;}
    /** Ferme le worker avant de retirer le propriétaire observable. */
    @Override public void onDestroy(){if(owner!=null)owner.close();if(historical!=null)historical.close();if(current==owner || current==historical){current=null;libraryOwners=List.of();}super.onDestroy();}
}
