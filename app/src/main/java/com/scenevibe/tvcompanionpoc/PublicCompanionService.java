package com.scenevibe.tvcompanionpoc;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.IBinder;

/**
 * Propriétaire persistant des commandes publiques après fermeture de l'Activity.
 * Notification passive et même permission FGS déjà présente ; aucun lecteur
 * tiers n'est lancé, pausé ou contrôlé. Le renderer reste OverlayService v31.
 */
public final class PublicCompanionService extends Service {
    private static volatile PublicCompanionOwner current;
    private static volatile String failure="";
    private PublicCompanionOwner owner;
    private static PublicCompanionOwner ownedFixture;
    /** Le banc utilise son seul réseau synthétique ; l'APK signé interdit cette substitution. */
    static void useOwnedFixture(PublicCompanionOwner fixture){
        if(!BuildConfig.DEBUG || !BuildConfig.PUBLIC_AUTONOMOUS_001B || !BuildConfig.CLOUD_ORIGIN.isEmpty())throw new IllegalStateException("OWNED_DEBUG_FIXTURE_ONLY");
        ownedFixture=fixture;
    }
    /** Expose seulement le code et l'état d'affichage du propriétaire public. */
    static PublicCompanionOwner current(){return current;}
    /** Retourne un échec borné si l'origine Cloud publique n'est pas configurée. */
    static String failure(){return failure;}
    /** Démarre explicitement depuis l'interface visible, puis poursuit le suivi sans confirmation TV. */
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!BuildConfig.PUBLIC_AUTONOMOUS_001B){stopSelf();return START_NOT_STICKY;}
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("public001b","SceneVibe · catalogue public",NotificationManager.IMPORTANCE_LOW));
        startForeground(1032,new Notification.Builder(this,"public001b").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("SceneVibe · télévision associée").setContentText("Réception des commentaires du catalogue").setOngoing(true).build());
        if(owner==null){try{owner=ownedFixture!=null?ownedFixture:new PublicCompanionOwner(this);current=owner;failure="";owner.start();}
            catch(RuntimeException refused){failure="Catalogue public non configuré dans cet environnement.";stopSelf();}}
        return START_STICKY;
    }
    /** Aucun client externe ne peut se lier au service ou récupérer son credential. */
    @Override public IBinder onBind(Intent intent){return null;}
    /** Ferme le worker avant de retirer le propriétaire observable. */
    @Override public void onDestroy(){if(owner!=null)owner.close();if(current==owner)current=null;super.onDestroy();}
}
