package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;

/**
 * Code temporaire lisible à distance. Sa saisie Web est l'unique association.
 * Aucun bouton d'approbation, confirmation Track ou connexion humaine n'existe
 * ici. Seuls les réglages système obligatoires peuvent être proposés si absents.
 */
public final class PublicPairingActivity extends Activity {
    private final Handler main=new Handler(android.os.Looper.getMainLooper());
    private TextView code,status;
    private Button text,audio;
    private final Runnable refresh=this::refresh;
    /** Présente le code et démarre le suivi public depuis une Activity visible. */
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        if(!BuildConfig.PUBLIC_AUTONOMOUS_001B){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);
        root.setPadding(48,32,48,32);root.setBackgroundColor(0xFFFBFAF6);
        TextView title=new TextView(this);title.setText("Associer ma TV au catalogue SceneVibe");title.setTextColor(0xFF25211B);title.setTextSize(28);root.addView(title);
        if (BuildConfig.PUBLIC_VIEWER_001 && !BuildConfig.PUBLIC_VIEWER_ORIGIN.isEmpty()) {
            TextView address = new TextView(this);
            address.setText("Ouvre " + BuildConfig.PUBLIC_VIEWER_ORIGIN + " sur ton téléphone ou ton ordinateur.");
            address.setTextColor(0xFF25211B);
            address.setTextSize(18);
            root.addView(address);
        }
        code=new TextView(this);code.setTag("public-tv-code");code.setTextColor(0xFF25211B);code.setTextSize(64);code.setGravity(Gravity.CENTER);root.addView(code);
        status=new TextView(this);status.setTag("public-tv-status");status.setTextSize(20);status.setTextColor(0xFF25211B);root.addView(status);
        if(!Settings.canDrawOverlays(this))button(root,"Réglage Android : superposition",()->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
        if(!NotificationAccess.isGranted(this))button(root,"Réglage Android : accès MEDIA",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        if(BuildConfig.PUBLIC_VIEWER_001){
            text=button(root,"TEXT",()->toggle(true));
            audio=button(root,"AUDIO",()->toggle(false));
            text.setEnabled(false);audio.setEnabled(false);
        }
        button(root,"Retour au Companion",this::finish);setContentView(root);
        startForegroundService(new Intent(this,PublicCompanionService.class));
    }
    /** Un contrôle D-pad ordinaire, sans dialogue d'autorisation applicative. */
    private Button button(LinearLayout root,String label,Runnable action){Button button=new Button(this);button.setText(label);button.setOnClickListener(v->action.run());root.addView(button);return button;}
    /** Modifie un seul canal public et reflète la modification dans l'overlay actif. */
    private void toggle(boolean written){
        PublicCompanionOwner owner=PublicCompanionService.current();
        PublicViewerPreferences preferences=owner==null?null:owner.viewerPreferences();
        if(preferences==null)return;
        boolean saved=written?preferences.setText(!preferences.text()):preferences.setAudio(!preferences.audio());
        if(saved)TvFirst009TextServiceOwner.applyViewerPreferences();
    }
    /** Affiche le seul code public et sa durée, jamais le secret de challenge. */
    private void refresh(){
        PublicCompanionOwner owner=PublicCompanionService.current();
        if(owner==null){code.setText("—");status.setText(PublicCompanionService.failure().isEmpty()?"Connexion au catalogue…":PublicCompanionService.failure());}
        else{
            code.setText(owner.code().isEmpty()?"TV associée":owner.code());
            long seconds=Math.max(0,(owner.codeExpires()-System.currentTimeMillis())/1000);
            status.setText(owner.status()+(owner.code().isEmpty()?"":"\nCode valable encore "+seconds+" s"));
            if(text!=null){
                PublicViewerPreferences preferences=owner.viewerPreferences();
                boolean ready=preferences!=null && preferences.initialized();
                text.setEnabled(ready);audio.setEnabled(ready);
                if(ready){
                    text.setText("Commentaires écrits · "+(preferences.text()?"ON":"OFF"));
                    audio.setText("Commentaires audio · "+(preferences.audio()?"ON":"OFF"));
                }
            }
        }
        main.postDelayed(refresh,500);
    }
    /** Reprend uniquement l'affichage ; l'association et le worker appartiennent au service. */
    @Override protected void onResume(){super.onResume();main.post(refresh);}
    /** Retire les callbacks UI sans arrêter la réception ou les commentaires. */
    @Override protected void onPause(){main.removeCallbacks(refresh);super.onPause();}
}
