package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.app.PendingIntent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Native qualification-only foreground Activity, excluded from the signed APK.
 * It represents a viewer owned by the test environment, never Prime. Its remote
 * button sends the same immutable service PendingIntent as the notification.
 * This validates the route once invoked, not a Sony notification-menu shortcut.
 */
public final class TvUxForegroundFixtureActivity extends Activity {
    static volatile TvUxForegroundFixtureActivity current;
    volatile boolean resumed;
    volatile int backCount,accepted,transportKeys;
    /** Present only owned test controls, without video, Cloud or a background playback permit. */
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);current=this;
        if(!BuildConfig.DEBUG || !BuildConfig.TVUX_CONTROL_PANEL){finish();return;}
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(40,40,40,40);
        TextView label=new TextView(this);label.setText("ACTIVITÉ DE VISIONNAGE POSSÉDÉE PAR LE TEST — jamais Prime");label.setTextSize(24);content.addView(label);
        Button open=new Button(this);open.setText("Action notification SceneVibe");content.addView(open);
        open.setOnClickListener(view->{
            PendingIntent panel=getIntent().getParcelableExtra("panel");
            try{panel.send();}catch(PendingIntent.CanceledException canceled){throw new IllegalStateException(canceled);}
        });
        Button modal=new Button(this);modal.setText("Modale SceneVibe de test");content.addView(modal);
        modal.setOnClickListener(view->TvCompanionDialogs.builder(this).setTitle("Modale SceneVibe")
                .setMessage("Back ou × doit annuler, sans action sur le Companion.")
                .setPositiveButton("Ne pas exécuter",(dialog,which)->accepted++).setNegativeButton("Annuler",null).show());
        setContentView(content);open.requestFocus();
    }
    /** Observe that a temporary window does not switch the foreground Activity. */
    @Override public void onResume(){super.onResume();resumed=true;}
    /** A pause is recorded rather than interpreted as a streaming platform behavior. */
    @Override public void onPause(){resumed=false;super.onPause();}
    /** Count Back received after the overlay closes, without leaving the test fixture. */
    @Override public void onBackPressed(){backCount++;}
    /** Record only owned test input; the prototype must never manufacture platform transport commands. */
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY || key==KeyEvent.KEYCODE_MEDIA_PAUSE || key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || key==KeyEvent.KEYCODE_MEDIA_REWIND)transportKeys++;
        return super.onKeyDown(key,event);
    }
}
