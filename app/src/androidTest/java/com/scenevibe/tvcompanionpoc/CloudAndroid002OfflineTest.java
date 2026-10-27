package com.scenevibe.tvcompanionpoc;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Re-open the real application in a new process with network blocked by the owned emulator harness. */
@RunWith(AndroidJUnit4.class)
public final class CloudAndroid002OfflineTest {
    /** Find a genuine visible widget in the currently displayed Activity. */
    private View find(View root,java.util.function.Predicate<View> predicate){
        if(root.isShown() && predicate.test(root))return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View value=find(group.getChildAt(i),predicate);if(value!=null)return value;}}
        return null;
    }

    /** Read the persisted cache through its real screen button; a restart must not restore any MEDIA permit. */
    @Test public void offlineUiRestorationDoesNotStartService() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();var target=instrumentation.getTargetContext();
        boolean unreachable=false;
        try{var connection=(javax.net.ssl.HttpsURLConnection)new java.net.URL(BuildConfig.CLOUD_ORIGIN+"/health").openConnection();
            connection.setConnectTimeout(3000);connection.setReadTimeout(3000);try{connection.getResponseCode();}finally{connection.disconnect();}
        }catch(java.io.IOException expected){unreachable=true;}
        assertTrue("Harness did not block the target app's real HTTPS access",unreachable);
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try{
            instrumentation.runOnMainSync(()->{View button=find(activity.getWindow().getDecorView(),view->view instanceof Button && ((Button)view).getText().toString().equals("Réglages"));assertNotNull(button);button.performClick();});
            long end=android.os.SystemClock.elapsedRealtime()+90000;AtomicBoolean clicked=new AtomicBoolean(),ready=new AtomicBoolean();
            while(android.os.SystemClock.elapsedRealtime()<end && !ready.get()){
                instrumentation.runOnMainSync(()->{
                    View button=find(activity.getWindow().getDecorView(),view->view instanceof Button && ((Button)view).getText().toString().startsWith("Relire le cache multimédia local") && view.isEnabled());
                    if(!clicked.get() && button!=null){button.performClick();clicked.set(true);}
                    ready.set(find(activity.getWindow().getDecorView(),view->view instanceof TextView && ((TextView)view).getText().toString().contains("Cache privé vérifié : 16 TEXT + 3 AUDIO"))!=null);
                });Thread.sleep(100);
            }
            assertTrue("Actual offline restore callback missing",ready.get());assertFalse(TvFirst009TextServiceOwner.status().active);
            TvFirst008Source source=new TvFirst008Source(target);MultimediaPrivateCache.Prepared cache;
            try{cache=MultimediaAndroidBridge.restoreCache(target,source);}finally{source.close();}
            assertNotNull(cache);assertEquals(19,cache.track.calendar.events().size());
            JSONObject seed=new JSONObject(new String(Files.readAllBytes(new File(target.getFilesDir(),"cloudandroid002-journey.json").toPath()),StandardCharsets.UTF_8));
            assertNotEquals(seed.getInt("pid"),android.os.Process.myPid());
            Files.write(new File(target.getFilesDir(),"cloudandroid002-offline.json").toPath(),new JSONObject().put("events",19)
                    .put("seedPid",seed.getInt("pid")).put("restorePid",android.os.Process.myPid()).put("networkBlockedByHarness",true)
                    .put("playbackRestored",false).toString().getBytes(StandardCharsets.UTF_8));
        }finally{instrumentation.runOnMainSync(activity::finish);}
    }
}
