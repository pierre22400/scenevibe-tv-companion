package com.scenevibe.tvcompanionpoc;

import android.app.Service;
import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** The default variant still reaches the historical permission gate, never the observation branch. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 35})
public final class TvFirstDefaultServiceTest {
    /** Actual default startup retains the old absent-overlay-permission refusal. */
    @Test public void defaultIsHistoricalAndKeepsPermissionGate() {
        assertFalse(BuildConfig.TVFIRST_OBSERVATION_ONLY);
        ServiceController<OverlayService> controller = Robolectric.buildService(OverlayService.class).create();
        OverlayService service = controller.get();
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(
                new Intent(service, OverlayService.class).setAction(OverlayService.ACTION_BOOT_PREPARE), 0, 1));
        assertNull(ReflectionHelpers.getField(service, "observationSession")); controller.destroy();
    }
}
