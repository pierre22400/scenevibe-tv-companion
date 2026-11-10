package com.scenevibe.tvcompanionpoc;

import android.accessibilityservice.AccessibilityService;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.os.SystemClock;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/**
 * Emulator-only setup of an actual Android accessibility service and system
 * shortcut target. Global action 13 traverses Android, not a direct callback or
 * panel PendingIntent. It does not emulate Sony's physical MUTE firmware.
 */
final class CompanionShortcutBench implements AutoCloseable {
    final Instrumentation instrument;
    final UiDevice remote;
    private final int oldFlags;
    private final Map<String,String> settings=new LinkedHashMap<>();
    /** Configure only the disposable emulator and preserve every prior test-owned setting. */
    CompanionShortcutBench(Instrumentation instrument) throws Exception {
        this.instrument=instrument;
        oldFlags=Configurator.getInstance().getUiAutomationFlags();
        Configurator.getInstance().setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        instrument.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        remote=UiDevice.getInstance(instrument);
        assertEquals("Emulator-only shortcut configuration","1",shell("getprop ro.kernel.qemu").trim());
        String component=new ComponentName(instrument.getTargetContext(),CompanionShortcutService.class).flattenToString();
        for(String key:new String[]{"enabled_accessibility_services","accessibility_enabled","accessibility_shortcut_target_service","accessibility_button_targets","accessibility_shortcut_dialog_shown"})settings.put(key,shell("settings get secure "+key).trim());
        String previous=settings.get("enabled_accessibility_services");
        String services="null".equals(previous)||previous.isEmpty()?component:previous.contains(component)?previous:previous+":"+component;
        set("enabled_accessibility_services",services);set("accessibility_enabled","1");
        set("accessibility_shortcut_target_service",component);set("accessibility_button_targets",component);set("accessibility_shortcut_dialog_shown","1");
        await(()->CompanionShortcutService.diagnostics().connected,"Actual principal accessibility service did not connect",15000);
    }
    /** Execute shell setup only through UiAutomation on the checked emulator. */
    private String shell(String command) throws Exception {return remote.executeShellCommand(command);}
    /** Quote a scalar setting as data, never a shell expression. */
    private String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
    /** Apply initial shortcut setup without touching Cloud, pairing or protected media. */
    private void set(String key,String value) throws Exception {
        String reply=shell("settings put secure "+key+" "+quote(value));
        String actual=shell("settings get secure "+key).trim();
        assertEquals("Emulator setting not applied: "+key+"; reply="+reply,value,actual);
    }
    /** Poll observable Android state on the actual application main Looper. */
    void await(BooleanSupplier condition,String message,long timeout) throws Exception {
        long deadline=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<deadline){AtomicBoolean ready=new AtomicBoolean();instrument.runOnMainSync(()->ready.set(condition.getAsBoolean()));if(ready.get())return;Thread.sleep(50);}fail(message+"; shortcut="+CompanionShortcutService.diagnostics().result);
    }
    /** Request Android's configured hardware accessibility shortcut, then require a real delivered callback. */
    void systemShortcut() throws Exception {
        int before=CompanionShortcutService.diagnostics().callbacks;
        assertTrue("Android global hardware accessibility shortcut unavailable on this emulator",instrument.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).performGlobalAction(AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT));
        await(()->CompanionShortcutService.diagnostics().callbacks>before,"System action did not deliver AccessibilityButtonController callback",10000);
    }
    /** Restore only the saved emulator settings; no physical-TV operation exists in this helper. */
    @Override public void close() throws Exception {
        for(var item:settings.entrySet()){
            if("null".equals(item.getValue()))shell("settings delete secure "+item.getKey());else set(item.getKey(),item.getValue());
        }
        Configurator.getInstance().setUiAutomationFlags(oldFlags);
    }
}
