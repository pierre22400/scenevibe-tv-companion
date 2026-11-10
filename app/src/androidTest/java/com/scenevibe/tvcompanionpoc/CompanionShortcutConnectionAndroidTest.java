package com.scenevibe.tvcompanionpoc;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * P0 reception: actual Android binding and framework-delivered shortcut callback,
 * with absent Track refusal. Sony physical MUTE remains a separate qualification.
 */
@RunWith(AndroidJUnit4.class)
public final class CompanionShortcutConnectionAndroidTest {
    /** Require a real Android connection, closed capabilities and a callback through the configured system action. */
    @Test public void systemShortcutBindsPrincipalServiceAndRefusesAbsentTrack() throws Exception {
        assertTrue(BuildConfig.COMPANION_MUTE_002);
        var instrument=InstrumentationRegistry.getInstrumentation();
        var declaration=instrument.getTargetContext().getPackageManager().getServiceInfo(
                new android.content.ComponentName(instrument.getTargetContext(),CompanionShortcutService.class),0);
        assertTrue("Installed shortcut component is disabled",declaration.enabled);
        try(var bench=new CompanionShortcutBench(instrument)){
            var context=instrument.getTargetContext();
            var manager=context.getSystemService(AccessibilityManager.class);
            AccessibilityServiceInfo selected=null;
            for(var info:manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK))
                if(context.getPackageName().equals(info.getResolveInfo().serviceInfo.packageName) && info.getResolveInfo().serviceInfo.name.endsWith(".CompanionShortcutService"))selected=info;
            assertNotNull("Principal service not actually enabled by Android",selected);
            assertEquals(0,selected.eventTypes);assertEquals(0,selected.getCapabilities());
            assertNotEquals(0,selected.flags & AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON);
            assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE",selected.getResolveInfo().serviceInfo.permission);
            int accepted=CompanionShortcutService.diagnostics().accepted;
            assertFalse(TvFirst009TextServiceOwner.multimediaActive());
            bench.systemShortcut();
            assertEquals("NOT_READY",CompanionShortcutService.diagnostics().result);
            assertEquals(accepted,CompanionShortcutService.diagnostics().accepted);
            assertFalse(TvFirst009TextServiceOwner.controlBusy());
            System.out.println("MUTE002_P0_PASS; ACTUAL_ANDROID_BINDING; SYSTEM_GLOBAL_ACTION_13; ACTUAL_CALLBACK; NO_TRACK_REFUSED; NOT_SONY_MUTE");
        }
    }
}
