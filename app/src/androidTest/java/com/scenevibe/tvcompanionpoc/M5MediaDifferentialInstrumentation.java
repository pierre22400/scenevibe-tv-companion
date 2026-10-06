package com.scenevibe.tvcompanionpoc;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Build;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONArray;

/** Test APK only: compare actual legacy and candidate inside the same native Android VM. */
public final class M5MediaDifferentialInstrumentation extends Instrumentation {
    /** Start a bounded test process without an Activity or MediaSession acquisition. */
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    /** Export all raw synthetic traces in the test package cache, never in installation storage. */
    @Override public void onStart(){Bundle result=new Bundle();try{
        byte[] bytes;
        try(InputStream in=getContext().getAssets().open("corpus.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);bytes=out.toByteArray();
        }
        JSONObject corpus=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        // Reflection keeps this runner compilable in the retained M4 pre-fix build,
        // whose test-only source set deliberately has no Phase C shared harness.
        JSONObject evidence=(JSONObject)Class.forName("com.scenevibe.tvcompanionpoc.M5CDifferentialHarness")
                .getMethod("executeAndroid",JSONArray.class).invoke(null,corpus.getJSONArray("cases"));
        evidence.put("api",Build.VERSION.SDK_INT).put("environment",System.getProperty("java.vm.name")+" "+System.getProperty("java.vm.version"));
        File file=new File(getContext().getCacheDir(),"m5-phase-c-android.json");
        try(FileOutputStream out=new FileOutputStream(file)){out.write((evidence.toString(2)+"\n").getBytes(StandardCharsets.UTF_8));}
        result.putString("result","PASS");result.putInt("compared",evidence.getInt("compared"));result.putInt("hashmap",evidence.getInt("hashmap"));result.putInt("divergences",evidence.getInt("divergences"));finish(-1,result);
    }catch(Exception|AssertionError failed){result.putString("result","FAIL");finish(0,result);}}
}
