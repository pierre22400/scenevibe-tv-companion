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
    private final Runnable refresh=this::refresh;
    /** Présente le code et démarre le suivi public depuis une Activity visible. */
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        if(!BuildConfig.PUBLIC_AUTONOMOUS_001B){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);
        root.setPadding(48,32,48,32);root.setBackgroundColor(0xFFFBFAF6);
        TextView title=new TextView(this);title.setText("Associer ma TV au catalogue SceneVibe");title.setTextColor(0xFF25211B);title.setTextSize(28);root.addView(title);
        code=new TextView(this);code.setTag("public-tv-code");code.setTextColor(0xFF25211B);code.setTextSize(64);code.setGravity(Gravity.CENTER);root.addView(code);
        status=new TextView(this);status.setTag("public-tv-status");status.setTextSize(20);status.setTextColor(0xFF25211B);root.addView(status);
        if(!Settings.canDrawOverlays(this))button(root,"Réglage Android : superposition",()->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
        if(!NotificationAccess.isGranted(this))button(root,"Réglage Android : accès MEDIA",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        button(root,"Retour au Companion",this::finish);setContentView(root);
        startForegroundService(new Intent(this,PublicCompanionService.class));
    }
    /** Un contrôle D-pad ordinaire, sans dialogue d'autorisation applicative. */
    private void button(LinearLayout root,String label,Runnable action){Button button=new Button(this);button.setText(label);button.setOnClickListener(v->action.run());root.addView(button);}
    /** Affiche le seul code public et sa durée, jamais le secret de challenge. */
    private void refresh(){
        PublicCompanionOwner owner=PublicCompanionService.current();
        if(owner==null){code.setText("—");status.setText(PublicCompanionService.failure().isEmpty()?"Connexion au catalogue…":PublicCompanionService.failure());}
        else{
            code.setText(owner.code().isEmpty()?"TV associée":owner.code());
            long seconds=Math.max(0,(owner.codeExpires()-System.currentTimeMillis())/1000);
            status.setText(owner.status()+(owner.code().isEmpty()?"":"\nCode valable encore "+seconds+" s"));
        }
        main.postDelayed(refresh,500);
    }
    /** Reprend uniquement l'affichage ; l'association et le worker appartiennent au service. */
    @Override protected void onResume(){super.onResume();main.post(refresh);}
    /** Retire les callbacks UI sans arrêter la réception ou les commentaires. */
    @Override protected void onPause(){main.removeCallbacks(refresh);super.onPause();}
}
