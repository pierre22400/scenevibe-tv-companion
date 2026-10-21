package com.scenevibe.tvcompanionpoc;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Actual merged variant, Activity/boot/service entry points and direct late render sinks. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={31,35})
@LooperMode(LooperMode.Mode.PAUSED)
public final class TvFirst008BoundaryTest {
    /** The merged installable profile exposes only the TV launcher and Internet permission. */
    @Test public void mergedManifestAndBuildCloseRuntimeAuthorities() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        assertTrue(BuildConfig.TVFIRST_DELIVERY_ONLY);assertTrue(BuildConfig.TVFIRST_OBSERVATION_ONLY);assertFalse(BuildConfig.ENABLE_LAN_DEV);
        assertEquals(13,BuildConfig.VERSION_CODE);assertEquals("",BuildConfig.CLOUD_ORIGIN);assertFalse(BuildConfig.DEBUG);
        PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_PERMISSIONS|PackageManager.GET_SERVICES|PackageManager.GET_RECEIVERS|PackageManager.MATCH_DISABLED_COMPONENTS);
        assertArrayEquals(new String[]{"android.permission.INTERNET"},info.requestedPermissions);
        for(android.content.pm.ServiceInfo service:info.services)assertFalse(service.enabled);
        for(android.content.pm.ActivityInfo receiver:info.receivers)assertFalse(receiver.enabled);
        assertEquals("SceneVibe TV",context.getApplicationInfo().loadLabel(context.getPackageManager()).toString());
    }
    /** Launch/resume/diagnostics never migrate legacy credentials or construct a historical Cloud client. */
    @Test public void actualActivitiesStayReadOnlyAndGiveAnHonestUnconfiguredState() {
        Context context=RuntimeEnvironment.getApplication();Map<String,Map<String,?>> before=seed(context);
        try(ActivityController<MainActivity> activity=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            String copy=text(activity.get().findViewById(android.R.id.content));
            assertTrue(copy.contains("n’est pas configuré"));assertFalse(copy.contains("Start SceneVibe"));assertFalse(copy.contains("Grant both"));
            activity.pause().resume();Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(before,snapshot(context));
        }
        try(ActivityController<DiagnosticsActivity> activity=Robolectric.buildActivity(DiagnosticsActivity.class).setup()) {
            String copy=text(activity.get().findViewById(android.R.id.content));
            assertTrue(copy.contains("FORBIDDEN"));assertFalse(copy.contains("Reset SceneVibe Cloud"));assertFalse(copy.contains("sentinel-private"));
            assertEquals(before,snapshot(context));
        }
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }
    /** Boot and package replacement return before credential construction or persistence, regardless of opt-in. */
    @Test public void realBootAndReplacementCannotMigrateOrStartAnything() {
        Context context=RuntimeEnvironment.getApplication();Map<String,Map<String,?>> before=seed(context);
        for(String action:new String[]{Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED})new BootReceiver().onReceive(context,new Intent(action));
        assertEquals(before,snapshot(context));assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }
    /** Even a direct framework service invocation and delayed native sinks cannot probe, restore or render. */
    @Test public void actualServiceAllActionsAndLateSinksAreInert() {
        Context context=RuntimeEnvironment.getApplication();Map<String,Map<String,?>> before=seed(context);
        ServiceController<OverlayService> controller=Robolectric.buildService(OverlayService.class).create();OverlayService service=controller.get();
        for(String action:new String[]{null,OverlayService.ACTION_BOOT_PREPARE,OverlayService.ACTION_CLOUD_CONNECT,OverlayService.ACTION_CLOUD_RESET,OverlayService.ACTION_TOP}) {
            assertEquals(Service.START_NOT_STICKY,service.onStartCommand(action==null?null:new Intent().setAction(action),0,1));
            assertInert(service);
        }
        SceneRuntimeController.SceneSink sink=ReflectionHelpers.getField(service,"sceneSink");assertFalse(sink.preflight(null));sink.show(null);
        ReflectionHelpers.callInstanceMethod(service,"showRenderer");assertInert(service);controller.destroy();sink.show(null);assertInert(service);
        assertEquals(before,snapshot(context));
    }
    /** Production transport refuses cleartext, alternate port, path, userinfo and malformed origins. */
    @Test public void productionTransportHasNoLoopbackHttpOrFallbackOrigin() {
        for(String origin:new String[]{"","http://127.0.0.1:1234","https://host:443","https://host/path","https://user@host","https://host?x=1"})
            assertThrows(IllegalArgumentException.class,()->new TvFirst008Http(origin));
    }
    /** Check actual service owner fields rather than a mirror policy. */
    private static void assertInert(OverlayService service) {
        for(String field:new String[]{"mediaSessionProbe","cloudClient","installationStore","packageInstaller","videoRuntimePorts","trackScheduler","commentaryServer","sceneController","renderer","sceneRenderer"})assertNull(field,ReflectionHelpers.getField(service,field));
    }
    /** Seed values that would trigger legacy migration if a wrong entry point were reached. */
    private static Map<String,Map<String,?>> seed(Context context) {
        for(String name:new String[]{"cloud_track","cloud_identity","cloud_secret","installation","overlay","pairing"})
            context.getSharedPreferences(name,0).edit().clear().putString("sentinel","sentinel-private").putString("deviceToken","legacy-not-to-migrate").commit();
        return snapshot(context);
    }
    /** Compare exact stored maps; never emit the seeded secret values in application diagnostics. */
    private static Map<String,Map<String,?>> snapshot(Context context) {
        Map<String,Map<String,?>> values=new HashMap<>();
        for(String name:new String[]{"cloud_track","cloud_identity","cloud_secret","installation","overlay","pairing"})values.put(name,new HashMap<>(context.getSharedPreferences(name,0).getAll()));return values;
    }
    /** Read only the visible Android view tree. */
    private static String text(View view) {
        StringBuilder text=new StringBuilder(view instanceof TextView?((TextView)view).getText():"");
        if(view instanceof ViewGroup)for(int index=0;index<((ViewGroup)view).getChildCount();index++)text.append('\n').append(text(((ViewGroup)view).getChildAt(index)));return text.toString();
    }
}
