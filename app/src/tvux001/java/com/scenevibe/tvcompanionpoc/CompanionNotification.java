package com.scenevibe.tvcompanionpoc;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import androidx.core.app.NotificationCompat;

/**
 * Public AndroidX TV notification adapter included only in v19.
 * Keeping its dependency variant-scoped preserves the merged permissions and
 * closed runtime boundaries of the previously qualified cache-only profiles.
 */
final class CompanionNotification {
    /** Offer a service PendingIntent, never an Activity launch or a platform media action. */
    static Notification create(Context context,String channel,PendingIntent panel,PendingIntent stop) {
        return new NotificationCompat.Builder(context,channel).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("SceneVibe · commandes du Companion")
                .setContentText("Ouvrir le panneau sans lancer l’application · disponibilité selon la TV")
                .setContentIntent(panel).setOngoing(true).setSilent(true)
                .addAction(0,"Ouvrir le panneau",panel).addAction(0,"Arrêter les commentaires",stop)
                .extend(new NotificationCompat.TvExtender().setContentIntent(panel).setSuppressShowOverApps(true)).build();
    }
    /** This variant adapter owns no service or commentary state. */
    private CompanionNotification() {}
}
