package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

/** Qualifie les valeurs initiales et la persistance indépendante des deux choix publics. */
@RunWith(RobolectricTestRunner.class)
public final class PublicViewerPreferencesTest {
    /** Vérifie qu'une seconde initialisation et une nouvelle instance ne rallument pas AUDIO. */
    @Test public void initialDefaultsAndIndependentSwitchesPersist() throws Exception {
        Context app=RuntimeEnvironment.getApplication();
        var durable=app.getSharedPreferences("viewer-preferences-first",Context.MODE_PRIVATE);
        PublicViewerPreferences first=new PublicViewerPreferences(durable);
        assertFalse(first.initialized());assertFalse(first.setAudio(true));
        first.initialize(null);
        assertTrue(first.text());assertTrue(first.audio());assertTrue(first.setAudio(false));
        PublicViewerPreferences restored=new PublicViewerPreferences(durable);
        restored.initialize(new JSONObject().put("text",true).put("audio",true));
        assertTrue(restored.text());assertFalse(restored.audio());
        assertTrue(restored.setText(false));assertTrue(restored.setAudio(true));
        assertFalse(new PublicViewerPreferences(durable).text());assertTrue(new PublicViewerPreferences(durable).audio());
    }

    /** Une installation publique préexistante conserve ses choix lors de la mise à niveau. */
    @Test public void importsExistingPublicChoicesOnlyOnFirstInitialization() throws Exception {
        Context app=RuntimeEnvironment.getApplication();
        var durable=app.getSharedPreferences("viewer-preferences-upgrade",Context.MODE_PRIVATE);
        PublicViewerPreferences prefs=new PublicViewerPreferences(durable);
        prefs.initialize(new JSONObject().put("text",false).put("audio",true));
        assertFalse(prefs.text());assertTrue(prefs.audio());
        prefs.initialize(null);
        assertFalse(prefs.text());assertTrue(prefs.audio());
    }
}
