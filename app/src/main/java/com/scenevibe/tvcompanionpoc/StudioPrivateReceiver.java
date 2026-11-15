package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.scenevibe.tvcompanionpoc.presentation.TvAccountSource;

/**
 * Cycle de réception Q2 hébergé par le service existant. Un redémarrage
 * rétablit la possession et relit les octets, puis cherche une nouvelle
 * demande. Aucune de ces opérations ne lance le moteur MEDIA.
 */
final class StudioPrivateReceiver {
    final TvFirst008Source source;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable tick=this::poll;
    private boolean closed, cacheChecked;
    private MultimediaPrivateCache.Prepared prepared;
    private StudioPrivateCommand pending;
    private String message="Vérification du cache privé Q2…";

    /** Construit un seul propriétaire de la réception avec sa propre identité. */
    StudioPrivateReceiver(Context context) {
        source=TvFirst008Source.studioQ2(context);
        source.resume();
        main.post(tick);
    }

    /** Confirme l'existence locale de l'identité, sans lire ni renouveler le credential. */
    static boolean hasIdentity(Context context) {
        return !BuildConfig.STUDIO_PRIVATE_ORIGIN.isEmpty() && TvFirst008State.studioQ2(context).read("identity")!=null;
    }

    /** Ferme le polling avant de libérer les leases du transport. */
    void close() {
        closed=true;
        main.removeCallbacks(tick);
        source.close();
    }

    /** Renvoie seulement un cache encore sélectionné par le pointeur privé global. */
    MultimediaPrivateCache.Prepared prepared() {
        return prepared!=null && prepared.cacheSelected()?prepared:null;
    }

    /** Résume la progression sans confondre cache, sélection et restitution effective. */
    String status() {
        if(pending!=null && !pending.cacheConfirmed && TvFirst009TextServiceOwner.controlBusy()) {
            String active=TvFirst009TextServiceOwner.selectedTrackTitle();
            return (active.isEmpty()?"Commentaires en cours":"« "+active+" » est en cours")
                    +" · « "+pending.title+" » r"+pending.revision+(BuildConfig.MEDIA_UNIVERSAL?" · Studio Q2":"")
                    +" en attente. Arrêter les commentaires permet le téléchargement. L’activation reste manuelle.";
        }
        return message;
    }

    /** Actualise une fois le cache inerte et cadence les demandes sans réassocier la TV. */
    private void poll() {
        if(closed)return;
        main.postDelayed(tick,10_000L);
        if(source.busy())return;
        if(!cacheChecked && source.accountStage()!=TvAccountSource.AccountStage.UNASSOCIATED
                && source.accountStage()!=TvAccountSource.AccountStage.CODE) {
            cacheChecked=true;
            source.restoreMultimedia(value->{
                prepared=value;
                message=value==null?"Aucune Track Q2 dans le cache courant.":"Cache privé Q2 vérifié · "+value.track.title();
            });
            return;
        }
        if(source.accountStage()==TvAccountSource.AccountStage.ASSOCIATED) source.pollStudioPrivate(this::received);
        else if(source.accountStage()==TvAccountSource.AccountStage.ERROR)source.refresh();
    }

    /** Remet au même bridge la dernière demande et publie seulement le cache relu. */
    private void received(StudioPrivateCommand command) {
        if(closed || command.id==null){pending=null;return;}
        pending=command;
        if(command.cacheConfirmed && prepared()!=null && !command.id.equals(prepared.binding.intention)) return;
        if(command.cacheConfirmed && prepared()==null && !source.studioPendingMatches(command.id)) return;
        if(prepared()!=null && command.id.equals(prepared.binding.intention)
                && source.studioReceiptMatches(prepared.binding))return;
        if(!TvFirst009TextServiceOwner.controlBusy())message="Téléchargement et vérification · "+command.title+" r"+command.revision;
        source.receiveStudioPrivate(command,this::downloaded);
    }

    /** Conserve la référence reçue après validation et reçu, sans l'activer. */
    void downloaded(MultimediaPrivateCache.Prepared value) {
        if(value==null || closed)return;
        prepared=value;pending=null;
        message="Prête pour sélection · "+value.track.title()+" · révision "+value.profile.identity.optLong("editorialRevision");
    }
}
