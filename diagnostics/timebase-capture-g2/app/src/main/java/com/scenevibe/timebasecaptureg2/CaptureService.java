package com.scenevibe.timebasecaptureg2;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.WindowManager;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-consent bounded G2 acquisition. A worker owns all Android capture resources.
 * The display buffer exists transiently in ImageReader; only a validated ROI is copied.
 * Closure precedes optional user inspection. No foreground discovery, pattern inference,
 * retry, image file, external command or Activity launch exists in this service.
 */
public final class CaptureService extends Service {
    public static volatile String lastResult="DISARMED";
    public static volatile JSONObject lastProof;
    public static volatile boolean active;
    private static volatile CaptureService live;
    private final AtomicBoolean closed=new AtomicBoolean();
    private HandlerThread thread;
    private Handler worker;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private double[] roi;
    private long generation,projectionAt,requestElapsed,bufferElapsed,lastTimestamp;
    private long readerCreatedElapsed,displayCreatedElapsed,readerGeneration;
    private int width,height,received,rejected,closedImages;
    private int displayId=-1,decisionLogErrors,decisionsTruncated;
    private boolean started,granted;
    private boolean timestampSeen;
    private final LinkedHashMap<String,Integer> rejectionCounts=new LinkedHashMap<>();
    private final JSONArray bufferDecisions=new JSONArray();
    private final String attempt=UUID.randomUUID().toString();

    /** Android creates a fresh service with no consent or image. */
    public CaptureService() { }

    /** Owner stop invalidates policy immediately, then requests serialized resource closure. */
    public static void cancel(Context c) {
        SessionMemory.policy.close();SessionMemory.roi.clear();
        CaptureService service=live;
        if(service!=null&&service.worker!=null)service.worker.post(()->service.finish("OWNER_STOP"));
        else c.stopService(new Intent(c,CaptureService.class));
    }

    /** Promote immediately, including rapid-stop cases, then create one serial worker. */
    @Override public void onCreate() {
        super.onCreate();live=this;active=true;promote();thread=new HandlerThread("G2-one-frame");thread.start();worker=new Handler(thread.getLooper());
    }

    /** The private service exposes no binder or transport authority. */
    @Override public IBinder onBind(Intent intent) { return null; }

