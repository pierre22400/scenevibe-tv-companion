package com.scenevibe.timebasecaptureg1;

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
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One explicit MediaProjection consent, one bounded acquisition, then terminal cleanup.
 * The only eligible foreground is our own fixture. A single worker serializes image ownership,
 * revocation and resource release. No bitmap, screenshot or arbitrary payload is journaled.
 */
public final class CaptureService extends Service {
    public static volatile String lastResult="DISARMED";
    public static volatile JSONObject lastProof;
    public static volatile boolean active;
    private static volatile CaptureService live;
    private final AtomicBoolean closed=new AtomicBoolean();
    private final CapturePolicy policy=new CapturePolicy();
    private HandlerThread thread;
    private Handler worker;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private Intent consent;
    private CapturePolicy.Mode mode;
    private double[] roi;
    private long generation,requestElapsed,requestUptime,requestWall,bufferElapsed;
    private int width,height,received,rejected,closedImages,markers;
    private boolean middleMagenta;
    private final String attempt=UUID.randomUUID().toString();

    /** New service instances never restore a token or previous arm state. */
    public CaptureService() { }

    /** Stop only this private diagnostic; no third-party service or transport is addressed. */
    public static void cancel(Context context) { context.stopService(new Intent(context,CaptureService.class)); }

    /** Create a single serial worker, without permanent polling. */
    @Override public void onCreate() {
        super.onCreate(); live=this; thread=new HandlerThread("G1-single-capture"); thread.start(); worker=new Handler(thread.getLooper());
    }

    /** No binder authority; only our non-exported explicit service intent is supported. */
    @Override public IBinder onBind(Intent intent) { return null; }

