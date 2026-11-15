package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.scenevibe.tvcompanionpoc.presentation.TvAccountSource;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Bibliothèque Q2 dans Mes Tracks, avec actions D-pad dans l'Activity existante.
 * Le service possède l'association et le cache. L'activation est un choix local
 * qui exige encore les permissions et la reconnaissance MEDIA.
 */
final class StudioPrivateControls {
    private final Activity activity;
    private final Runnable targetsChanged;
    private final LinearLayout root, tracks;
    private final TextView status;
    private final Button pair, cancel, list, activate, stop, text, audio;
    private String notice="";
    private boolean pairPending;

    /** Construit la vue sans ouvrir de session Cloud ou de code TV. */
    StudioPrivateControls(Activity activity,Runnable targetsChanged) {
        this.activity=activity;this.targetsChanged=targetsChanged;
        root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setTag("scenevibe-private-q2");
        status=new TextView(activity);root.addView(status);
        pair=button(root,"Obtenir un code Studio Q2",this::pair);
        cancel=button(root,"Annuler le code Studio Q2",()->{
            StudioPrivateReceiver receiver=PublicCompanionService.studio();
            if(receiver!=null)receiver.source.cancelPairing();
        });
        list=button(root,"Charger mes Tracks privées Q2",this::list);
        tracks=new LinearLayout(activity);tracks.setOrientation(LinearLayout.VERTICAL);root.addView(tracks);
        activate=button(root,"Activer les commentaires de la Track Q2 en cache",this::activate);
        stop=button(root,"Arrêter la restitution SceneVibe",()->TvFirst009TextServiceOwner.requestStop(activity));
        text=button(root,"Commentaires écrits",()->TvFirst009TextServiceOwner.setMultimediaTextEnabled(!TvFirst009TextServiceOwner.multimediaTextEnabled()));
        audio=button(root,"Commentaires audio",()->TvFirst009TextServiceOwner.setMultimediaAudioEnabled(!TvFirst009TextServiceOwner.multimediaAudioEnabled()));
    }

    /** Rattache le même groupe au panneau Mes Tracks du shell natif. */
    LinearLayout view(){return root;}

    /** Affiche le code et les preuves du récepteur sans réinitialiser d'association. */
    void refresh() {
        StudioPrivateReceiver receiver=PublicCompanionService.studio();
        TvFirst008Source source=receiver==null?null:receiver.source;
        boolean busy=source!=null && source.busy();
        TvAccountSource.AccountStage stage=source==null?TvAccountSource.AccountStage.UNASSOCIATED:source.accountStage();
        boolean associated=stage==TvAccountSource.AccountStage.ASSOCIATED;
        if(pairPending && source!=null && !busy) {
            pairPending=false;
            if(!associated && stage!=TvAccountSource.AccountStage.CODE)source.beginPairing(false);
        }
        pair.setEnabled(!pairPending && !busy && !associated && stage!=TvAccountSource.AccountStage.CODE);
        cancel.setEnabled(!busy && source!=null && !associated);
        list.setEnabled(associated && !busy);
        activate.setEnabled(associated && !busy && receiver!=null && receiver.prepared()!=null && !TvFirst009TextServiceOwner.controlBusy());
        stop.setEnabled(TvFirst009TextServiceOwner.controlBusy());
        text.setText("Commentaires écrits : "+(TvFirst009TextServiceOwner.multimediaTextEnabled()?"ON":"OFF"));
        audio.setText("Commentaires audio : "+(TvFirst009TextServiceOwner.multimediaAudioEnabled()?"ON":"OFF"));
        String state=source==null?"Associe cette TV avec un code depuis le Studio Q2.":source.accountMessage()+"\n"+source.verification();
        status.setText((BuildConfig.MEDIA_UNIVERSAL?"Studio Q2 · "+BuildConfig.STUDIO_PRIVATE_ORIGIN+"/studio\n":"")
                +state+(receiver==null?"":"\n"+receiver.status())+(notice.isEmpty()?"":"\n"+notice));
        if(TvFirst009TextServiceOwner.multimediaActive()) status.append("\n"+TvFirst009TextServiceOwner.multimediaAvailability());
    }

    /** Démarre le service sur action et génère au plus un code TV Q2. */
    private void pair() {
        pairPending=true;notice="";
        PublicCompanionService.resumeStudio(activity,true);
        refresh();
    }

    /** Charge uniquement les Tracks déjà autorisées pour ce grant. */
    private void list() {
        StudioPrivateReceiver receiver=PublicCompanionService.studio();
        if(receiver==null)return;
        notice="Lecture du Cloud privé…";
        receiver.source.listMultimedia(reply->showList(receiver,reply));
    }

