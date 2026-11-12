package com.scenevibe.tvcompanionpoc;

import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.*;

/** Qualifie le chemin D-pad de la vue Réglages, sans accès réseau ni démarrage de la TV physique. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, qualifiers = "w960dp-h540dp-land-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public final class PublicAssociationFocusTest {
    private static final String ASSOCIATION = "Associer ma TV au catalogue public";

    /** L’action de configuration existante rejoint le focus de Réglages et s’ouvre avec OK. */
    @Test public void optionalAssociationUsesTheSettingsDpadAndTheExistingActivity() {
        assertTrue(BuildConfig.PUBLIC_VIEWER_001);
        new android.app.Instrumentation().setInTouchMode(false);
        var controller = Robolectric.buildActivity(MainActivity.class)
                .create().start().resume().visible().windowFocusChanged(true);
        MainActivity activity = controller.get();
        View shell = activity.findViewById(android.R.id.content).findViewWithTag("tv-shell");
        assertNotNull(shell);
        layout(shell);
        assertNull(findAssociation(activity.findViewById(android.R.id.content)));
        View menu = shell.findViewWithTag("nav-SETTINGS");
        assertNotNull(menu);
        assertTrue(menu.requestFocus());
        key(activity, shell, KeyEvent.KEYCODE_DPAD_CENTER);
        Button association = findAssociation(shell);
        assertNotNull("Association action must live in the shell", association);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        for (int n = 0; n < 12 && shell.findFocus() != association; n++) {
            key(activity, shell, KeyEvent.KEYCODE_DPAD_DOWN);
        }
        assertSame("D-pad DOWN must reach the scrollable association action", association, shell.findFocus());
        assertTrue(association.isFocused());
        assertTrue(association.getBackground().isStateful());
        key(activity, shell, KeyEvent.KEYCODE_DPAD_CENTER);
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(next);
        assertNotNull(next.getComponent());
        assertEquals(PublicPairingActivity.class.getName(), next.getComponent().getClassName());
        controller.pause().stop().destroy();
    }

    /** Mesure la même arborescence que le shell Android TV et déroule les rappels immédiats. */
    private static void layout(View shell) {
        int width = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
        shell.measure(width, height);
        shell.layout(0, 0, 1920, 1080);
        ShadowLooper.idleMainLooper();
        shell.measure(width, height);
        shell.layout(0, 0, 1920, 1080);
    }

    /** Distribue des événements de télécommande via l’Activity, comme pour les autres actions. */
    private static void key(MainActivity activity, View shell, int keyCode) {
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        layout(shell);
    }

    /** Localise le bouton réellement rendu par son texte accessible. */
    private static Button findAssociation(View view) {
        if (view instanceof Button && ASSOCIATION.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                Button action = findAssociation(group.getChildAt(i));
                if (action != null) return action;
            }
        }
        return null;
    }
}
