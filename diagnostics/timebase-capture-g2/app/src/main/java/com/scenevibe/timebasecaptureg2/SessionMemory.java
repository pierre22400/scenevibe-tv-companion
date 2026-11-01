package com.scenevibe.timebasecaptureg2;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;

/**
 * Process-only coordination, gated by generation and strict ROI expiry, never saved state.
 * A new arm or stop erases previous pixels. No third-party identity or foreground monitor exists.
 */
final class SessionMemory {
    static final CapturePolicy policy=new CapturePolicy();
    static final RoiLease roi=new RoiLease();
    static volatile boolean preparationVisible,synthetic=true;

    /** Utility only; there is no persisted singleton or account data. */
    private SessionMemory() { }

    /** Invalidate the attempt synchronously before requesting Android resource cleanup. */
    static void cancel(Context c) { policy.close();roi.clear();CaptureService.cancel(c); }

    /** Record only an explicit human label; synthetic labels can never become physical outcomes. */
    static synchronized boolean label(Context c,String value,boolean comparable) {
        if(CaptureService.active||policy.active()||CaptureService.lastProof==null)return false;
        if((value.equals("A")||value.equals("B"))&&(!comparable||roi.get(policy.generation,SystemClock.elapsedRealtime())==null))return false;
        if(!value.equals("A")&&!value.equals("B")&&!value.equals("C")&&!value.equals("INCONCLUSIVE"))return false;
        try {
            JSONObject row=new JSONObject().put("kind","OWNER_LABEL_NOT_AUTOMATIC_SIGNAL").put("generation",policy.generation)
                .put("attempt",CaptureService.lastProof.optString("attempt")).put("sourceCommit",BuildConfig.SOURCE_COMMIT)
                .put("label",synthetic?"SIMULATED_"+value:value).put("context",synthetic?"OWN_SYNTHETIC":"OWNER_DECLARED_CONTEXT")
                .put("elapsedMs",SystemClock.elapsedRealtime()).put("uptimeMs",SystemClock.uptimeMillis()).put("wallMs",System.currentTimeMillis());
            return LocalJournal.append(c,row);
        } catch(org.json.JSONException e) { CaptureService.lastResult="JOURNAL_UNAVAILABLE";return false; }
    }
}