    /** Contrôle chaque carte avant d'afficher son état local et les métadonnées du serveur. */
    private void showList(StudioPrivateReceiver receiver,JSONObject reply) {
        try {
            MultimediaDeliveryProfile.closed(reply,"items");
            JSONArray rows=reply.getJSONArray("items");
            MultimediaDeliveryProfile.require(rows.length()<=100,"Q2_LIBRARY_BUDGET");
            tracks.removeAllViews();
            for(int index=0;index<rows.length();index++) {
                JSONObject row=rows.getJSONObject(index);
                MultimediaDeliveryProfile.closed(row,"publicationId","trackId","editorialRevision","packageSha256","title","work","platform",
                        "season","episode","edition","creator","createdAt","revisedAt","textCount","audioCount");
                String publication=TvFirst008Wire.uuid(row.getString("publicationId"));
                MultimediaDeliveryProfile.identifier(row.get("trackId"));
                MultimediaDeliveryProfile.digest(row.get("packageSha256"));
                MultimediaDeliveryProfile.integer(row,"editorialRevision",1,2_147_483_647L);
                String title=row.getString("title");
                MultimediaPrivateCache.Prepared cached=receiver.prepared();
                boolean installed=cached!=null && publication.equals(cached.binding.publication);
                button(tracks,description(row)+(installed?"\nCache vérifié · prête pour sélection":"\nDisponible dans le Cloud"),
                        ()->{if(installed)activate();else confirmDownload(receiver,publication,title,row.optLong("editorialRevision"));});
            }
            notice=rows.length()==0?"Aucune Track Q2 distribuable pour cette TV.":"";
        }catch(Exception invalid){tracks.removeAllViews();notice="Bibliothèque privée Q2 indisponible.";}
        targetsChanged.run();refresh();
    }

    /** Affiche uniquement des métadonnées d'œuvre attestées par la projection privée. */
    private String description(JSONObject row) {
        String episode=row.isNull("season")||row.isNull("episode")?"":" · S"+row.optInt("season")+" E"+row.optInt("episode");
        String platform="prime_video".equals(row.optString("platform"))?"Prime Video":row.optString("platform","Plateforme indisponible");
        DateTimeFormatter calendar=DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm",Locale.FRANCE).withZone(ZoneId.systemDefault());
        return row.optString("title")+" · révision "+row.optLong("editorialRevision")+"\n"+row.optString("work")+" · "+platform+episode
                +" · "+row.optString("edition","Édition indisponible")+" · "+row.optString("creator","Créateur indisponible")
                +"\nCréée le "+calendar.format(Instant.parse(row.optString("createdAt")))+" · révision du "+calendar.format(Instant.parse(row.optString("revisedAt")))
                +"\n"+row.optInt("textCount")+" TEXT · "+row.optInt("audioCount")+" AUDIO";
    }

    /** Garde les commentaires actifs et décrit le choix avant une sélection manuelle. */
    private void confirmDownload(StudioPrivateReceiver receiver,String publication,String title,long revision) {
        if(TvFirst009TextServiceOwner.controlBusy()) {
            notice="« "+TvFirst009TextServiceOwner.selectedTrackTitle()+" » est active. Arrête ses commentaires avant de sélectionner « "+title+" » r"+revision+".";
            refresh();return;
        }
        TvCompanionDialogs.builder(activity).setTitle("Télécharger la révision privée")
                .setMessage("« "+title+" » r"+revision+" remplacera le cache privé courant après vérification.")
                .setNegativeButton("Annuler",null).setPositiveButton("Télécharger",(dialog,which)->{
                    notice="Téléchargement de « "+title+" » r"+revision;
                    receiver.source.downloadMultimedia(publication,value->{
                        receiver.downloaded(value);
                        notice=value==null?"Sélection différée pendant la session MEDIA.":"";
                        list();
                    });
                }).show();
    }

    /** Demande un nouveau consentement local pour cette édition et ce cache exacts. */
    private void activate() {
        StudioPrivateReceiver receiver=PublicCompanionService.studio();
        MultimediaPrivateCache.Prepared prepared=receiver==null?null:receiver.prepared();
        if(prepared==null || TvFirst009TextServiceOwner.controlBusy())return;
        TvCompanionDialogs.builder(activity).setTitle("Activer les commentaires Q2")
                .setMessage("Confirme l’édition de « "+prepared.track.title()+" » · révision "+prepared.profile.identity.optLong("editorialRevision")
                        +(BuildConfig.MEDIA_UNIVERSAL?". La restitution attend une position valide fournie par Prime Video.":
                        ". La restitution attend la reconnaissance de l’œuvre par Android TV."))
                .setNegativeButton("Annuler",null).setPositiveButton("Activer",(dialog,which)->{
                    boolean accepted=TvFirst009TextServiceOwner.requestMultimediaStart(activity,receiver.source,prepared,true);
                    notice=accepted?(BuildConfig.MEDIA_UNIVERSAL?"Track sélectionnée · attente de l’horloge Prime Video.":"Commentaires activés · attente du média compatible."):
                            "Vérifie que le cache, les permissions et l’association sont valides.";
                    refresh();
                }).show();
    }

    /** Crée un bouton natif focusable au rendu lisible sur la bibliothèque TV. */
    private Button button(LinearLayout parent,String label,Runnable action) {
        Button button=new Button(activity);button.setText(label);button.setAllCaps(false);button.setFocusable(true);button.setMaxLines(5);
        button.setTextColor(ColorStateList.valueOf(activity.getColor(R.color.scenevibe_foreground)));
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},background(R.color.scenevibe_public_action_border));
        states.addState(new int[]{},background(R.color.scenevibe_card));
        button.setBackground(states);button.setPadding(18,10,18,10);
        button.setOnClickListener(view->action.run());parent.addView(button);return button;
    }

    /** Rend le focus visible sans dépendre de la couleur seule. */
    private GradientDrawable background(int stroke) {
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(activity.getColor(R.color.scenevibe_card));shape.setCornerRadius(8);shape.setStroke(3,activity.getColor(stroke));
        return shape;
    }
}
