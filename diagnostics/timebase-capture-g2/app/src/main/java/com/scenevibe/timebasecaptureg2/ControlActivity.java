package com.scenevibe.timebasecaptureg2;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Voluntary TV preparation, always initially disarmed. Consent reserves one volatile
 * generation; leaving preparation is necessary before capture. Nothing launches a
 * third-party app or the inspection Activity automatically. No saved token or pixels.
 */
public final class ControlActivity extends Activity {
    private CapturePolicy.Mode mode=CapturePolicy.Mode.PUBLIC;
    private EditText x,y,w,h,delay;
    private CheckBox futureContext;
    private TextView result;
    private double[] pending;
    private long pendingDelay,pendingGeneration;

    /** Construct disarmed preparation without restoring Android permission data. */
    public ControlActivity() { }

    /** Present scope, normalized ROI, bounded delay and focusable voluntary controls. */
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(40,20,40,20);
        TextView info=new TextView(this);info.setTextSize(20);
        info.setText("PRIVATE DIAGNOSTIC — NOT FOR DISTRIBUTION\nG2 préparation. Initialement désarmé. Aucun essai Sony/Netflix autorisé par cette livraison.\nCapture système du display entier transitoire ; seule la ROI est copiée. Aucune image sauvegardée, réseau ou audio. Inspection volontaire après fermeture, effacement à 30 secondes. Chaque tentative exige un nouveau consentement.");panel.addView(info);
        x=field(panel,"X normalisé","0.05");y=field(panel,"Y normalisé","0.10");w=field(panel,"Largeur normalisée","0.90");h=field(panel,"Hauteur normalisée","0.30");
        delay=field(panel,"Délai ms (500–60000)","3000");
        futureContext=new CheckBox(this);futureContext.setText("Contexte externe futur : GO physique distinct et admissibilité favorable déjà obtenus (déclaration humaine)");panel.addView(futureContext);
        button(panel,"Consentement et armement unique",()->requestConsent());
        button(panel,"Afficher notre faux lecteur",()->{if(!CaptureService.active){result.setText("Arme et accepte d'abord.");return;}startActivity(new Intent(this,FixtureActivity.class).putExtra("mode",mode.name()));});
        for(CapturePolicy.Mode m:CapturePolicy.Mode.values())button(panel,"Choisir "+m,()->{if(!SessionMemory.policy.active()){mode=m;result.setText("Contrôle possédé : "+m);} });
        button(panel,"Inspecter volontairement la ROI",()->{if(CaptureService.active){result.setText("Attends la fermeture de la projection.");return;}startActivity(new Intent(this,ResultActivity.class));});
        button(panel,"Arrêter et effacer",()->{pending=null;SessionMemory.cancel(this);result.setText("Arrêt demandé, ROI effacée.");});
        button(panel,"Supprimer le journal local",()->deleteJournal());
        result=new TextView(this);result.setTextSize(20);panel.addView(result);
        ScrollView scroll=new ScrollView(this);scroll.addView(panel);setContentView(scroll);
    }

    /** Delete technical records only after cleanup; never race the final journal append. */
    private void deleteJournal() {
        if(CaptureService.active){SessionMemory.cancel(this);result.setText("Fermeture en cours ; réessaie explicitement.");return;}
        java.io.File f=new java.io.File(getFilesDir(),"g2.jsonl");result.setText(!f.exists()||f.delete()?"Journal supprimé.":"Suppression impossible.");
    }

    /** Add one labeled numeric field; no arbitrary media or user text is collected. */
    private EditText field(LinearLayout panel,String label,String value) {
        EditText f=new EditText(this);f.setContentDescription(label);f.setText(value);f.setSingleLine(true);
        f.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);panel.addView(f);return f;
    }

    /** Add an ordinary D-pad focusable button with an explicit local action. */
    private void button(LinearLayout panel,String title,Runnable action) {Button b=new Button(this);b.setText(title);b.setOnClickListener(v->action.run());panel.addView(b);}

    /** Reserve before the system dialog, rejecting overlap and erasing any previous ROI. */
    private void requestConsent() {
        if(SessionMemory.policy.active()||CaptureService.active){result.setText("Une tentative est déjà active.");return;}
        try {
            pending=new double[]{Double.parseDouble(x.getText().toString()),Double.parseDouble(y.getText().toString()),Double.parseDouble(w.getText().toString()),Double.parseDouble(h.getText().toString())};
            CapturePolicy.roi(pending[0],pending[1],pending[2],pending[3],1000,1000);
            pendingDelay=Long.parseLong(delay.getText().toString());SessionMemory.roi.clear();
            pendingGeneration=SessionMemory.policy.arm(SystemClock.elapsedRealtime(),pendingDelay);
            SessionMemory.synthetic=!futureContext.isChecked();CaptureService.lastProof=null;CaptureService.lastResult="CONSENT_PENDING";
            MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
            Intent c=Build.VERSION.SDK_INT>=34?manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):manager.createScreenCaptureIntent();
            startActivityForResult(c,101);
        } catch(RuntimeException e){pending=null;SessionMemory.policy.close();LocalJournal.rejected(this,"ROI_INVALID_OR_PROJECTION_UNAVAILABLE",mode);result.setText(CaptureService.lastResult);}
    }

    /** Consume exactly one current consent, then wait for voluntary departure without launching a target. */
    @Override protected void onActivityResult(int request,int code,Intent data) {
        super.onActivityResult(request,code,data);if(request!=101)return;
        if(code!=RESULT_OK||data==null||pending==null||!SessionMemory.policy.consent(pendingGeneration,SystemClock.elapsedRealtime())) {
            pending=null;SessionMemory.policy.close();LocalJournal.rejected(this,code!=RESULT_OK?"CONSENT_DENIED":"CONSENT_DISCARDED",mode);result.setText(CaptureService.lastResult);return;
        }
        Intent capture=new Intent(this,CaptureService.class).putExtra("consent",data).putExtra("generation",pendingGeneration).putExtra("roi",pending);pending=null;
        startForegroundService(capture);result.setText("Armé. Quitte volontairement la préparation avant le délai ; aucun lecteur ne sera ouvert automatiquement.");
    }

    /** Returning during a live attempt cancels it; initial consent return has not departed yet. */
    @Override protected void onResume() {
        super.onResume();SessionMemory.preparationVisible=true;
        if(SessionMemory.policy.preparationLeft&&SessionMemory.policy.active())SessionMemory.cancel(this);
        if(result!=null)result.setText(CaptureService.lastResult);
    }

    /** Track departure only from this Activity, without discovering the replacement package. */
    @Override protected void onStop() {SessionMemory.preparationVisible=false;SessionMemory.policy.left();super.onStop();}

    /** Destroying preparation while pending/armed invalidates its single-generation permission. */
    @Override protected void onDestroy() {if(isFinishing()&&SessionMemory.policy.active())SessionMemory.cancel(this);super.onDestroy();}
}
