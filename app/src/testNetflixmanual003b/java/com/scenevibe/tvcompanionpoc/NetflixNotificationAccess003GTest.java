package com.scenevibe.tvcompanionpoc;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;

/**
 * Exercise the experimental notification and its local dispatch on an Android 12 JVM model.
 * The system UI, Sony notification rendering and cross-app focus are outside this model.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public final class NetflixNotificationAccess003GTest {
    private static final String[] ACTIONS = {
        "OPEN_CONTROLS", "ARM", "OPEN_I", "OPEN_PAUSED", "PAUSE", "RESUME", "CLOSE", "STOP"
    };

    /** Check the cold service's foreground record, channel and explicit immutable actions. */
    @Test public void coldServicePublishesProtectedActions() {
        var owner = Robolectric.buildService(NetflixManualSyncService.class).create();
        try {
            var service = owner.get();
            var shadow = Shadows.shadowOf(service);
            Notification note = shadow.getLastForegroundNotification();
            assertNotNull(note);
            assertEquals(8103, shadow.getLastForegroundNotificationId());
            assertTrue(shadow.isLastForegroundNotificationAttached());
            assertEquals("netflix-manual-003c", note.getChannelId());
            var manager = service.getSystemService(NotificationManager.class);
            assertEquals(NotificationManager.IMPORTANCE_DEFAULT,
                    manager.getNotificationChannel(note.getChannelId()).getImportance());
            assertTrue((note.flags & Notification.FLAG_ONGOING_EVENT) != 0);
            assertNull(note.contentIntent);
            assertEquals(ACTIONS.length, note.actions.length);
            assertEquals(ACTIONS.length, Arrays.stream(note.actions)
                    .map(action -> action.actionIntent).distinct().count());
            for (int index = 0; index < ACTIONS.length; index++) {
                var pending = Shadows.shadowOf(note.actions[index].actionIntent);
                assertTrue(pending.isService());
                assertTrue(pending.isImmutable());
                assertEquals(ACTIONS[index], pending.getSavedIntent().getAction());
                assertEquals(new ComponentName(service, NetflixManualSyncService.class),
                        pending.getSavedIntent().getComponent());
            }
        } finally {
            owner.destroy();
        }
    }

    /** Replay the action's saved intent at the service boundary and confirm reentry after closure. */
    @Test public void closeKeepsForegroundEntryAvailableForRepeatedOpen() {
        ShadowSettings.setCanDrawOverlays(true);
        var owner = Robolectric.buildService(NetflixManualSyncService.class).create();
        try {
            var service = owner.get();
            var shadow = Shadows.shadowOf(service);
            Notification note = shadow.getLastForegroundNotification();
            Intent open = Shadows.shadowOf(note.actions[0].actionIntent).getSavedIntent();
            Intent close = Shadows.shadowOf(note.actions[6].actionIntent).getSavedIntent();
            for (int cycle = 0; cycle < 3; cycle++) {
                service.onStartCommand(open, 0, cycle * 2 + 1);
                AlertDialog menu = ReflectionHelpers.getField(service, "controls");
                assertNotNull(menu);
                service.onStartCommand(close, 0, cycle * 2 + 2);
                assertNull(ReflectionHelpers.getField(service, "controls"));
                assertSame(note, shadow.getLastForegroundNotification());
                assertTrue(shadow.isLastForegroundNotificationAttached());
            }
        } finally {
            owner.destroy();
            ShadowSettings.setCanDrawOverlays(false);
        }
    }
}
