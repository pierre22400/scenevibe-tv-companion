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
import java.util.Arrays;
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
    private int width,height,received,rejected,closedImages;
    private boolean started,granted;
    private final String attempt=UUID.randomUUID().toString();

    /** Android creates a fresh service with no consent or image. */
    public CaptureService() { }

    /** Owner stop invalidates policy immediately, then requests serialized resource closure. */
    public static void cancel(Context c) {
        SessionMemory.policy.close();SessionMemory.roi.clear();c.stopService(new Intent(c,CaptureService.class));
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
            requestElapsed=SystemClock.elapsedRealtime();lastResult="CAPTURING";
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);
            reader.setOnImageAvailableListener(source->read(source),worker);
            display=projection.createVirtualDisplay("G2-single-consented-display",width,height,getResources().getDisplayMetrics().densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,worker);
            if(display==null) { finish("PROJECTION_UNAVAILABLE");return; }
            worker.postDelayed(()->finish("IMAGE_TIMEOUT"),CapturePolicy.MAX_CAPTURE_MS);
        } catch(SecurityException e) { finish("PROJECTION_UNAVAILABLE"); }
        catch(RuntimeException e) { finish("RESOURCE_FAILURE"); }
    }

    /** Drain callbacks without retries, ignoring initialization and duplicate timestamps, then copy one ROI. */
    private void read(ImageReader source) {
        Image image=null;int[] crop=null;String outcome=null;
        try {
            if(closed.get())return;
            image=source.acquireLatestImage();if(image==null){rejected++;return;}received++;
            long now=SystemClock.elapsedRealtime(),stamp=image.getTimestamp();
            boolean duplicate=stamp<=lastTimestamp;lastTimestamp=Math.max(lastTimestamp,stamp);
            if(received>240) { outcome="IMAGE_TIMEOUT";return; }
            if(!SessionMemory.policy.accepts(generation,now)||duplicate) { rejected++;return; }
            if(SessionMemory.preparationVisible) { outcome="PREPARATION_RETURNED";return; }
            if(image.getWidth()!=width||image.getHeight()!=height||image.getFormat()!=PixelFormat.RGBA_8888||image.getPlanes().length!=1) { outcome="IMAGE_INVALID";return; }
            Image.Plane p=image.getPlanes()[0];int[] r=CapturePolicy.roi(roi[0],roi[1],roi[2],roi[3],width,height);
            crop=PixelProbe.crop(p.getBuffer(),width,height,p.getPixelStride(),p.getRowStride(),r);
            if(!SessionMemory.policy.frame(generation,SystemClock.elapsedRealtime())) { outcome="STALE_GENERATION";return; }
            bufferElapsed=SystemClock.elapsedRealtime();SessionMemory.roi.put(generation,crop,r[2]-r[0],r[3]-r[1],bufferElapsed);crop=null;
            // Elapsed check on every access also handles suspend or delayed main-loop dispatch.
            new Handler(getMainLooper()).postDelayed(()->SessionMemory.roi.get(generation,SystemClock.elapsedRealtime()),CapturePolicy.ROI_MS);
            outcome="FRAME_READY_NOT_CLASSIFIED";
        } catch(RuntimeException e) { outcome="IMAGE_INVALID"; }
        finally {
            if(crop!=null)Arrays.fill(crop,0);
            if(image!=null){image.close();closedImages++;}
            if(outcome!=null)finish(outcome);
        }
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
