package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.scenevibe.tvcompanionpoc.presentation.TvAccountSource;
import java.util.function.Supplier;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Raccorde le transport multimédia à l'écran Réglages du Companion existant.
 * Le compte, le worker, le credential chiffré et le cache appartiennent toujours
 * à TV-FIRST. Une sélection demande confirmation et ne confère aucun permis
 * de restitution. Les profils historiques ne construisent jamais ces contrôles.
 */
final class CloudMultimediaControls {
    private final Activity activity;
    private final Supplier<TvFirst008Source> owner;
    private final LinearLayout root,items;
    private final TextView status;
    private final Button catalogue,restore;
    private String result="Aucun paquet multimédia vérifié dans cette session.";
    private MultimediaPrivateCache.Prepared preparedCache;
    private Button activate,stop,textToggle,audioToggle;
    private boolean textEnabled=true,audioEnabled=true;

    /** Attach only view controls; constructing the screen never performs network I/O or starts playback. */
    CloudMultimediaControls(Activity activity,Supplier<TvFirst008Source> owner) {
        this.activity=activity;this.owner=owner;
        root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
        status=new TextView(activity);status.setTextColor(0xFFFFFFFF);status.setTextSize(17);root.addView(status);
        catalogue=button("Charger le catalogue multimédia privé",this::catalogue);
        restore=button("Relire le cache multimédia local",this::restore);
        items=new LinearLayout(activity);items.setOrientation(LinearLayout.VERTICAL);root.addView(items);
        if(BuildConfig.CLOUD_MULTIMEDIA_SERVICE){
            activate=button("Activer TEXT + AUDIO depuis le cache",this::confirmActivation);
            stop=button("Arrêter les commentaires",()->{TvFirst009TextServiceOwner.requestStop(activity);refresh();});
            textToggle=button("Commentaires écrits : ON",()->{textEnabled=!textEnabled;TvFirst009TextServiceOwner.setMultimediaTextEnabled(textEnabled);refresh();});
            audioToggle=button("Commentaires audio : ON",()->{audioEnabled=!audioEnabled;TvFirst009TextServiceOwner.setMultimediaAudioEnabled(audioEnabled);refresh();});
        }
        refresh();
    }

    /** Return the same launcher settings subtree, without a new Activity or application. */
    LinearLayout view() {return root;}

    /** Reflect actual source readiness and failure; retaining a file never implies Cloud authorization. */
    void refresh() {
        TvFirst008Source source=owner.get();boolean ready=source!=null && !source.busy();
        catalogue.setEnabled(ready && source.accountStage()==TvAccountSource.AccountStage.ASSOCIATED);
        restore.setEnabled(ready);
        for(int i=0;i<items.getChildCount();i++)items.getChildAt(i).setEnabled(catalogue.isEnabled());
        String state=source==null?"Association disponible dans le parcours Compte.":source.accountMessage();
        status.setText("Cloud → Android TV · multimédia expérimental\n"+result+"\n"+state
                +(BuildConfig.CLOUD_MULTIMEDIA_SERVICE?"\nAdmission locale explicite requise. "+TvFirst009TextServiceOwner.status().code:
                    "\nLe cache n'arme pas la restitution. L'identité d'édition et le mapping MEDIA restent à qualifier."));
        if(activate!=null){
            activate.setEnabled(ready && preparedCache!=null && source.accountStage()==TvAccountSource.AccountStage.ASSOCIATED
                    && !TvFirst009TextServiceOwner.controlBusy());
            stop.setEnabled(TvFirst009TextServiceOwner.controlBusy());
            textToggle.setText("Commentaires écrits : "+(textEnabled?"ON":"OFF"));
            audioToggle.setText("Commentaires audio : "+(audioEnabled?"ON":"OFF"));
        }
    }

    /** Ask the current account/device authority for its closed, private multimedia catalogue. */
    private void catalogue() {
        TvFirst008Source source=owner.get();if(source==null || !catalogue.isEnabled())return;
        result="Chargement du catalogue privé…";
        source.listMultimedia(this::listed);refresh();
    }

