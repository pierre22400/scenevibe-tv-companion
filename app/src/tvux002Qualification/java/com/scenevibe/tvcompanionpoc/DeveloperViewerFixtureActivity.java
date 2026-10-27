package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * OWN viewer in the disposable native test variant only, absent from the M5 APK.
 * This is a normal Activity destination for the same launch/onStop handoff used
 * by the public Prime launcher. It contains no video or third-party application.
 */
public final class DeveloperViewerFixtureActivity extends Activity {
    static volatile DeveloperViewerFixtureActivity current;
    volatile boolean resumed;
    volatile int backCount,remoteKeys,transportKeys;
    /** Present only owned UI, so native focus and input return can be observed. */
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);current=this;
        if(!BuildConfig.DEBUG || !BuildConfig.TVUX_DEVELOPER_OVERLAY){finish();return;}
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(40,40,40,40);
        TextView title=new TextView(this);title.setText("VISIONNAGE POSSÉDÉ PAR LE BANC — jamais Prime");title.setTextSize(24);content.addView(title);
        Button play=new Button(this);play.setText("Commandes du lecteur de test");play.setAllCaps(false);content.addView(play);
        setContentView(content);play.requestFocus();
    }
    /** Record the actual foreground lifecycle, independently of window focus. */
    @Override public void onResume(){super.onResume();resumed=true;}
    /** Losing the foreground is observable rather than inferred from a label. */
    @Override public void onPause(){resumed=false;super.onPause();}
    /** Keep this fixture alive to count Back after the service window has closed. */
    @Override public void onBackPressed(){backCount++;}
    /** Verify returned remote input and absence of manufactured transport keys. */
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        if(key==KeyEvent.KEYCODE_DPAD_DOWN || key==KeyEvent.KEYCODE_DPAD_UP)remoteKeys++;
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY || key==KeyEvent.KEYCODE_MEDIA_PAUSE || key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || key==KeyEvent.KEYCODE_MEDIA_REWIND)transportKeys++;
        return super.onKeyDown(key,event);
    }
}
