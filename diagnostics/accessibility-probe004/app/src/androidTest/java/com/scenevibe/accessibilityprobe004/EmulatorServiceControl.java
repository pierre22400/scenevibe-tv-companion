package com.scenevibe.accessibilityprobe004;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.io.IOException;
import java.io.InputStream;

/**
 * Emulator-only test setup, excluded from the delivered diagnostic APK. Instrumentation can
 * kill its target process, so enabled settings are refreshed after runner startup, not before.
 */
final class EmulatorServiceControl {
    /** Prevent construction of this fixed-command test utility. */
    private EmulatorServiceControl() { }

    /** Refuse any physical hardware before Activity creation, settings writes or instrumentation setup. */
    static void requireEmulator() throws IOException {
        if (!"ranchu".equals(Build.HARDWARE) && !"goldfish".equals(Build.HARDWARE))
            throw new IOException("Emulator-only test refused physical hardware");
    }

    /** Enable only our owned test service after UiAutomation exists, refusing physical hardware. */
    static ProbeService connect(Instrumentation instrumentation) throws IOException {
        requireEmulator();
        UiAutomation ui = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        command(ui, "settings put secure accessibility_enabled 0");
        command(ui, "settings delete secure enabled_accessibility_services");
        long disconnectedDeadline = SystemClock.uptimeMillis() + 15_000;
        while (ProbeService.current() != null && SystemClock.uptimeMillis() < disconnectedDeadline) SystemClock.sleep(50);
        if (ProbeService.current() != null) throw new IOException("Previous emulator service did not disconnect");
        command(ui, "settings put secure enabled_accessibility_services com.scenevibe.accessibilityprobe004/com.scenevibe.accessibilityprobe004.ProbeService");
        command(ui, "settings put secure accessibility_enabled 1");
        long deadline = SystemClock.uptimeMillis() + 15_000;
        while (ProbeService.current() == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
        return ProbeService.current();
    }

    /** Consume and close the result of a fixed emulator setup command; no arbitrary user input. */
    private static void command(UiAutomation ui, String command) throws IOException {
        try (InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))) {
            byte[] buffer = new byte[1024];
            while (stream.read(buffer) != -1) { /* Drain completion before the next setting. */ }
        }
    }
}