    /** Consume one explicit generation; restoration or a duplicate intent terminates safely. */
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null||started||"STOP".equals(intent.getAction())) { worker.post(()->finish("OWNER_STOP"));return START_NOT_STICKY; }
        started=true;lastProof=null;worker.post(()->prepare(intent));return START_NOT_STICKY;
    }

    /** Keep Android's visible notification and an explicit stop, without hiding capture indicators. */
    private void promote() {
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("g2","G2 capture ponctuelle",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,0,new Intent(this,CaptureService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"g2").setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("G2 — capture ponctuelle consentie").setContentText("Attente bornée puis une acquisition ; arrêt disponible")
            .setOngoing(true).addAction(new Notification.Action.Builder(null,"Arrêter",stop).build()).build();
        startForeground(302,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }

    /** Establish consent once, but create no display/buffers until the configured trigger. */
    @SuppressWarnings("deprecation")
    private void prepare(Intent intent) {
        try {
            generation=intent.getLongExtra("generation",-1);roi=intent.getDoubleArrayExtra("roi");
            if(roi==null||roi.length!=4)throw new IllegalArgumentException("ROI_INVALID");
            CapturePolicy.roi(roi[0],roi[1],roi[2],roi[3],1000,1000);
            if(generation!=SessionMemory.policy.generation||SessionMemory.policy.state!=CapturePolicy.State.WAITING) { finish("CONSENT_DISCARDED");return; }
            Intent consent=intent.getParcelableExtra("consent");
            if(consent==null) { finish("CONSENT_DENIED");return; }
            projection=getSystemService(MediaProjectionManager.class).getMediaProjection(android.app.Activity.RESULT_OK,consent);
            intent.removeExtra("consent");consent=null;
            if(projection==null) { finish("PROJECTION_UNAVAILABLE");return; }
            granted=true;projectionAt=SystemClock.elapsedRealtime();
            projection.registerCallback(new MediaProjection.Callback() {
                /** System revocation is terminal, including while waiting; never rearm. */
                @Override public void onStop() { finish("SESSION_REVOKED"); }
            },worker);
            lastResult="WAITING";
            long remaining=SessionMemory.policy.waitAt+SessionMemory.policy.delay-SystemClock.elapsedRealtime();
            worker.postDelayed(()->begin(),Math.max(0,remaining));
        } catch(SecurityException e) { finish("PROJECTION_UNAVAILABLE"); }
        catch(RuntimeException e) { finish("RESOURCE_FAILURE"); }
    }

    /** Open one virtual display only after departure from our preparation and a bounded delay. */
    private void begin() {
        if(closed.get())return;
        if(!SessionMemory.policy.begin(generation,SystemClock.elapsedRealtime(),SessionMemory.preparationVisible)) { finish("ARM_EXPIRED_OR_PREPARATION_VISIBLE");return; }
        try {
            Rect bounds=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
            width=bounds.width();height=bounds.height();CapturePolicy.roi(roi[0],roi[1],roi[2],roi[3],width,height);
            requestElapsed=SessionMemory.policy.triggeredAt;lastResult="INITIALIZING";
            // Preserve the 250-ms barrier before creating the fresh queue, not by losing its only frame.
            worker.postDelayed(()->openDisplay(),CapturePolicy.INITIALIZATION_MS);
            worker.postDelayed(()->finish("IMAGE_TIMEOUT"),Math.max(0,requestElapsed+CapturePolicy.MAX_CAPTURE_MS-SystemClock.elapsedRealtime()));
        } catch(RuntimeException e) { finish("RESOURCE_FAILURE"); }
    }

    /** Create one new queue/surface after settling, bound only to this generation and virtual display. */
    private void openDisplay() {
        if(closed.get())return;
        String refusal=SessionMemory.policy.bufferRejection(generation,SystemClock.elapsedRealtime());
        if(refusal!=null) { decision(refusal,0,SystemClock.elapsedRealtime(),generation);finish(refusal);return; }
        if(SessionMemory.preparationVisible) { finish("PREPARATION_RETURNED");return; }
        try {
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);
            readerCreatedElapsed=SystemClock.elapsedRealtime();readerGeneration=generation;
            final long boundGeneration=readerGeneration;
            reader.setOnImageAvailableListener(source->read(source,boundGeneration),worker);
            display=projection.createVirtualDisplay("G2-single-consented-display",width,height,getResources().getDisplayMetrics().densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,worker);
            if(display==null) { finish("PROJECTION_UNAVAILABLE");return; }
            if(display.getDisplay()==null) { finish("DISPLAY_UNAVAILABLE");return; }
            displayId=display.getDisplay().getDisplayId();displayCreatedElapsed=SystemClock.elapsedRealtime();lastResult="CAPTURING";
        } catch(SecurityException e) { finish("PROJECTION_UNAVAILABLE"); }
        catch(RuntimeException e) { finish("RESOURCE_FAILURE"); }
    }

    /** Drain callbacks without retries, ignoring initialization and duplicate timestamps, then copy one ROI. */
    private void read(ImageReader source) {
        read(source,generation);
    }

    /** Validate the callback's immutable queue/generation binding before accepting its first or later timestamp. */
    private void read(ImageReader source,long callbackGeneration) {
        Image image=null;int[] crop=null;String outcome=null;
        try {
            if(closed.get())return;
            long now=SystemClock.elapsedRealtime();
            if(source!=reader) { decision("SOURCE_READER_MISMATCH",0,now,callbackGeneration);outcome="SOURCE_READER_MISMATCH";return; }
            if(callbackGeneration!=readerGeneration||callbackGeneration!=generation) { decision("STALE_GENERATION",0,now,callbackGeneration);outcome="STALE_GENERATION";return; }
            if(display==null||display.getDisplay()==null||display.getDisplay().getDisplayId()!=displayId) { decision("DISPLAY_BINDING_MISMATCH",0,now,callbackGeneration);outcome="DISPLAY_BINDING_MISMATCH";return; }
            image=source.acquireLatestImage();if(image==null){decision("NO_IMAGE_AVAILABLE",0,now,callbackGeneration);return;}received++;
            now=SystemClock.elapsedRealtime();long stamp=image.getTimestamp();
            if(received>240) { decision("BUFFER_LIMIT",stamp,now,callbackGeneration);outcome="IMAGE_TIMEOUT";return; }
            String refusal=SessionMemory.policy.bufferRejection(callbackGeneration,now);
            if(refusal!=null) { decision(refusal,stamp,now,callbackGeneration);return; }
            refusal=CapturePolicy.timestampRejection(timestampSeen,lastTimestamp,stamp);
            if(refusal!=null) { decision(refusal,stamp,now,callbackGeneration);return; }
            timestampSeen=true;lastTimestamp=stamp;
            if(SessionMemory.preparationVisible) { decision("PREPARATION_RETURNED",stamp,now,callbackGeneration);outcome="PREPARATION_RETURNED";return; }
            if(image.getWidth()!=width||image.getHeight()!=height||image.getFormat()!=PixelFormat.RGBA_8888||image.getPlanes().length!=1) { decision("IMAGE_LAYOUT_INVALID",stamp,now,callbackGeneration);outcome="IMAGE_INVALID";return; }
            Image.Plane p=image.getPlanes()[0];int[] r=CapturePolicy.roi(roi[0],roi[1],roi[2],roi[3],width,height);
            crop=PixelProbe.crop(p.getBuffer(),width,height,p.getPixelStride(),p.getRowStride(),r);
            if(!SessionMemory.policy.frame(generation,SystemClock.elapsedRealtime())) { String cause=SessionMemory.policy.bufferRejection(generation,SystemClock.elapsedRealtime());outcome=cause==null?"FRAME_COMMIT_REFUSED":cause;decision(outcome,stamp,SystemClock.elapsedRealtime(),callbackGeneration);return; }
            bufferElapsed=SystemClock.elapsedRealtime();SessionMemory.roi.put(generation,crop,r[2]-r[0],r[3]-r[1],bufferElapsed);crop=null;
            // Elapsed check on every access also handles suspend or delayed main-loop dispatch.
            new Handler(getMainLooper()).postDelayed(()->SessionMemory.roi.get(generation,SystemClock.elapsedRealtime()),CapturePolicy.ROI_MS);
            outcome="FRAME_READY_NOT_CLASSIFIED";
            decision("ACCEPTED",stamp,now,callbackGeneration);
        } catch(RuntimeException e) { decision("IMAGE_READ_OR_CROP_FAILURE",0,SystemClock.elapsedRealtime(),callbackGeneration);outcome="IMAGE_INVALID"; }
        finally {
            if(crop!=null)Arrays.fill(crop,0);
            if(image!=null){image.close();closedImages++;}
            if(outcome!=null)finish(outcome);
        }
    }

    /** Keep bounded scalar decisions and aggregate rejection causes; never log or retain diagnostic pixels. */
    private void decision(String reason,long stamp,long now,long callbackGeneration) {
        if(!"ACCEPTED".equals(reason)){rejected++;rejectionCounts.put(reason,rejectionCounts.getOrDefault(reason,0)+1);}
        if(bufferDecisions.length()>=16){decisionsTruncated++;return;}
        try {
            bufferDecisions.put(new JSONObject().put("index",received).put("decision",reason).put("receiptElapsedMs",now)
                .put("sinceTriggerMs",now-requestElapsed).put("imageTimestampNs",stamp).put("callbackGeneration",callbackGeneration)
                .put("readerGeneration",readerGeneration).put("virtualDisplayId",displayId));
        } catch(org.json.JSONException e){decisionLogErrors++;}
    }

    /** Release image-producing resources before publishing a result; only successful ROI may remain. */
    private void finish(String reason) {
        if(!closed.compareAndSet(false,true))return;
        SessionMemory.policy.close();worker.removeCallbacksAndMessages(null);int errors=0;
        try { if(display!=null)display.release(); } catch(RuntimeException e){errors++;} finally {display=null;}
        try { if(reader!=null){reader.setOnImageAvailableListener(null,null);reader.close();} } catch(RuntimeException e){errors++;} finally {reader=null;}
        try { if(projection!=null)projection.stop(); } catch(RuntimeException e){errors++;} finally {projection=null;}
        if(errors!=0||!reason.equals("FRAME_READY_NOT_CLASSIFIED"))SessionMemory.roi.clear();
        lastResult=errors==0?reason:"RESOURCE_FAILURE";
        try {
            JSONObject j=new JSONObject().put("kind","TECHNICAL_ATTEMPT_NOT_AD_SIGNAL").put("attempt",attempt).put("generation",generation)
                .put("provenance",SessionMemory.synthetic?"OWN_SYNTHETIC":"OWNER_DECLARED_CONTEXT_NOT_VERIFIED")
                .put("api",android.os.Build.VERSION.SDK_INT).put("version",BuildConfig.VERSION_NAME).put("sourceCommit",BuildConfig.SOURCE_COMMIT)
                .put("outcome",lastResult).put("width",width).put("height",height).put("roi",new org.json.JSONArray(roi==null?new double[0]:roi))
                .put("delayMs",SessionMemory.policy.delay).put("projectionElapsedMs",projectionAt).put("triggerElapsedMs",requestElapsed)
                .put("bufferElapsedMs",bufferElapsed).put("closedElapsedMs",SystemClock.elapsedRealtime())
                .put("readerCreatedElapsedMs",readerCreatedElapsed).put("displayCreatedElapsedMs",displayCreatedElapsed)
                .put("virtualDisplayId",displayId).put("readerGeneration",readerGeneration).put("initializationMs",CapturePolicy.INITIALIZATION_MS)
                .put("freshnessPolicy","NEW_READER_AND_SINGLE_DISPLAY_AFTER_BARRIER_NO_CROSS_CLOCK_COMPARISON")
                .put("rejectionCounts",new JSONObject(rejectionCounts)).put("bufferDecisions",bufferDecisions)
                .put("decisionsTruncated",decisionsTruncated).put("decisionLogErrors",decisionLogErrors)
                .put("receivedUptimeMs",SystemClock.uptimeMillis()).put("receivedWallMs",System.currentTimeMillis())
                .put("timestampPolicy","IMAGE_TIMESTAMPS_ONLY_COMPARED_TO_IMAGE_TIMESTAMPS")
                .put("received",received).put("rejected",rejected).put("imagesClosed",closedImages)
                .put("usefulFrames",SessionMemory.policy.usefulFrame?1:0).put("consent",granted?"GRANTED":"UNKNOWN")
                .put("releaseErrors",errors).put("resourcesClosed",errors==0).put("imagePersisted",false).put("sequence",1);
            lastProof=j;if(!LocalJournal.append(this,j))lastResult="JOURNAL_UNAVAILABLE";
        } catch(org.json.JSONException e){lastResult="JOURNAL_UNAVAILABLE";SessionMemory.roi.clear();}
        active=false;live=null;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();thread.quitSafely();
    }

    /** Destruction joins bounded serialized cleanup; interruptions/errors remain visible. */
    @Override public void onDestroy() {
        if(worker!=null&&!closed.get()) {
            java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);
            worker.post(()->{try{finish("OWNER_STOP");}finally{latch.countDown();}});
            try {if(!latch.await(1000,java.util.concurrent.TimeUnit.MILLISECONDS))lastResult="CLOSE_DELAYED";}
            catch(InterruptedException e){Thread.currentThread().interrupt();lastResult="CLOSE_INTERRUPTED";}
        }
        super.onDestroy();
    }
}