    /** Validate the discovery envelope before offering any explicit selection action. */
    private void listed(JSONObject reply) {
        items.removeAllViews();
        try {
            MultimediaDeliveryProfile.closed(reply,"profile","items");
            MultimediaDeliveryProfile.require(MultimediaDeliveryProfile.PROFILE.equals(reply.getString("profile")),"CATALOGUE_PROFILE");
            JSONArray rows=reply.getJSONArray("items");
            MultimediaDeliveryProfile.require(rows.length()<=100,"CATALOGUE_BUDGET");
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);
                MultimediaDeliveryProfile.closed(row,"publicationId","trackId","editorialRevision","title","packageSha256");
                String publication=TvFirst008Wire.uuid(row.getString("publicationId")),title=row.getString("title");
                MultimediaDeliveryProfile.identifier(row.get("trackId"));MultimediaDeliveryProfile.digest(row.get("packageSha256"));
                MultimediaDeliveryProfile.integer(row,"editorialRevision",1,9_007_199_254_740_991L);
                MultimediaDeliveryProfile.require(!title.trim().isEmpty() && title.length()<=500,"CATALOGUE_TITLE");
                Button select=new Button(activity);select.setText("Télécharger : "+title);
                select.setOnClickListener(view->confirm(publication,title));items.addView(select);
            }
            result=rows.length()+" publication(s) privée(s) disponible(s).";
        }catch(Exception refused){items.removeAllViews();result="Catalogue refusé : contrat multimédia invalide.";}
        refresh();
    }

    /** Separate catalogue discovery from the user's confirmed private assignment and download. */
    private void confirm(String publication,String title) {
        new AlertDialog.Builder(activity).setTitle("Télécharger cette track ?").setMessage(title
                +"\nLes fichiers seront vérifiés et conservés dans le cache privé de SceneVibe.")
                .setNegativeButton("Annuler",null).setPositiveButton("Télécharger",(dialog,which)->download(publication)).show();
    }

    /** Reuse the Activity source's serial worker and durable intention after a confirmed D-pad action. */
    private void download(String publication) {
        TvFirst008Source source=owner.get();if(source==null || source.busy())return;
        result="Téléchargement et vérification en cours…";
        source.downloadMultimedia(publication,this::prepared);refresh();
    }

    /** Read verified local bytes after reopening the app; never make an automatic network or playback request. */
    private void restore() {
        TvFirst008Source source=owner.get();if(source==null || source.busy())return;
        result="Relecture du cache privé…";source.restoreMultimedia(this::prepared);refresh();
    }

    /** Display counts taken from the verified shared calendar rather than assuming Columbo or nineteen events. */
    private void prepared(MultimediaPrivateCache.Prepared prepared) {
        preparedCache=prepared;
        if(prepared==null)result="Aucun cache multimédia local pour cette association.";
        else {
            int audio=prepared.track.audio.size(),total=prepared.track.calendar.events().size();
            result="Cache privé vérifié : "+(total-audio)+" TEXT + "+audio+" AUDIO · "+total+" événements."
                    +"\nRelu après commit ; aucune restitution automatique.";
        }
        refresh();
    }

    /** Require a fresh owner decision; UNKNOWN is never silently converted into editorial evidence. */
    private void confirmActivation(){
        if(preparedCache==null || activate==null || !activate.isEnabled())return;
        new AlertDialog.Builder(activity).setTitle("Admission locale TEXT + AUDIO")
                .setMessage("Confirme que tu utilises l'édition de référence de Columbo — Eaux troubles, sans publicité, et que tu autorises les 16 commentaires écrits et les 3 tonalités de qualification. Les changements d'édition ou de publicité imposent l'arrêt.")
                .setNegativeButton("Annuler",null).setPositiveButton("Je confirme · Activer",(dialog,which)->{
                    boolean started=TvFirst009TextServiceOwner.requestMultimediaStart(activity,owner.get(),preparedCache,true);
                    if(started){TvFirst009TextServiceOwner.setMultimediaTextEnabled(textEnabled);TvFirst009TextServiceOwner.setMultimediaAudioEnabled(audioEnabled);}
                    result=started?"Démarrage du service demandé ; attente d'une identité MEDIA compatible.":"Admission refusée : vérifie compte, permissions et mapping de référence.";refresh();
                }).show();
    }

    /** Create one remote-focusable action using the existing Android widget toolkit. */
    private Button button(String title,Runnable action) {
        Button button=new Button(activity);button.setText(title);button.setOnClickListener(view->action.run());root.addView(button);return button;
    }
}
