package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Button;
import android.widget.EditText;
import android.widget.CheckBox;
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
    private Button adToggle,adSuspend,adResume,adCalibrate;
    private TextView adStatus;
    private long restoredEpoch=Long.MIN_VALUE;

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
            if(!BuildConfig.TVUX_DEVELOPER_OVERLAY){
            textToggle=button("Commentaires écrits : ON",()->{textEnabled=!TvFirst009TextServiceOwner.multimediaTextEnabled();TvFirst009TextServiceOwner.setMultimediaTextEnabled(textEnabled);refresh();});
            audioToggle=button("Commentaires audio : ON",()->{audioEnabled=!TvFirst009TextServiceOwner.multimediaAudioEnabled();TvFirst009TextServiceOwner.setMultimediaAudioEnabled(audioEnabled);refresh();});
            if(BuildConfig.TVUX_CONTROL_PANEL)button("Ouvrir le panneau de commandes",()->{
                result=TvFirst009TextServiceOwner.requestControlPanel(activity)?"Panneau demandé.":"Autorisation de superposition nécessaire.";refresh();
            });
            if(BuildConfig.ADSYNC_PROTOTYPE) {
                adToggle=button("AD-SYNC : OFF",()->{TvFirst009TextServiceOwner.setAdSyncEnabled(!TvFirst009TextServiceOwner.adSyncEnabled());refresh();});
                adSuspend=button("Suspendre les commentaires",()->{TvFirst009TextServiceOwner.suspendMultimediaComments();refresh();});
                adResume=button("Reprendre les commentaires",()->{TvFirst009TextServiceOwner.resumeMultimediaComments();refresh();});
                adCalibrate=button("Recaler sur le temps du film",this::confirmCalibration);
                adStatus=new TextView(activity);adStatus.setTextColor(0xFFFFFFFF);adStatus.setTextSize(17);root.addView(adStatus);
            }
        }
        }
        refresh();
    }

    /** Return the same launcher settings subtree, without a new Activity or application. */
    LinearLayout view() {return root;}

    /** Reflect actual source readiness and failure; retaining a file never implies Cloud authorization. */
    void refresh() {
        textEnabled=TvFirst009TextServiceOwner.multimediaTextEnabled();audioEnabled=TvFirst009TextServiceOwner.multimediaAudioEnabled();
        TvFirst008Source source=owner.get();boolean ready=source!=null && !source.busy();
        if(BuildConfig.ADSYNC_NONDESTRUCTIVE && ready && source.cacheViewEpoch()>=0 && restoredEpoch!=source.cacheViewEpoch()){
            restoredEpoch=source.cacheViewEpoch();preparedCache=null;result="Vérification du cache local existant…";
            source.restoreMultimedia(this::prepared);ready=false;
        }
        catalogue.setEnabled(ready && source.accountStage()==TvAccountSource.AccountStage.ASSOCIATED);
        restore.setEnabled(ready);
        for(int i=0;i<items.getChildCount();i++)items.getChildAt(i).setEnabled(catalogue.isEnabled());
        String state=source==null?"Association disponible dans le parcours Compte.":source.accountMessage();
        status.setText("Cloud → Android TV · multimédia expérimental\n"+result+"\n"+state
                +(BuildConfig.CLOUD_MULTIMEDIA_SERVICE?"\nAdmission locale explicite requise. "+TvFirst009TextServiceOwner.status().code:
                    "\nLe cache n'arme pas la restitution. L'identité d'édition et le mapping MEDIA restent à qualifier."));
        if(BuildConfig.ADSYNC_NONDESTRUCTIVE)status.append("\n"+TvFirst009TextServiceOwner.multimediaAvailability()
                +(TvFirst009TextServiceOwner.multimediaActive()?"\nAdmission conservée tant que consentement et reçu restent valides.":"\nNouvelle confirmation de restitution depuis le cache ; aucun retéléchargement requis."));
        if(activate!=null){
            activate.setEnabled(ready && preparedCache!=null && source.accountStage()==TvAccountSource.AccountStage.ASSOCIATED
                    && !TvFirst009TextServiceOwner.controlBusy());
            stop.setEnabled(TvFirst009TextServiceOwner.controlBusy());
            if(textToggle!=null)textToggle.setText("Commentaires écrits : "+(textEnabled?"ON":"OFF"));
            if(audioToggle!=null)audioToggle.setText("Commentaires audio : "+(audioEnabled?"ON":"OFF"));
            if(adToggle!=null) {
                boolean enabled=TvFirst009TextServiceOwner.adSyncEnabled(),running=TvFirst009TextServiceOwner.multimediaActive();
                adToggle.setText("AD-SYNC : "+(enabled?"ON · consultatif":"OFF · comportement v17"));
                adSuspend.setEnabled((enabled || BuildConfig.ADSYNC_NONDESTRUCTIVE) && running);adResume.setEnabled((enabled || BuildConfig.ADSYNC_NONDESTRUCTIVE) && running);adCalibrate.setEnabled((enabled || BuildConfig.ADSYNC_NONDESTRUCTIVE) && running);
                adStatus.setText("Aucune détection publicitaire Prime garantie. Suspendre manuellement pendant une publicité.\n"
                        +TvFirst009TextServiceOwner.adSyncStatus());
            }
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
        TvCompanionDialogs.builder(activity).setTitle("Télécharger cette track ?").setMessage(title
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
                    +(BuildConfig.ADSYNC_NONDESTRUCTIVE?"\n"+prepared.track.document.optString("title","Track")+" — cache présent : "+(total-audio)+" TEXT + "+audio+" AUDIO":"")
                    +"\nRelu après commit ; aucune restitution automatique.";
            if(BuildConfig.ADSYNC_NONDESTRUCTIVE)android.util.Log.i("SceneVibeCache","event=CACHE_RESTORED text="+(total-audio)+" audio="+audio+" events="+total);
        }
        refresh();
    }

    /** Require a fresh owner decision; UNKNOWN is never silently converted into editorial evidence. */
    private void confirmActivation(){
        if(preparedCache==null || activate==null || !activate.isEnabled())return;
        TvCompanionDialogs.builder(activity).setTitle("Admission locale TEXT + AUDIO")
                .setMessage(BuildConfig.ADSYNC_NONDESTRUCTIVE?"Active les 16 TEXT et 3 MP3 du cache pour l'édition de référence Columbo — Eaux troubles. Cette confirmation autorise le démarrage ; les allers-retours et interruptions MEDIA ne la renouvellent pas. Une véritable expiration/révocation reste bloquante. Publicité non observable : suspension manuelle disponible.":BuildConfig.ADSYNC_PROTOTYPE?"Confirme l'édition de référence de Columbo — Eaux troubles et l'activation des 16 TEXT et 3 tonalités. Pendant une publicité, suspends manuellement les commentaires ; confirme ensuite la reprise et le temps du film. La détection publicitaire automatique n'est pas prouvée.":"Confirme que tu utilises l'édition de référence de Columbo — Eaux troubles, sans publicité, et que tu autorises les 16 commentaires écrits et les 3 tonalités de qualification. Les changements d'édition ou de publicité imposent l'arrêt.")
                .setNegativeButton("Annuler",null).setPositiveButton("Je confirme · Activer",(dialog,which)->{
                    boolean started=TvFirst009TextServiceOwner.requestMultimediaStart(activity,owner.get(),preparedCache,true);
                    if(started){TvFirst009TextServiceOwner.setMultimediaTextEnabled(textEnabled);TvFirst009TextServiceOwner.setMultimediaAudioEnabled(audioEnabled);}
                    result=started?"Démarrage du service demandé ; attente d'une identité MEDIA compatible.":"Admission refusée : vérifie compte, permissions et mapping de référence.";refresh();
                }).show();
    }

    /** Confirm the film's visible time locally; no edit or seek is sent to the platform or Cloud. */
    private void confirmCalibration() {
        EditText input=new EditText(activity);input.setSingleLine(true);input.setHint("mm:ss ou hh:mm:ss");
        input.setInputType(android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_TIME);
        CheckBox replay=new CheckBox(activity);replay.setText("J'ai déplacé le film : autoriser la relecture");replay.setChecked(false);
        LinearLayout form=new LinearLayout(activity);form.setOrientation(LinearLayout.VERTICAL);form.addView(input);form.addView(replay);
        TvCompanionDialogs.builder(activity).setTitle("Temps du film")
                .setMessage("Confirme que le film a repris, puis saisis son temps actuel. Seuls les commentaires SceneVibe seront recalés (correction limitée à une heure).")
                .setView(form).setNegativeButton("Annuler",null).setPositiveButton("Confirmer le temps",(dialog,which)->{
                    try{long time=AdSyncController.parseFilmTime(input.getText().toString());
                        result=TvFirst009TextServiceOwner.calibrateMultimediaResult(time,replay.isChecked()).message;
                    }catch(IllegalArgumentException invalid){result="Saisis un temps valide mm:ss ou hh:mm:ss.";}
                    refresh();
                }).show();
    }

    /** Create one remote-focusable action using the existing Android widget toolkit. */
    private Button button(String title,Runnable action) {
        Button button=new Button(activity);button.setText(title);button.setOnClickListener(view->action.run());root.addView(button);return button;
    }
}
