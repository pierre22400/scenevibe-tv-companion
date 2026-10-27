package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.scenevibe.tvcompanionpoc.audio.AndroidLocalAudioPort;
import com.scenevibe.tvcompanionpoc.audio.AudioEvent;
import com.scenevibe.tvcompanionpoc.audio.LocalAudioPort;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Experimental app entry: import the actual Studio ZIP from the document picker
 * OR a fixed app-private ADB inbox, without database/network or Prime interaction.
 * Tone playback is a manual local audio-device diagnostic, NOT MEDIA admission,
 * synchronization proof, or automatic Prime-overlay activation.
 */
public final class StudioMultimediaQualificationActivity extends Activity {
    private static final int IMPORT_PACKAGE=101;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView status;
    private Button choose,chooseAdb;
    private LinearLayout manualAudioControls;
    private AndroidLocalAudioPort manualTonePort;
    private volatile boolean alive;

    @FunctionalInterface private interface OpenStream { InputStream open() throws Exception; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);alive=true;
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(48,32,48,32);
        TextView title=new TextView(this);
        title.setText("SceneVibe — Studio → TV / qualification ADB");
        title.setTextSize(25);layout.addView(title);
        status=new TextView(this);
        status.setText("Importer le ZIP Studio (sélection manuelle ou transfert ADB). "
                +"L'édition MEDIA et le mapping restent non prouvés : aucune restitution "
                +"automatique avec Prime n'est autorisée.");
        status.setTextSize(18);layout.addView(status);

        choose=new Button(this);choose.setText("Choisir un ZIP depuis Android");
        choose.setOnClickListener(view -> {
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent,IMPORT_PACKAGE);
        });layout.addView(choose);

        chooseAdb=new Button(this);chooseAdb.setText("Vérifier le ZIP transféré par ADB");
        chooseAdb.setOnClickListener(view -> loadLocal(() ->
            new FileInputStream(StudioMultimediaAdbInbox.checkedFile(getFilesDir()))));
        layout.addView(chooseAdb);

        manualAudioControls=new LinearLayout(this);
        manualAudioControls.setOrientation(LinearLayout.VERTICAL);
        layout.addView(manualAudioControls);
        setContentView(layout);chooseAdb.requestFocus();
    }

    @Override public void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=IMPORT_PACKAGE || result!=RESULT_OK || data==null || data.getData()==null) return;
        final android.net.Uri uri=data.getData();
        loadLocal(() -> getContentResolver().openInputStream(uri));
    }

    /** Both local import routes use the same bounded native MP3 decoder and hash checks. */
    private void loadLocal(OpenStream source) {
        choose.setEnabled(false);chooseAdb.setEnabled(false);
        if(manualTonePort!=null) { manualTonePort.release();manualTonePort=null; }
        manualAudioControls.removeAllViews();
        status.setText("Vérification du ZIP, provenance TEXT et décodage MP3 Android…");
        worker.execute(() -> {
            String message;
            StudioMultimediaPackage imported=null;
            StudioMultimediaFiles cache=null;
            try(InputStream input=source.open()) {
                if(input==null) throw new IllegalArgumentException("LOCAL_FILE_UNAVAILABLE");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1) {
                    if(bytes.size()+count>256*1024*1024)
                        throw new IllegalArgumentException("PACKAGE_BUDGET_EXCEEDED");
                    bytes.write(buffer,0,count);
                }
                imported=StudioMultimediaPackage.read(bytes.toByteArray(),new AndroidStudioAudioInspector(this));
                cache=new StudioMultimediaFiles(new java.io.File(getFilesDir(),"studio-packages"),imported);
                message="Package vérifié · révision "+imported.revision+" · "
                        +imported.originalManifest.scenes.size()+" TEXT · "+imported.audio.size()+" AUDIO.\n"
                        +(imported.hasReferencedIdentity()?"Registre MEDIA externe non raccordé.":"MEDIA_EDITION_OR_MAPPING_UNPROVEN")
                        +"\nLes boutons ci-dessous testent MANUELLEMENT les tonalités, "
                        +"sans synchronisation ni contrôle de Prime.";
            } catch(Exception refused) {
                String code=refused.getMessage();
                message="Import refusé · "+(code!=null && code.matches("[A-Z0-9_]{1,100}")
                        ?code:"PACKAGE_OR_NATIVE_DECODE_REFUSED");
            }
            final String observed=message;
            final StudioMultimediaPackage verified=imported;
            final StudioMultimediaFiles files=cache;
            runOnUiThread(() -> {
                if(!alive) return;
                status.setText(observed);
                if(verified!=null && files!=null) showManualTones(verified,files);
                choose.setEnabled(true);chooseAdb.setEnabled(true);
            });
        });
    }

    /** Manual sound-only test, explicitly disconnected from MEDIA/TEXT scheduling. */
    private void showManualTones(StudioMultimediaPackage verified,StudioMultimediaFiles files) {
        manualTonePort=new AndroidLocalAudioPort(files);
        manualTonePort.setGain(0.30f);
        ArrayList<AudioEvent> tones=new ArrayList<>(verified.audio.values());
        tones.sort(Comparator.comparingLong(AudioEvent::startMs));
        for(int i=0;i<tones.size();i++) {
            AudioEvent event=tones.get(i);
            final int number=i+1;
            Button play=new Button(this);
            play.setText("Tester MP3 "+String.format(java.util.Locale.ROOT,"%03d",number)
                    +" — début éditorial "+(event.startMs()/1000)+" s");
            play.setOnClickListener(view -> {
                if(manualTonePort==null) return;
                manualTonePort.prepare(event.assetRef());
                manualTonePort.start();
                LocalAudioPort.State state=manualTonePort.state();
                status.setText(state==LocalAudioPort.State.STARTED
                        ?"Tonalité "+number+" : PLAY local manuel. Aucune admission/synchronisation MEDIA."
                        :"Tonalité "+number+" : LOCAL_AUDIO_"+state.name());
            });
            manualAudioControls.addView(play);
        }
        Button stop=new Button(this);stop.setText("Arrêter le son local");
        stop.setOnClickListener(view -> {
            if(manualTonePort!=null) manualTonePort.stop();
            status.setText("Son local arrêté. Aucune intervention sur Prime.");
        });manualAudioControls.addView(stop);
    }

    @Override public void onDestroy() {
        alive=false;worker.shutdownNow();
        if(manualTonePort!=null) { manualTonePort.release();manualTonePort=null; }
        super.onDestroy();
    }
}
