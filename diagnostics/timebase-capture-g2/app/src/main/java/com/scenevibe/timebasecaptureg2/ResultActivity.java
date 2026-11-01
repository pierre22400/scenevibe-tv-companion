package com.scenevibe.timebasecaptureg2;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;

/**
 * Voluntary local inspection after projection closure. A secure window prevents recapture
 * of the ROI. The bitmap and backing array are wiped on expiry, manual erase or departure.
 * Labels require an explicit human comparison and remain simulated in owned controls.
 */
public final class ResultActivity extends Activity {
    private Bitmap bitmap;
    private ImageView image;
    private TextView status;
    private CheckBox comparable;
    private final Handler expiry=new Handler();

    /** Create no image until current generation and resource closure have been verified. */
    public ResultActivity() { }

    /** Render only an unexpired ROI; never restore pixels from Activity saved state. */
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        status=new TextView(this);status.setTextSize(20);status.setText(CaptureService.lastResult+" — constat humain uniquement, pas d'OCR.");panel.addView(status);
        image=new ImageView(this);image.setAdjustViewBounds(true);image.setMaxHeight((int)(300*getResources().getDisplayMetrics().density));panel.addView(image,new LinearLayout.LayoutParams(-1,-2));
        comparable=new CheckBox(this);comparable.setText("J'ai comparé la ROI et l'horloge physiquement visible du même contexte ; cadrage et instant comparables.");panel.addView(comparable);
        for(String label:new String[]{"A","B","C","INCONCLUSIVE"}) {
            Button b=new Button(this);b.setText("Constat humain "+label);b.setOnClickListener(v->status.setText(SessionMemory.label(this,label,comparable.isChecked())?"Constat enregistré (humain).":"Constat refusé : comparaison, ROI ou fermeture insuffisante."));panel.addView(b);
        }
        Button erase=new Button(this);erase.setText("Effacer et fermer");erase.setOnClickListener(v->{wipe();finish();});panel.addView(erase);ScrollView scroll=new ScrollView(this);scroll.addView(panel);setContentView(scroll);
        if(CaptureService.active){status.setText("Inspection refusée : projection non fermée.");return;}
        synchronized(SessionMemory.roi) {
            int[] pixels=SessionMemory.roi.get(SessionMemory.policy.generation,SystemClock.elapsedRealtime());
            if(pixels==null){status.setText("ROI indisponible ou expirée. A/B impossibles.");return;}
            bitmap=Bitmap.createBitmap(SessionMemory.roi.width,SessionMemory.roi.height,Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels,0,SessionMemory.roi.width,0,0,SessionMemory.roi.width,SessionMemory.roi.height);image.setImageBitmap(bitmap);
            expiry.postDelayed(()->{wipe();status.setText("ROI expirée et effacée.");},Math.max(0,SessionMemory.roi.expiresAt-SystemClock.elapsedRealtime()));
        }
    }

    /** Clear visible and backing pixels before dropping references; no bitmap is persisted. */
    private void wipe() {expiry.removeCallbacksAndMessages(null);if(image!=null)image.setImageDrawable(null);if(bitmap!=null){bitmap.eraseColor(0);bitmap.recycle();bitmap=null;}SessionMemory.roi.clear();}

    /** Losing foreground erases pixels before transition animations or a retained stopped instance. */
    @Override protected void onPause() {wipe();super.onPause();}

    /** Leaving inspection erases it even when Android retains the stopped Activity instance. */
    @Override protected void onStop() {wipe();super.onStop();}

    /** Idempotently erase again during teardown; no image survives configuration recreation. */
    @Override protected void onDestroy() {wipe();super.onDestroy();}
}
