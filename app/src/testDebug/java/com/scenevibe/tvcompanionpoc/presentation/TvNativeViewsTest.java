package com.scenevibe.tvcompanionpoc.presentation;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import com.scenevibe.tvcompanionpoc.MainActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/**
 * Executes real Android Views and Activity lifecycle on Robolectric API35 with native graphics.
 * The generated images are software renders, explicitly not emulator or Sony observations.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class TvNativeViewsTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private View root;
    private static final String FIRST = "demo:columbo-eaux-troubles";
    private static final String SECOND = "demo:demo-contrechamp";

    /** Starts a consumer debug presentation with no configured Cloud origin. */
    @Before public void start() {
        new android.app.Instrumentation().setInTouchMode(false);
        controller = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible().windowFocusChanged(true);
        activity = controller.get();
        root = activity.findViewById(android.R.id.content).findViewWithTag("tv-shell");
        layout(1920, 1080);
    }

    /** Releases only the Activity; no runtime service was started by these tests. */
    @After public void stop() { if (controller != null) controller.pause().stop().destroy(); }

    /** Measures actual native Views and executes only already-due UI callbacks. */
    private void layout(int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height); ShadowLooper.idleMainLooper();
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)); root.layout(0, 0, width, height);
    }

    /** Finds stable focus identity rather than relying on a list position. */
    private View find(String key) {
        View view = root.findViewWithTag(key); assertNotNull("Missing native target " + key, view); return view;
    }

    /** Performs a deliberate native click and settles the resulting layout. */
    private void click(String key) { assertTrue(find(key).performClick()); layout(root.getWidth(), root.getHeight()); }

    /** Delivers a hardware-like key pair through the Activity, with no global remote hook. */
    private void key(int code) {
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        layout(root.getWidth(), root.getHeight());
    }

    /** Returns the stable key of the currently focused native view. */
    private String focus() { return root.findFocus() == null ? null : String.valueOf(root.findFocus().getTag()); }

    /** Selects a clearly labelled manual debug scenario through its actual native dialog. */
    private void scenario(int index) {
        click("nav-SETTINGS"); click("local-scenarios");
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog(); assertNotNull(dialog);
        dialog.getListView().performItemClick(null, index, index);
        layout(root.getWidth(), root.getHeight());
    }

    /** Opens the first explicitly named fixture; production has no implicit reference. */
    private void detail() { click("nav-TRACKS"); click("card-" + FIRST); }

    /** Collects actual rendered text for honest-status and accessibility assertions. */
    private String copy(View view) {
        StringBuilder all = new StringBuilder();
        if (view instanceof TextView) all.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) all.append(copy(((ViewGroup) view).getChildAt(i)));
        return all.toString();
    }

    /** The initial focus is on the reference card and does not trigger a selection. */
    @Test public void initialFocusAndDpadDoNotPrepareOrStartAService() {
        assertEquals("card-" + FIRST, focus());
        assertNull(org.robolectric.Shadows.shadowOf(activity.getApplication()).getNextStartedService());
        assertNull(root.findViewWithTag("prepare-primary"));
        key(KeyEvent.KEYCODE_DPAD_LEFT); assertEquals("nav-HOME", focus());
        key(KeyEvent.KEYCODE_DPAD_DOWN); assertEquals("nav-TRACKS", focus());
        assertNotNull(root.findViewWithTag("home-tracks"));
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertEquals("card-" + FIRST, focus());
        assertNull(root.findViewWithTag("home-tracks"));
    }

    /** Back restores the exact second, withdrawn-but-consultable card and its scroll position. */
    @Test public void backRestoresWithdrawnCardAndScroll() {
        click("nav-TRACKS"); find("card-" + SECOND).requestFocus();
        ScrollView scroll = (ScrollView) root.findViewWithTag("tv-content-scroll"); scroll.scrollTo(0, 360);
        int before = scroll.getScrollY(); click("card-" + SECOND);
        assertNull(root.findViewWithTag("track-primary")); assertEquals("track-back", focus());
        key(KeyEvent.KEYCODE_BACK);
        assertEquals("card-" + SECOND, focus()); assertEquals(before, scroll.getScrollY());
    }

    /** Returning from an external Activity preserves both the native action focus and UI state. */
    @Test public void pauseResumeKeepsTheSameFocus() {
        click("nav-SETTINGS"); find("runtime-control-1").requestFocus();
        controller.pause().stop().start().resume().visible(); layout(1920, 1080);
        assertEquals("runtime-control-1", focus());
    }

    /** Diagnostics Back highlights home while retaining the entry actually used to enter. */
    @Test public void diagnosticsBackReturnsToItsOwnMenuEntry() {
        click("nav-DIAGNOSTICS"); assertEquals("diag-check", focus());
        click("diag-check"); assertEquals("diag-check", focus());
        key(KeyEvent.KEYCODE_BACK); assertEquals("nav-DIAGNOSTICS", focus());
        assertTrue(find("nav-HOME").isSelected()); assertFalse(find("nav-DIAGNOSTICS").isSelected());
    }

    /** Closing the qualification dialog restores its opener, without stealing focus on refresh. */
    @Test public void dialogDismissAndPeriodicStatusPreserveFocus() {
        click("nav-SETTINGS"); find("local-scenarios").requestFocus(); click("local-scenarios");
        ShadowAlertDialog.getLatestAlertDialog().cancel(); layout(1920, 1080);
        assertEquals("local-scenarios", focus());
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS); assertEquals("local-scenarios", focus());
    }

    /** No elapsed delay confirms a local intention, installs content or announces readiness. */
    @Test public void preparationHasNoAutomaticSimulationTimer() {
        detail(); click("track-primary"); String before = copy(root);
        assertTrue(before.contains("Demande envoyée · démonstration"));
        ShadowLooper.idleMainLooper(30, TimeUnit.SECONDS);
        assertEquals(before, copy(root)); assertFalse(copy(root).contains("Prête sur cette TV · démonstration\nÉtat"));
    }

    /** The runtime diagnostics entry opens the original internal Activity, not a demo report. */
    @Test public void diagnosticsRetainsTheActualActivityEntry() {
        click("nav-DIAGNOSTICS"); click("diag-details");
        android.content.Intent intent = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(intent); assertEquals("com.scenevibe.tvcompanionpoc.DiagnosticsActivity", intent.getComponent().getClassName());
    }

    /** Activity recreation retains a routed card, focus and scroll, without resetting any preference. */
    @Test public void instanceStateRestoresNativeVisit() {
        detail(); find("track-back").requestFocus(); Bundle state = new Bundle();
        controller.saveInstanceState(state).pause().stop().destroy(); controller = null;
        controller = Robolectric.buildActivity(MainActivity.class).create(state).start().resume().visible().windowFocusChanged(true);
        activity = controller.get(); root = activity.findViewById(android.R.id.content).findViewWithTag("tv-shell"); layout(1920, 1080);
        assertEquals("track-back", focus()); assertTrue(copy(root).contains("Columbo — Eaux troubles"));
    }

    /** Empty home and Tracks remain navigable rather than inserting replacement fixture content. */
    @Test public void emptyCatalogueHasAnExplicitRecoverableState() {
        scenario(9); click("nav-HOME"); assertEquals("nav-SETTINGS", focus());
        key(KeyEvent.KEYCODE_DPAD_RIGHT); assertEquals("home-tracks", focus());
        assertTrue(copy(root).contains("Aucune Track pour cette TV"));
        click("home-tracks"); assertEquals("tracks-empty-back", focus());
        key(KeyEvent.KEYCODE_DPAD_CENTER); assertNotNull(root.findViewWithTag("home-tracks"));
    }

    /** An explicitly disabled native control cannot become the next spatial focus target. */
    @Test public void disabledControlsAreSkipped() {
        click("nav-SETTINGS"); find("runtime-control-1").setEnabled(false);
        find("runtime-control-0").requestFocus(); key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertNotEquals("runtime-control-1", focus());
    }

    /** The settings controls remain reachable with directional movement and do not run on focus. */
    @Test public void actualCompanionControlsAreReachableByDpad() {
        click("nav-SETTINGS"); assertEquals("settings-back", focus());
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals("runtime-control-0", focus());
        assertTrue(((Button) find("runtime-control-0")).getText().toString().contains("Display over other apps"));
        assertNull(org.robolectric.Shadows.shadowOf(activity.getApplication()).getNextStartedService());
        assertTrue(((ScrollView) root.findViewWithTag("tv-content-scroll")).getScrollY() > 0);
    }

    /** An editorial excerpt click changes only local TEXT and preserves its native focus key. */
    @Test public void textPreviewDoesNotUseTheOverlayRenderer() {
        detail(); find("preview-detail").requestFocus(); click("preview-detail");
        assertEquals("preview-detail", focus()); assertTrue(copy(root).contains("Le détail qui ne cadre pas"));
        assertNull(org.robolectric.Shadows.shadowOf(activity.getApplication()).getNextStartedService());
    }

    /** A demonstration of readiness cannot be mistaken for installed or admitted runtime data. */
    @Test public void demoReadinessAndRefusalRemainExplicit() {
        scenario(3); detail(); click("track-primary");
        assertTrue(copy(root).contains("Prête sur cette TV · démonstration"));
        assertTrue(copy(root).contains("Aucun programme reconnu"));
        assertTrue(copy(root).contains("aucune affectation"));
        scenario(4); detail(); click("track-primary");
        assertTrue(copy(root).contains("Confirmation refusée")); assertTrue(copy(root).contains("Rien n’est prêt"));
        click("prepare-primary"); assertTrue(copy(root).contains("Demande envoyée · démonstration"));
    }

    /** Recreating the Activity does not turn a transient local request into ready or lose it. */
    @Test public void localIntentionSurvivesActivityRecreationWithoutProgressing() {
        detail(); click("track-primary"); Bundle state = new Bundle(); controller.saveInstanceState(state).pause().stop().destroy();
        controller = Robolectric.buildActivity(MainActivity.class).create(state).start().resume().visible().windowFocusChanged(true);
        activity = controller.get(); root = activity.findViewById(android.R.id.content).findViewWithTag("tv-shell"); layout(1920, 1080);
        assertTrue(copy(root).contains("Demande envoyée · démonstration"));
        assertEquals("prepare-primary", focus());
    }

    /** Writes real native View pixels, preserving the explicit software provenance in the filename. */
    private void screenshot(String resolution, String name, int width, int height) throws Exception {
        layout(width, height);
        // Normalize the comparison viewport to the v0 top-of-page captures. Focus/Back
        // behavior is qualified separately above; reference 05 is itself a scrolled view.
        if (!name.startsWith("05-")) ((ScrollView) root.findViewWithTag("tv-content-scroll")).scrollTo(0, 0);
        File directory = new File(System.getProperty("user.dir"), "build/tvfirst002/native-views");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(image));
        try (FileOutputStream out = new FileOutputStream(new File(directory, resolution + "-" + name + "-robolectric.png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        image.recycle();
    }

    /** Renders all nine product reference variants plus the native qualification dialog disposition. */
    private void captureReferenceSeries(String resolution, int width, int height) throws Exception {
        screenshot(resolution, "01-accueil", width, height);
        click("nav-TRACKS"); screenshot(resolution, "02-mes-tracks", width, height);
        click("card-" + FIRST); screenshot(resolution, "03-fiche-track", width, height);
        scenario(1); detail(); click("track-primary"); screenshot(resolution, "04-preparation-confirmation", width, height);
        scenario(3); detail(); click("track-primary"); screenshot(resolution, "05-preparation-prete", width, height);
        scenario(4); detail(); click("track-primary"); screenshot(resolution, "06-echec-confirmation-refusee", width, height);
        scenario(8); click("nav-HOME"); click("card-" + FIRST); screenshot(resolution, "07-fiche-indisponible", width, height);
        scenario(0); click("nav-SETTINGS"); screenshot(resolution, "08-reglages", width, height);
        click("nav-DIAGNOSTICS"); screenshot(resolution, "09-diagnostics", width, height);
        scenario(9); click("nav-HOME"); screenshot(resolution, "11-accueil-vide", width, height);
        click("nav-TRACKS"); screenshot(resolution, "12-tracks-vides", width, height);
    }

    /** Captures actual Android Views at xhdpi: 1920×1080 corresponds to 960×540dp. */
    @Test public void capture1080ReferenceSurfaces() throws Exception { captureReferenceSeries("1080", 1920, 1080); }

    /** A separate density configuration reproduces TV's 720p dp measurement, without scaling TvStage. */
    @Test @Config(qualifiers = "w960dp-h540dp-land-tvdpi")
    public void capture720ReferenceSurfaces() throws Exception { captureReferenceSeries("720", 1280, 720); }
}