    /** Start visible foreground service before consuming consent; reject process-restoration and overlap. */
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null&&"STOP".equals(intent.getAction())) { worker.post(()->finish("OWNER_STOP")); return START_NOT_STICKY; }
        if(intent==null||active) { stopSelf(); return START_NOT_STICKY; }
        active=true; lastResult="ARMED"; lastProof=null;
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("g1","G1 capture ponctuelle",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,0,new Intent(this,CaptureService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,"g1").setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("G1 — écran synthétique uniquement").setContentText("Capture ponctuelle consentie ; arrêt disponible")
            .setOngoing(true).addAction(new Notification.Action.Builder(null,"Arrêter",stop).build()).build();
        startForeground(301,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        worker.post(()->arm(intent)); return START_NOT_STICKY;
    }

    /** Validate only closed/scalar configuration, discard the original token intent after use. */
    @SuppressWarnings("deprecation")
    private void arm(Intent intent) {
        try {
            mode=CapturePolicy.Mode.valueOf(intent.getStringExtra("mode"));
            roi=intent.getDoubleArrayExtra("roi");
            if(roi==null||roi.length!=4)throw new IllegalArgumentException("ROI_INVALID");
            CapturePolicy.roi(roi[0],roi[1],roi[2],roi[3],1000,1000);
            long delay=intent.getLongExtra("delay",-1);
            generation=policy.arm(SystemClock.elapsedRealtime(),delay);
            consent=intent.getParcelableExtra("consent");
            if(consent==null||intent.getIntExtra("code",0)!=android.app.Activity.RESULT_OK) { finish("CONSENT_DENIED"); return; }
            worker.postDelayed(()->begin(),delay);
            worker.postDelayed(()->finish("IMAGE_TIMEOUT"),delay+CapturePolicy.MAX_CAPTURE_MS);
        } catch(RuntimeException e) { finish("ROI_INVALID"); }
    }

    /** Open exactly one virtual display if the owned fixture is currently and freshly drawn. */
    private void begin() {
        if(closed.get())return;
        if(!FixtureActivity.visible||FixtureActivity.shownMode!=mode||FixtureActivity.drawnUptime==0
            ||SystemClock.uptimeMillis()-FixtureActivity.drawnUptime<400
            ||(mode==CapturePolicy.Mode.MIXED&&!FixtureActivity.surfaceDrawn)) { finish("WRONG_CONTROL_SCREEN"); return; }
        if(!policy.begin(generation,SystemClock.elapsedRealtime())) { finish("ARM_EXPIRED"); return; }
        try {
            Rect bounds=getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
            width=bounds.width(); height=bounds.height();
            CapturePolicy.roi(roi[0],roi[1],roi[2],roi[3],width,height);
            requestElapsed=SystemClock.elapsedRealtime();requestUptime=SystemClock.uptimeMillis();requestWall=System.currentTimeMillis();
            projection=getSystemService(MediaProjectionManager.class).getMediaProjection(android.app.Activity.RESULT_OK,consent); consent=null;
            if(projection==null) { finish("PROJECTION_UNAVAILABLE"); return; }
            projection.registerCallback(new MediaProjection.Callback() {
                /** Revocation is terminal and never attempts token reuse. */
                @Override public void onStop() { finish("SESSION_REVOKED"); }
            },worker);
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);
            reader.setOnImageAvailableListener(source->readImage(source),worker);
            display=projection.createVirtualDisplay("G1-owned-control",width,height,getResources().getDisplayMetrics().densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,worker);
            if(display==null)finish("PROJECTION_UNAVAILABLE");
        } catch(SecurityException e) { finish("PROJECTION_UNAVAILABLE"); }
        catch(RuntimeException e) { finish("RESOURCE_FAILURE"); }
    }

    /** Consume a fresh ImageReader frame, copy only ROI, then wipe the crop and close the image. */
    private void readImage(ImageReader source) {
        Image image=null; int[] crop=null; String outcome=null;
        try {
            if(closed.get())return;
            image=source.acquireLatestImage(); if(image==null) { rejected++; return; }
            received++;
            if(!policy.accepts(generation,SystemClock.elapsedRealtime())||!FixtureActivity.visible||FixtureActivity.shownMode!=mode) {
                outcome="WRONG_CONTROL_SCREEN"; return;
            }
            if(image.getWidth()!=width||image.getHeight()!=height||image.getFormat()!=PixelFormat.RGBA_8888||image.getPlanes().length!=1) {
                outcome="IMAGE_INVALID";return;
            }
            Image.Plane p=image.getPlanes()[0]; ByteBuffer bytes=p.getBuffer();
            int[] r=CapturePolicy.roi(roi[0],roi[1],roi[2],roi[3],width,height);
            crop=PixelProbe.crop(bytes,width,height,p.getPixelStride(),p.getRowStride(),r);
            markers=PixelProbe.matches(crop,r[2]-r[0],r[3]-r[1]);
            middleMagenta=PixelProbe.near(PixelProbe.pixel(bytes,width/2,(int)(height*.675),width,height,p.getPixelStride(),p.getRowStride()),0xffff00ff);
            bufferElapsed=SystemClock.elapsedRealtime();
            if(mode==CapturePolicy.Mode.SECURE)outcome=markers==0?"ROI_NOT_VISIBLE":"SECURITY_CONTROL_FAILED";
            else if(markers==4)outcome="ROI_VISIBLE";
            else { rejected++; lastResult="ACQUIRING_PATTERN_NOT_READY"; }
        } catch(RuntimeException e) { outcome="IMAGE_INVALID"; }
        finally {
            if(crop!=null)Arrays.fill(crop,0);
            if(image!=null) { image.close(); closedImages++; }
            if(outcome!=null)finish(outcome);
        }
    }

    /** Idempotently close every resource, publish scalar evidence and stop the foreground service. */
    private void finish(String outcome) {
        if(!closed.compareAndSet(false,true))return;
        policy.close(); consent=null; worker.removeCallbacksAndMessages(null);
        int errors=0;
        try { if(display!=null)display.release(); } catch(RuntimeException e) { errors++; }
        finally { display=null; }
        try { if(reader!=null) { reader.setOnImageAvailableListener(null,null); reader.close(); } } catch(RuntimeException e) { errors++; }
        finally { reader=null; }
        try { if(projection!=null)projection.stop(); } catch(RuntimeException e) { errors++; }
        finally { projection=null; }
        lastResult=errors==0?outcome:"RESOURCE_FAILURE";
        try {
            JSONObject j=new JSONObject().put("provenance","OWN_SYNTHETIC_EMULATOR_OR_OWNER_CONTROL_NOT_NETFLIX")
                .put("attempt",attempt).put("generation",generation).put("api",android.os.Build.VERSION.SDK_INT)
                .put("version","0.0.1-g1-private-diagnostic").put("mode",mode==null?"UNKNOWN":mode.name())
                .put("outcome",lastResult).put("width",width).put("height",height).put("roi",new org.json.JSONArray(roi==null?new double[0]:roi))
                .put("requestElapsedMs",requestElapsed).put("requestUptimeMs",requestUptime).put("requestWallMs",requestWall)
                .put("bufferReceivedElapsedMs",bufferElapsed).put("closedElapsedMs",SystemClock.elapsedRealtime())
                .put("imageTimestampBase","NOT_ASSUMED_NOT_USED").put("received",received).put("rejected",rejected)
                .put("imagesClosed",closedImages).put("markerMatches",markers).put("middleMagenta",middleMagenta)
                .put("secureSurfaceDrawn",FixtureActivity.surfaceDrawn).put("sequence",1)
                .put("releaseErrors",errors).put("resourcesClosed",errors==0).put("imagePersisted",false);
            lastProof=j;
            File file=new File(getFilesDir(),"g1.jsonl");
            if(file.length()>1024*1024&&!file.delete())throw new java.io.IOException("JOURNAL_LIMIT");
            try(FileOutputStream stream=new FileOutputStream(file,true)) { stream.write((j.toString()+"\n").getBytes(StandardCharsets.UTF_8)); }
        } catch(Exception e) { lastResult="JOURNAL_UNAVAILABLE"; }
        active=false; live=null; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); thread.quitSafely();
    }

    /** Destruction, screen departure and owner stop all terminate the same worker-owned attempt. */
    @Override public void onDestroy() { if(worker!=null)worker.post(()->finish("OWNER_STOP")); super.onDestroy(); }
}
