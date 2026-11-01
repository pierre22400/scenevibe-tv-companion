package com.scenevibe.timebasecaptureg2;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Private scalar-only UTF-8 journal, including attempts rejected before any service exists.
 * The writer accepts constructed closed-schema objects, never images or arbitrary Android payloads.
 */
final class LocalJournal {
    /** Utility only; no state, token or image is retained. */
    private LocalJournal() { }

    /** Append one scalar record under the size cap, reporting any storage failure explicitly. */
    static synchronized boolean append(Context context,JSONObject row) {
        File file=new File(context.getFilesDir(),"g2.jsonl");
        try {
            if(file.length()>1024*1024&&!file.delete())return false;
            try(FileOutputStream stream=new FileOutputStream(file,true)) { stream.write((row.toString()+"\n").getBytes(StandardCharsets.UTF_8)); }
            return true;
        } catch(java.io.IOException e) { return false; }
    }

    /** Record an owner/system-consent rejection without starting a foreground service or capture. */
    static void rejected(Context context,String category,CapturePolicy.Mode mode) {
        try {
            JSONObject row=new JSONObject().put("attempt",UUID.randomUUID().toString()).put("sequence",1)
                .put("provenance","OWN_CONTROL_CONSENT_OR_VALIDATION_NOT_CAPTURE")
                .put("mode",mode.name()).put("api",android.os.Build.VERSION.SDK_INT)
                .put("outcome",category).put("receivedElapsedMs",SystemClock.elapsedRealtime())
                .put("receivedUptimeMs",SystemClock.uptimeMillis()).put("receivedWallMs",System.currentTimeMillis())
                .put("projectionStarted",false).put("resourcesClosed",true).put("imagePersisted",false);
            CaptureService.lastProof=row;
            CaptureService.lastResult=append(context,row)?category:"JOURNAL_UNAVAILABLE";
        } catch(org.json.JSONException e) { CaptureService.lastResult="JOURNAL_UNAVAILABLE"; }
    }
}
