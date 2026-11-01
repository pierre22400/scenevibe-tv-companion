package com.scenevibe.timebasecaptureg1;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Voluntary D-pad preparation and scalar results. A fresh system consent is requested
 * for every attempt. No token, bitmap or arm state is saved across recreation/restart.
 */
public final class ControlActivity extends Activity {
    private CapturePolicy.Mode mode=CapturePolicy.Mode.PUBLIC;
    private EditText x,y,w,h,delay;
    private TextView result;
    private double[] pending;
    private long pendingDelay;

    /** Android constructor; the initial mode is always disarmed. */
    public ControlActivity() { }

    /** Build accessible high-contrast controls; extras select only an owned test scenario. */
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { if(getIntent().hasExtra("mode"))mode=CapturePolicy.Mode.valueOf(getIntent().getStringExtra("mode")); }
        catch(RuntimeException e) { mode=CapturePolicy.Mode.PUBLIC; }
        LinearLayout panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(40,20,40,20);
        TextView info=new TextView(this); info.setText("PRIVATE DIAGNOSTIC — NOT FOR DISTRIBUTION\nG1 : nos écrans synthétiques uniquement. État initial désarmé.\nCapture système transitoire du display entier ; seule la ROI est exploitée. Aucune image sauvegardée, aucun réseau/audio. Consentement valable pour une seule session.\nMode : "+mode);
        info.setTextSize(20); panel.addView(info);
        x=field(panel,"X normalisé", "0.05"); y=field(panel,"Y normalisé","0.10");
        w=field(panel,"Largeur normalisée","0.90"); h=field(panel,"Hauteur normalisée","0.30");
        delay=field(panel,"Délai ms (500–5000)","3000");
        button(panel,"Consentement et contrôle synthétique",()->requestConsent());
        button(panel,"Arrêt et révocation",()->CaptureService.cancel(this));
        button(panel,"Supprimer le journal local",()->{ CaptureService.cancel(this); new java.io.File(getFilesDir(),"g1.jsonl").delete(); result.setText("Suppression demandée ; aucune image stockée."); });
        for(CapturePolicy.Mode m:CapturePolicy.Mode.values())button(panel,"Choisir "+m,()->{mode=m;info.setText("PRIVATE DIAGNOSTIC — NOT FOR DISTRIBUTION\nMode "+mode+". Capture du display entier, ROI seule traitée, aucun réseau/audio/image persistante. Nouveau consentement requis.");});
        result=new TextView(this); result.setTextSize(20); panel.addView(result);
        ScrollView scroll=new ScrollView(this); scroll.addView(panel); setContentView(scroll);
    }

    /** Add a labeled numeric input; validation occurs before opening the system consent. */
    private EditText field(LinearLayout panel,String label,String value) {
        EditText f=new EditText(this); f.setContentDescription(label); f.setText(value); f.setSingleLine(true);
        f.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        panel.addView(f); return f;
    }

    /** Add one ordinary focusable button, with no global input injection. */
    private void button(LinearLayout panel,String text,Runnable action) {
        Button b=new Button(this); b.setText(text); b.setOnClickListener(v->action.run()); panel.addView(b);
    }

    /** Validate finite ROI/delay and request ordinary consent, using only APIs present on the OS. */
    private void requestConsent() {
        try {
            pending=new double[]{Double.parseDouble(x.getText().toString()),Double.parseDouble(y.getText().toString()),Double.parseDouble(w.getText().toString()),Double.parseDouble(h.getText().toString())};
            CapturePolicy.roi(pending[0],pending[1],pending[2],pending[3],1000,1000);
            pendingDelay=Long.parseLong(delay.getText().toString());
            if(pendingDelay<500||pendingDelay>5000)throw new IllegalArgumentException("DELAY_INVALID");
            MediaProjectionManager m=getSystemService(MediaProjectionManager.class);
            Intent consent=Build.VERSION.SDK_INT>=34?m.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):m.createScreenCaptureIntent();
            startActivityForResult(consent,101);
        } catch(RuntimeException e) { pending=null; result.setText("ROI_INVALID / PROJECTION_UNAVAILABLE"); }
    }

    /** Consume consent exactly once, then open only our fixture; cancellation never starts capture. */
    @Override protected void onActivityResult(int request,int code,Intent data) {
        super.onActivityResult(request,code,data);
        if(request!=101)return;
        if(code!=RESULT_OK||data==null||pending==null) { CaptureService.lastResult="CONSENT_DENIED"; result.setText("CONSENT_DENIED"); pending=null; return; }
        Intent capture=new Intent(this,CaptureService.class).putExtra("consent",data).putExtra("code",code)
            .putExtra("mode",mode.name()).putExtra("roi",pending).putExtra("delay",pendingDelay);
        pending=null;
        startForegroundService(capture);
        startActivity(new Intent(this,FixtureActivity.class).putExtra("mode",mode.name()));
    }

    /** Display scalar results only, never a surviving screenshot or old permission. */
    @Override protected void onResume() { super.onResume(); if(result!=null)result.setText(CaptureService.lastResult); }
}
