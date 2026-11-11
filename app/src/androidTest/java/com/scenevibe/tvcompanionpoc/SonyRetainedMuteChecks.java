package com.scenevibe.tvcompanionpoc;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import static org.junit.Assert.*;

/**
 * Exercise the real principal Track clock, calendar, overlay and MP3 renderer
 * through framework-delivered shortcut callbacks and native D-pad input.
 * Only the emulator, owned MEDIA session and synthetic private/public packages
 * participate. These observations are not physical Sony MUTE or Prime evidence.
 */
final class SonyRetainedMuteChecks {
    /** Qualify cancellation, both commit policies, TEXT and native AUDIO after actual shortcut editing. */
    static void exercise(Instrumentation instrument,int version) throws Exception {
        try (var bench = new CompanionShortcutBench(instrument)) {
            instrument.runOnMainSync(() -> TvFirst009TextServiceOwner.setDeveloperMode(instrument.getTargetContext(), false));
            SonySignedContinuityTest.player(bench.remote, 30000, false);
            waitForOwnedFocus(bench);
            bench.await(()->TvFirst009TextServiceOwner.multimediaTrackPosition()==30000,"Initial real clock missing",10000);
            int accepted=CompanionShortcutService.diagnostics().accepted;
            open(bench);bench.remote.pressDPadDown();bench.remote.pressDPadCenter();
            bench.remote.pressDPadLeft();assertEquals(30000,position(instrument));
            bench.remote.pressDPadCenter();closed(bench);
            assertEquals(20000,position(instrument));Thread.sleep(500);assertEquals(20000,position(instrument));
            assertTrue(TvFirst009TextServiceOwner.multimediaManuallySuspended());

            open(bench);bench.remote.pressDPadCenter();bench.remote.pressDPadRight();
            assertEquals(20000,position(instrument));bench.remote.pressBack();closed(bench);
            assertEquals(20000,position(instrument));assertTrue(TvFirst009TextServiceOwner.multimediaManuallySuspended());

            open(bench);bench.remote.pressDPadCenter();bench.remote.pressDPadUp();
            assertEquals(20000,position(instrument));bench.remote.pressDPadCenter();closed(bench);
            assertEquals(80000,position(instrument));assertFalse(TvFirst009TextServiceOwner.multimediaManuallySuspended());

            // Actual native MP3 playback after a shortcut-driven backward recalage.
            open(bench);bench.remote.pressDPadCenter();bench.remote.pressDPadDown();bench.remote.pressDPadRight();
            longOk(instrument);bench.remote.pressDPadRight();assertEquals(80000,position(instrument));
            int audioBefore=TvFirst009TextServiceOwner.multimediaAudioPlaybackStarts();
            bench.remote.pressDPadCenter();closed(bench);assertEquals(31000,position(instrument));
            SonySignedContinuityTest.player(bench.remote, 30000, true);
            bench.await(()->TvFirst009TextServiceOwner.multimediaAudioPlaybackStarts()>audioBefore,"No native AUDIO after shortcut recalage",12000);
            Thread.sleep(300);assertEquals(audioBefore+1,TvFirst009TextServiceOwner.multimediaAudioPlaybackStarts());

            // The owned viewer pauses itself; the Companion never sends any transport command.
            SonySignedContinuityTest.player(bench.remote, 30000, false);
            bench.await(()->TvFirst009TextServiceOwner.multimediaTrackPosition()==31000,"Paused projection was not refreshed",10000);
            open(bench);bench.remote.pressDPadCenter();bench.remote.pressDPadLeft();
            assertEquals(31000,position(instrument));bench.remote.pressDPadCenter();closed(bench);
            assertEquals(21000,position(instrument));
            SonySignedContinuityTest.player(bench.remote, 30000, true);
            bench.await(TvFirst009TextServiceOwner::multimediaTextAttached,"No native TEXT window after shortcut recalage",12000);
            assertEquals(accepted+5,CompanionShortcutService.diagnostics().accepted);
            assertFalse(TvFirst009TextServiceOwner.adSyncEnabled());
            SonySignedContinuityTest.report(instrument, "SONY001A_RETAINED_SHORTCUT_PASS version=" + version
                    + " system_callback real_clock BACK D_PAD focus_return native_TEXT_AUDIO");
        } finally {
            instrument.runOnMainSync(TvFirst009TextServiceOwner::closeDeveloperPanel);
        }
    }

    /** Read the real shared projection on its owning main Looper. */
    private static long position(Instrumentation instrument){
        long[] value={-1};instrument.runOnMainSync(()->value[0]=TvFirst009TextServiceOwner.multimediaTrackPosition());return value[0];
    }
    /** Ask Android policy for the configured callback and require actual window focus. */
    private static void open(CompanionShortcutBench bench) throws Exception {
        bench.systemShortcut();bench.await(TvFirst009TextServiceOwner::developerHasFocus,"Shortcut recalage window has no focus",10000);
    }
    /** Require removal and input restoration after either commit or cancellation. */
    private static void closed(CompanionShortcutBench bench) throws Exception {
        waitForOwnedFocus(bench);
        assertFalse(TvFirst009TextServiceOwner.developerHasFocus());
    }

    /** Query Android's actual current window to distinguish input focus from resumed activity state. */
    private static void waitForOwnedFocus(CompanionShortcutBench bench) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < deadline) {
            String windows = bench.remote.executeShellCommand("dumpsys window windows");
            if (java.util.Arrays.stream(windows.split("\\R")).anyMatch(line -> line.contains("mCurrentFocus")
                    && line.contains("com.amazon.amazonvideo.livingroom") && line.contains("OwnedPlayer"))) return;
            Thread.sleep(200);
        }
        fail("Android input focus did not return to the owned viewer");
    }

    /** Supply an actual held CENTER pair to the native window, rather than calling a View handler. */
    private static void longOk(Instrumentation instrument) throws Exception {
        UiAutomation automation=instrument.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        long down=SystemClock.uptimeMillis();
        assertTrue(automation.injectInputEvent(key(down,KeyEvent.ACTION_DOWN),true));
        Thread.sleep(800);assertTrue(automation.injectInputEvent(key(down,KeyEvent.ACTION_UP),true));
    }
    /** Use only public emulator input injection; production never synthesizes keys. */
    private static KeyEvent key(long down,int action){
        return new KeyEvent(down,SystemClock.uptimeMillis(),action,KeyEvent.KEYCODE_DPAD_CENTER,0,0,KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,InputDevice.SOURCE_DPAD);
    }
    /** Prevent an independent runtime or clock from being created by the test helper. */
    private SonyRetainedMuteChecks(){}
}
