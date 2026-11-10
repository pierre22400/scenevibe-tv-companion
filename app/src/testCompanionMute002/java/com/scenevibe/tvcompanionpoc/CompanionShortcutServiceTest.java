package com.scenevibe.tvcompanionpoc;

import android.accessibilityservice.AccessibilityButtonController;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/**
 * Explicitly synthetic Android callback tests: qualify registration lifetime,
 * closed admission and manifest protection, never Sony MUTE transmission.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=31)
public final class CompanionShortcutServiceTest {
    private ServiceController<CompanionShortcutService> owner;
    private CompanionShortcutService service;
    /** Construct the real principal service, without a Track or foreground service. */
    @Before public void before(){assertTrue(BuildConfig.COMPANION_MUTE_002);owner=Robolectric.buildService(CompanionShortcutService.class).create();service=owner.get();}
    /** Retire callback identities after every independent registration test. */
    @After public void after(){owner.destroy();}
    /** Read the private current callback solely for tests explicitly labelled synthetic. */
    private AccessibilityButtonController.AccessibilityButtonCallback callback(){return ReflectionHelpers.getField(service,"callback");}
    /** Read the exact controller paired with the current synthetic callback. */
    private AccessibilityButtonController controller(){return ReflectionHelpers.getField(service,"controller");}
    /** Binding alone never opens a panel or creates an admitted clock. */
    @Test public void connectionDoesNotActivateAnything(){int count=CompanionShortcutService.diagnostics().callbacks;service.onServiceConnected();assertTrue(CompanionShortcutService.diagnostics().connected);assertEquals(count,CompanionShortcutService.diagnostics().callbacks);assertFalse(TvFirst009TextServiceOwner.multimediaActive());}
    /** A synthetic callback reaches the owner and is refused when no Track exists. */
    @Test public void syntheticCallbackFailsClosedWithoutTrack(){service.onServiceConnected();int count=CompanionShortcutService.diagnostics().callbacks;callback().onClicked(controller());assertEquals(count+1,CompanionShortcutService.diagnostics().callbacks);assertEquals("NOT_READY",CompanionShortcutService.diagnostics().result);assertFalse(TvFirst009TextServiceOwner.controlBusy());}
    /** Reconnection makes an already queued callback from the old registration inert. */
    @Test public void retiredSyntheticCallbackCannotReopen(){service.onServiceConnected();var old=callback();var source=controller();service.onServiceConnected();int count=CompanionShortcutService.diagnostics().callbacks;old.onClicked(source);assertEquals(count,CompanionShortcutService.diagnostics().callbacks);}
    /** Disconnect invalidates ownership before unregistering the callback. */
    @Test public void unboundSyntheticCallbackIsInert(){service.onServiceConnected();var callback=callback();var source=controller();service.onUnbind(new Intent());int count=CompanionShortcutService.diagnostics().callbacks;callback.onClicked(source);assertEquals(count,CompanionShortcutService.diagnostics().callbacks);assertFalse(CompanionShortcutService.diagnostics().connected);}
    /** A callback associated with another controller cannot enter the principal owner. */
    @Test public void wrongControllerIsRefused(){service.onServiceConnected();int count=CompanionShortcutService.diagnostics().callbacks;callback().onClicked(null);assertEquals(count,CompanionShortcutService.diagnostics().callbacks);}
    /** The exported system binding remains protected by Android's signature permission. */
    @Test public void manifestProtectsTheSystemBinding() throws Exception {var info=service.getPackageManager().getServiceInfo(new ComponentName(service,CompanionShortcutService.class),PackageManager.GET_META_DATA);assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE",info.permission);assertTrue(info.exported);assertTrue(info.enabled);assertTrue(info.metaData.containsKey("android.accessibilityservice"));}
    /** Ordinary events and feedback interrupts are deliberately ignored. */
    @Test public void ordinaryEventsCannotTriggerThePanel(){service.onServiceConnected();int count=CompanionShortcutService.diagnostics().callbacks;service.onAccessibilityEvent(null);service.onInterrupt();assertEquals(count,CompanionShortcutService.diagnostics().callbacks);}
}
