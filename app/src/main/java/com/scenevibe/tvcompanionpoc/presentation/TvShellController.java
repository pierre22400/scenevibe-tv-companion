package com.scenevibe.tvcompanionpoc.presentation;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.scenevibe.tvcompanionpoc.R;
import com.scenevibe.tvcompanionpoc.BuildConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The accepted v0 composition, measured by Android Views in the existing launcher Activity.
 * This controller owns presentation only. Runtime controls are the original actual Views;
 * the service, pairing, installer, calendar and renderer remain outside its lifetime.
 */
public final class TvShellController {
    /** Supplies bounded observational text; it must never return credentials or package content. */
    public interface RuntimeReader {
        /** Captures current real Companion state without starting a worker or sending a request. */
        RuntimeStatus read();
    }

    /** Already sanitized status, independent of the development catalogue. */
    public static final class RuntimeStatus {
        public final String cloud, permissions, service, device;

        /** Retains consumer-safe text, not ids, routes, secrets or executable data. */
        public RuntimeStatus(String cloud, String permissions, String service, String device) {
            this.cloud = cloud; this.permissions = permissions; this.service = service; this.device = device;
        }
    }

    private final Activity activity;
    private final TvViews ui;
    private final TvPresentationSource source;
    private final RuntimeReader runtime;
    private final Runnable openTechnicalDiagnostics;
    private final LinearLayout runtimeControls;
    private final TvNavigation navigation = new TvNavigation();
    private final Map<String, View> targets = new LinkedHashMap<>();
    private final Map<TvNavigation.Screen, Button> menu = new LinkedHashMap<>();
    private final LinearLayout root, page;
    private final ScrollView scroll;
    private final TextView connection;
    private TextView accountStatus, diagnosticConnection, diagnosticService, diagnosticDevice;
    private boolean restoring;
    private String defaultFocus;

    /** Builds one shell and reuses the real control View tree instead of static placeholders. */
    public TvShellController(Activity activity, LinearLayout runtimeControls, RuntimeReader runtime,
            Runnable openTechnicalDiagnostics, Bundle savedState) {
        this.activity = activity;
        this.ui = new TvViews(activity);
        this.runtimeControls = runtimeControls;
        this.runtime = runtime;
        this.openTechnicalDiagnostics = openTechnicalDiagnostics;
        source = TvSourceFactory.create(activity);
        restore(savedState);
        root = ui.column();
        root.setTag("tv-shell");
        root.setBackgroundColor(ui.color(R.color.scenevibe_background));
        LinearLayout header = ui.row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(d(R.dimen.tv_safe_x), 0, d(R.dimen.tv_safe_x), 0);
        header.setBackgroundColor(ui.color(R.color.scenevibe_card));
        ImageView mark = icon("sparkles", R.color.scenevibe_primary_foreground, 19);
        mark.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6));
        mark.setBackground(ui.panel(R.color.scenevibe_primary, 0, 8));
        header.addView(mark, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));
        TextView brand = text("SceneVibe ", 22, true, 650, R.color.scenevibe_foreground);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(-2, -2);
        brandParams.leftMargin = ui.dp(9);
        header.addView(brand, brandParams);
        header.addView(text("TV", 22, true, 650, R.color.scenevibe_primary));
        View spacer = new View(activity);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        connection = text(s(source.isLocalDemo() ? R.string.tv_demo_header : R.string.tv_unconnected_header),
                13, false, 400, R.color.scenevibe_muted_foreground);
        header.addView(connection);
        root.addView(header, new LinearLayout.LayoutParams(-1, d(R.dimen.tv_header_height) - ui.dp(1)));
        View separator = new View(activity);
        separator.setBackgroundColor(ui.color(R.color.scenevibe_border));
        root.addView(separator, new LinearLayout.LayoutParams(-1, ui.dp(1)));
        LinearLayout body = ui.row();
        body.setClipChildren(true);
        body.setClipToPadding(true);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout sidebar = ui.column();
        sidebar.setPadding(d(R.dimen.tv_safe_x), d(R.dimen.tv_content_top),
                d(R.dimen.tv_sidebar_end), d(R.dimen.tv_safe_y));
        sidebar.setBackgroundColor(ui.color(R.color.scenevibe_sidebar));
        body.addView(sidebar, new LinearLayout.LayoutParams(d(R.dimen.tv_sidebar_width), -1));
        TvNavigation.Screen[] sections = {TvNavigation.Screen.HOME, TvNavigation.Screen.TRACKS,
                TvNavigation.Screen.SETTINGS, TvNavigation.Screen.DIAGNOSTICS};
        int[] labels = {R.string.tv_home, R.string.tv_tracks, R.string.tv_settings, R.string.tv_diagnostics};
        String[] icons = {"house", "bookmark", "settings", "activity"};
        for (int i = 0; i < sections.length; i++) {
            TvNavigation.Screen section = sections[i];
            Button button = ui.button(s(labels[i]), false, () -> {
                remember(); navigation.section(section); render();
            });
            button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            ui.typography(button, 15, false, 500);
            button.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
            int iconId = drawable("tv_icon_" + icons[i]);
            android.graphics.drawable.Drawable glyph = activity.getDrawable(iconId);
            if (glyph != null) {
                glyph.setTint(ui.color(R.color.scenevibe_sidebar_foreground));
                glyph.setBounds(0, 0, ui.dp(18), ui.dp(18));
                button.setCompoundDrawables(glyph, null, null, null);
                button.setCompoundDrawablePadding(ui.dp(10));
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, d(R.dimen.tv_nav_height));
            if (i > 0) p.topMargin = d(R.dimen.tv_nav_gap);
            sidebar.addView(button, p);
            menu.put(section, button);
        }
        scroll = new ScrollView(activity);
        scroll.setTag("tv-content-scroll");
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.setSmoothScrollingEnabled(false);
        scroll.setPadding(d(R.dimen.tv_content_start), d(R.dimen.tv_content_top),
                d(R.dimen.tv_safe_x), d(R.dimen.tv_safe_y));
        body.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        page = ui.column();
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        render();
        if (source instanceof TvAccountSource) ((TvAccountSource) source).onChange(() -> {
            remember(); render();
        });
    }

    /** Returns the single native root for the existing MainActivity content view. */
    public View view() { return root; }

    /** Expose the existing source to explicit local qualification controls without creating a second journey. */
    public TvPresentationSource sourceForLocalQualification() { return source; }

    /** Resolves accepted dimensions through Android resources. */
    private int d(int resource) { return activity.getResources().getDimensionPixelSize(resource); }

    /** Resolves localized copy without introducing raw UI text into runtime decisions. */
    private String s(int resource, Object... args) { return activity.getString(resource, args); }

    /** Produces one typography-consistent TextView. */
    private TextView text(CharSequence value, float sp, boolean heading, int weight, int color) {
        return ui.text(value, sp, heading, weight, color);
    }

    /** Resolves only bundled drawable names; an absent illustration has an explicit text fallback. */
    private int drawable(String name) {
        switch (name) {
            case "tv_icon_activity": return R.drawable.tv_icon_activity;
            case "tv_icon_arrow_left": return R.drawable.tv_icon_arrow_left;
            case "tv_icon_bookmark": return R.drawable.tv_icon_bookmark;
            case "tv_icon_check": return R.drawable.tv_icon_check;
            case "tv_icon_circle_dashed": return R.drawable.tv_icon_circle_dashed;
            case "tv_icon_circle_off": return R.drawable.tv_icon_circle_off;
            case "tv_icon_house": return R.drawable.tv_icon_house;
            case "tv_icon_info": return R.drawable.tv_icon_info;
            case "tv_icon_layers": return R.drawable.tv_icon_layers;
            case "tv_icon_monitor": return R.drawable.tv_icon_monitor;
            case "tv_icon_refresh_cw": return R.drawable.tv_icon_refresh_cw;
            case "tv_icon_rotate_ccw": return R.drawable.tv_icon_rotate_ccw;
            case "tv_icon_settings": return R.drawable.tv_icon_settings;
            case "tv_icon_sparkles": return R.drawable.tv_icon_sparkles;
            case "tv_icon_x": return R.drawable.tv_icon_x;
            case "editorial_cinema_noir": return R.drawable.editorial_cinema_noir;
            case "editorial_contrechamp": return R.drawable.editorial_contrechamp;
            case "editorial_detail": return R.drawable.editorial_detail;
            case "editorial_editions": return R.drawable.editorial_editions;
            case "editorial_mise_en_scene": return R.drawable.editorial_mise_en_scene;
            case "editorial_space_columbo": return R.drawable.editorial_space_columbo;
            default: return 0;
        }
    }

    /** Creates a decorative, nonfocusable icon with no redundant accessibility announcement. */
    private ImageView icon(String name, int color, int size) {
        ImageView image = new ImageView(activity);
        int id = drawable("tv_icon_" + name.replace('-', '_'));
        if (id != 0) image.setImageResource(id);
        image.setColorFilter(ui.color(color));
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setMinimumWidth(ui.dp(size)); image.setMinimumHeight(ui.dp(size));
        return image;
    }

    /** Adds a full-width block with the accepted vertical rhythm. */
    private void block(LinearLayout parent, View view, int gap) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        if (parent.getChildCount() > 0) p.topMargin = ui.dp(gap);
        parent.addView(view, p);
    }

    /** Adds body text with the muted token and a predictable line height. */
    private void paragraph(LinearLayout parent, CharSequence value, float sp, int gap) {
        block(parent, text(value, sp, false, 400, R.color.scenevibe_muted_foreground), gap);
    }

    /** Adds the green uppercase eyebrow from the reference. */
    private void eyebrow(LinearLayout parent, String value) {
        TextView label = text(value.toUpperCase(Locale.FRENCH), 11, false, 650, R.color.scenevibe_primary);
        label.setLetterSpacing(.045f);
        block(parent, label, 0);
    }

    /** Creates a page introduction whose title remains visible on first entry. */
    private void introduction(String eyebrow, String title, String detail) {
        LinearLayout intro = ui.column();
        eyebrow(intro, eyebrow);
        block(intro, text(title, 32, true, 650, R.color.scenevibe_foreground), 7);
        TextView description = text(detail, 14, false, 400, R.color.scenevibe_muted_foreground);
        description.setMaxWidth(ui.dp(520));
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-2, -2);
        descriptionParams.topMargin = ui.dp(7);
        intro.addView(description, descriptionParams);
        block(page, intro, 14);
    }

    /** Keeps demo provenance visible on every content surface without a simulated connection. */
    private void demoNotice(LinearLayout parent) {
        if (!source.isLocalDemo()) return;
        LinearLayout row = ui.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8));
        row.setBackground(ui.panel(R.color.scenevibe_secondary, 0, 8));
        ImageView info = icon("info", R.color.scenevibe_primary, 15);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(ui.dp(15), ui.dp(15));
        infoParams.rightMargin = ui.dp(7);
        row.addView(info, infoParams);
        TextView notice = text(s(R.string.tv_demo_notice), 12, false, 400, R.color.scenevibe_secondary_foreground);
        row.addView(notice, new LinearLayout.LayoutParams(0, -2, 1));
        block(parent, row, 14);
    }

    /** Builds a neutral or error panel, with actual text rather than color-only meaning. */
    private LinearLayout state(String title, String message, boolean error) {
        LinearLayout panel = ui.column();
        panel.setPadding(ui.dp(24), ui.dp(22), ui.dp(24), ui.dp(22));
        panel.setBackground(error ? ui.accentPanel(R.color.scenevibe_card, 10)
                : ui.panel(R.color.scenevibe_muted, 0, 10));
        if (error) panel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        if (error) panel.addView(icon("circle-off", R.color.scenevibe_primary, 26),
                new LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)));
        block(panel, text(title, 20, true, 650, R.color.scenevibe_foreground), error ? 8 : 0);
        paragraph(panel, message, 14, 8);
        return panel;
    }

    /** Records one stable Activity-local focus target and handles only directional keys on it. */
    private void target(View view, String key) {
        view.setId(View.generateViewId());
        view.setTag(key);
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
        targets.put(key, view);
        view.setOnFocusChangeListener((focused, hasFocus) -> {
            if (key.startsWith("card-")) focused.setElevation(hasFocus ? ui.dp(8) : 0);
            root.invalidate();
            if (focused.getParent() instanceof View) ((View) focused.getParent()).invalidate();
            if (hasFocus && !key.startsWith("nav-") && !restoring) ensureVisible(focused);
        });
        view.setOnKeyListener((focused, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            TvFocusSearch.Direction direction;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT: direction = TvFocusSearch.Direction.LEFT; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: direction = TvFocusSearch.Direction.RIGHT; break;
                case KeyEvent.KEYCODE_DPAD_UP: direction = TvFocusSearch.Direction.UP; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: direction = TvFocusSearch.Direction.DOWN; break;
                default: return false;
            }
            moveFocus(key, direction);
            return true;
        });
    }

    /** Scrolls only the content container, leaving shell/menu and Android Home untouched. */
    private void ensureVisible(View view) {
        if (!isInsidePage(view)) return;
        Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
        page.offsetDescendantRectToMyCoords(view, rect);
        rect.inset(0, -ui.dp(24));
        scroll.requestChildRectangleOnScreen(page, rect, true);
    }

    /** Checks View ancestry without assuming the target belongs to the main scroll container. */
    private boolean isInsidePage(View view) {
        for (android.view.ViewParent p = view.getParent(); p != null; p = p.getParent()) if (p == page) return true;
        return false;
    }

    /** Uses the frozen spatial score, with explicit menu return-to-content memory. */
    private void moveFocus(String key, TvFocusSearch.Direction direction) {
        if (key.startsWith("nav-") && direction == TvFocusSearch.Direction.RIGHT) {
            String remembered = navigation.current().focus;
            if (remembered == null || remembered.startsWith("nav-") || !requestFocus(remembered)) requestFocus(defaultFocus);
            return;
        }
        List<TvFocusSearch.Target> geometry = new ArrayList<>();
        TvFocusSearch.Target current = null;
        for (Map.Entry<String, View> entry : targets.entrySet()) {
            View view = entry.getValue();
            int[] point = new int[2]; view.getLocationInWindow(point);
            TvFocusSearch.Target t = new TvFocusSearch.Target(entry.getKey(), point[0], point[1],
                    point[0] + view.getWidth(), point[1] + view.getHeight(), view.isShown() && view.isEnabled());
            geometry.add(t);
            if (entry.getKey().equals(key)) current = t;
        }
        if (current == null) return;
        String next = TvFocusSearch.next(current, geometry, direction, "nav-" + navigation.activeSection().name());
        if (next != null) {
            if (!key.startsWith("nav-") && next.startsWith("nav-")) navigation.current().focus = key;
            requestFocus(next);
        }
    }

    /** Requests an existing enabled target; absent cards never fall back to another Track id. */
    private boolean requestFocus(String key) {
        View view = targets.get(key);
        return view != null && view.isShown() && view.isEnabled() && view.requestFocus();
    }

    /** Preserves the current visit before an Activity or route transition. */
    public void remember() {
        View focused = root.findFocus();
        String key = focused != null && focused.getTag() instanceof String ? (String) focused.getTag() : navigation.current().focus;
        navigation.remember(key, scroll.getScrollY());
    }

    /** Restores focus after permission settings or the existing DiagnosticsActivity returns. */
    public void resume() {
        if (source instanceof TvAccountSource) ((TvAccountSource) source).resume();
        refreshRuntime();
        root.post(() -> restoreFocus(true));
    }

    /** Invalidate Activity-owned network callbacks before pause; no service is affected. */
    public void pause() {
        remember();
        if (source instanceof TvAccountSource) ((TvAccountSource) source).pause();
    }

    /** Release only the account source owned by this Activity. */
    public void close() {
        if (source instanceof TvAccountSource) ((TvAccountSource) source).close();
    }

    /** Reads existing status only; the 1s legacy status refresh never rebuilds or steals focus. */
    public void refreshRuntime() {
        RuntimeStatus status = runtime.read();
        String cloud = source instanceof TvAccountSource ? ((TvAccountSource) source).accountMessage() : status.cloud;
        if (source instanceof TvAccountSource) connection.setText(((TvAccountSource) source).accountStage() == TvAccountSource.AccountStage.ASSOCIATED
                ? (BuildConfig.CLOUD_MULTIMEDIA_SERVICE?"Compte associé · Qualification TEXT + AUDIO":
                    BuildConfig.TVFIRST_TEXT_SONY_QUALIFICATION ? "Compte associé · Qualification TEXT" : "Compte associé · Cache uniquement") : "Compte non confirmé");
        if (accountStatus != null) accountStatus.setText(cloud);
        if (diagnosticConnection != null) diagnosticConnection.setText(cloud);
        if (diagnosticService != null) diagnosticService.setText(status.service + "\n" + status.permissions);
        if (diagnosticDevice != null) diagnosticDevice.setText(status.device);
    }

    /** Lets Back leave home normally and otherwise restores the previous actual visit. */
    public boolean back() {
        remember();
        if (!navigation.back()) return false;
        render();
        return true;
    }

    /** Rebuilds only presentation Views; no service or durable store is owned or cleared here. */
    private void render() {
        restoring = true;
        if (runtimeControls.getParent() instanceof ViewGroup) ((ViewGroup) runtimeControls.getParent()).removeView(runtimeControls);
        page.removeAllViews(); targets.clear();
        accountStatus = null; diagnosticConnection = null; diagnosticService = null; diagnosticDevice = null;
        for (Map.Entry<TvNavigation.Screen, Button> entry : menu.entrySet()) {
            Button button = entry.getValue(); boolean selected = entry.getKey() == navigation.activeSection();
            button.setSelected(selected);
            button.setBackground(ui.focusBackground(selected ? R.color.scenevibe_sidebar_accent : R.color.scenevibe_sidebar,
                    0, 8, selected));
            button.setTextColor(ui.color(selected ? R.color.scenevibe_sidebar_accent_foreground : R.color.scenevibe_sidebar_foreground));
            target(button, "nav-" + entry.getKey().name());
        }
        defaultFocus = "nav-" + navigation.activeSection().name();
        switch (navigation.current().screen) {
            case HOME: home(); break;
            case TRACKS: tracks(); break;
            case TRACK: detail(); break;
            case PREPARE: prepare(); break;
            case SETTINGS: settings(); break;
            case DIAGNOSTICS: diagnostics(); break;
        }
        refreshRuntime();
        root.post(() -> restoreFocus(false));
    }

    /** Restores focus before scroll so first-entry actions cannot hide their page heading. */
    private void restoreFocus(boolean returningActivity) {
        restoring = true;
        String memory = navigation.current().focus;
        if (!requestFocus(memory) && !requestFocus(defaultFocus)) requestFocus("nav-" + navigation.activeSection().name());
        scroll.scrollTo(0, navigation.current().scroll);
        restoring = false;
        View focused = root.findFocus();
        if (memory == null && focused != null && isInsidePage(focused) && focused.getHeight() < scroll.getHeight() - ui.dp(48)) {
            int[] point = new int[2], viewport = new int[2];
            focused.getLocationInWindow(point); scroll.getLocationInWindow(viewport);
            if (point[1] < viewport[1] || point[1] + focused.getHeight() > viewport[1] + scroll.getHeight()) ensureVisible(focused);
        }
    }

    /** Opens a detail/preparation visit after storing exact card and scroll memory. */
    private void open(TvNavigation.Screen screen, String id) {
        remember(); navigation.push(screen, id); render();
    }

    /** Returns to Mes Tracks on the actual offer card, retaining that visit's scroll. */
    private void returnToTracks(String id) { remember(); navigation.tracks(id); render(); }

    /** Adds an accessible action with stable focus identity. */
    private Button action(LinearLayout parent, int label, boolean primary, String key, Runnable command) {
        Button button = ui.button(s(label), primary, command);
        String name = label == R.string.tv_prepare_action ? "monitor"
                : label == R.string.tv_back_tracks ? "arrow_left"
                : label == R.string.tv_retry ? "rotate_ccw"
                : label == R.string.tv_cancel ? "x"
                : label == R.string.tv_diag_refresh ? "refresh_cw"
                : label == R.string.tv_back_home && key.startsWith("prepare") ? "house" : null;
        if (name != null) {
            android.graphics.drawable.Drawable glyph = activity.getDrawable(drawable("tv_icon_" + name));
            if (glyph != null) {
                glyph.setBounds(0, 0, ui.dp(16), ui.dp(16));
                button.setCompoundDrawables(glyph, null, null, null);
                button.setCompoundDrawablePadding(ui.dp(8));
                button.setCompoundDrawableTintList(ui.buttonTextColors(primary));
            }
        }
        target(button, key);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        if (parent.getChildCount() > 0) p.topMargin = ui.dp(8);
        parent.addView(button, p);
        return button;
    }

    /** Retains the reference edition glyph beside the authored metadata. */
    private View editionLine(TvOffer offer, float sp) {
        LinearLayout row = ui.row();
        ImageView layers = icon("layers", R.color.scenevibe_primary, (int) sp);
        LinearLayout.LayoutParams glyph = new LinearLayout.LayoutParams(ui.dp(sp), ui.dp(sp));
        glyph.rightMargin = ui.dp(6); glyph.topMargin = ui.dp(2);
        row.addView(layers, glyph);
        row.addView(text(offer.edition, sp, false, 400, R.color.scenevibe_primary), new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    /** Draws the editorial image or an honest text fallback; no remote image is fetched. */
    private View image(TvOffer offer, boolean wide, boolean withdrawn) {
        FrameLayout frame = new FrameLayout(activity);
        frame.setBackgroundColor(ui.color(R.color.scenevibe_secondary));
        String file = offer.illustration == null ? "" : offer.illustration.substring(offer.illustration.lastIndexOf('/') + 1);
        int id = drawable("editorial_" + file.replace(".svg", "").replace('-', '_'));
        if (id != 0) {
            ImageView image = new ImageView(activity);
            image.setImageResource(id); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setContentDescription(s(R.string.tv_illustration_content, offer.title));
            if (withdrawn) {
                ColorMatrix matrix = new ColorMatrix(); matrix.setSaturation(.15f);
                image.setColorFilter(new ColorMatrixColorFilter(matrix)); image.setAlpha(.7f);
            }
            frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView fallback = text(offer.title + "\n" + s(R.string.tv_image_missing), 16, true, 600, R.color.scenevibe_primary);
            fallback.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16));
            frame.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
        }
        if (wide) {
            TextView label = text(offer.visualLabel, 11, false, 600, R.color.scenevibe_primary);
            label.setPadding(ui.dp(7), ui.dp(3), ui.dp(7), ui.dp(3));
            label.setBackground(ui.panel(R.color.scenevibe_card, 0, 3));
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
            p.leftMargin = ui.dp(9); p.bottomMargin = ui.dp(9); frame.addView(label, p);
        }
        frame.setMinimumHeight(ui.dp(170));
        return frame;
    }

    /** Displays readiness only from the runtime binding, or unmistakably as a local demonstration. */
    private String cardStatus(TvOffer offer) {
        if (!offer.available) return s(R.string.tv_unavailable);
        TvPreparation prep = source.preparation(offer.id);
        if (prep.readyFor(offer.id)) return source instanceof TvAccountSource ? "Cache confirmé" : s(R.string.tv_ready);
        if (source.isLocalDemo() && prep.demonstration && prep.stage == TvPreparation.Stage.READY) return s(R.string.tv_demo_ready);
        if (prep.stage != TvPreparation.Stage.NONE && prep.stage != TvPreparation.Stage.FAILED) return s(R.string.tv_in_progress);
        return source instanceof TvAccountSource ? "Ajout à vérifier" : s(R.string.tv_available);
    }

    /** Reuses the wide/recap TrackCard hierarchy and blocks child focus inside the clickable card. */
    private View card(TvOffer offer, boolean wide, boolean interactive) {
        FrameLayout outer = new FrameLayout(activity);
        outer.setClipChildren(false); outer.setClipToPadding(false);
        LinearLayout card = wide ? ui.row() : ui.column();
        card.setBackground(ui.panel(R.color.scenevibe_card, R.color.scenevibe_border, 12));
        card.setClipToOutline(true);
        View illustration = image(offer, true, !offer.available);
        card.addView(illustration, wide ? new LinearLayout.LayoutParams(d(R.dimen.tv_image_width), -1)
                : new LinearLayout.LayoutParams(-1, ui.dp(158)));
        LinearLayout copy = ui.column();
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(ui.dp(wide ? 20 : 14), ui.dp(12), ui.dp(wide ? 20 : 14), ui.dp(12));
        card.addView(copy, wide ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2));
        if (offer.channel != null) {
            LinearLayout author = ui.row(); author.setGravity(Gravity.CENTER_VERTICAL);
            String[] words = offer.channel.trim().split("\\s+");
            String initials = "";
            for (int i = 0; i < Math.min(2, words.length); i++) if (!words[i].isEmpty()) initials += words[i].substring(0, 1);
            TextView avatar = text(initials.toUpperCase(Locale.FRENCH), 11, false, 700, R.color.scenevibe_primary);
            avatar.setGravity(Gravity.CENTER);
            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(ui.color(R.color.scenevibe_secondary)); avatar.setBackground(circle);
            avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            author.addView(avatar, new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)));
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1); nameParams.leftMargin = ui.dp(7);
            author.addView(text(offer.channel, 12, false, 400, R.color.scenevibe_muted_foreground), nameParams);
            block(copy, author, 0);
        }
        block(copy, text(offer.title, wide ? 22 : 18, true, 650,
                offer.available ? R.color.scenevibe_foreground : R.color.scenevibe_muted_foreground), 6);
        TextView description = text(offer.description, wide ? 13 : 12, false, 400, R.color.scenevibe_muted_foreground);
        description.setMaxLines(3); description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        block(copy, description, 6);
        block(copy, text(offer.filmTitle, 12, false, 600, R.color.scenevibe_foreground), 6);
        block(copy, editionLine(offer, 12), 6);
        TextView chip = text(s(R.string.tv_format), 11, false, 650, R.color.scenevibe_primary);
        chip.setPadding(ui.dp(7), ui.dp(2), ui.dp(7), ui.dp(2));
        chip.setBackground(ui.panel(R.color.scenevibe_card, R.color.scenevibe_border, 4));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, -2); chipParams.topMargin = ui.dp(6);
        copy.addView(chip, chipParams);
        View line = new View(activity); line.setBackgroundColor(ui.color(R.color.scenevibe_border));
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, ui.dp(1));
        lineParams.topMargin = ui.dp(9); copy.addView(line, lineParams);
        block(copy, text((offer.available ? "✓  " : "⊘  ") + cardStatus(offer), 12, false, 650,
                offer.available ? R.color.scenevibe_primary : R.color.scenevibe_foreground), 9);
        TvPreparation prep = source.preparation(offer.id);
        int statusDetail = !offer.available ? R.string.tv_unavailable_detail
                : prep.stage == TvPreparation.Stage.READY && source.isLocalDemo() ? R.string.tv_demo_ready_detail
                : prep.stage == TvPreparation.Stage.READY && prep.readyFor(offer.id) ? R.string.tv_stage_ready_detail
                : prep.stage != TvPreparation.Stage.NONE && prep.stage != TvPreparation.Stage.FAILED
                        ? R.string.tv_in_progress_detail : R.string.tv_available_detail;
        paragraph(copy, source instanceof TvAccountSource ? (prep.readyFor(offer.id)
                ? "Cache durable et reçu Cloud concordants. Aucun rendu ni MEDIA."
                : "La distributabilité sera vérifiée lors de l’ajout.") : s(statusDetail), 12, 3);
        outer.addView(card, new FrameLayout.LayoutParams(-1, -2));
        if (interactive) {
            outer.setBackground(ui.focusBackground(R.color.scenevibe_card, 0, 12, false));
            outer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            outer.setContentDescription(offer.title + ". " + offer.filmTitle + ". " + cardStatus(offer));
            outer.setOnClickListener(v -> {
                if (source instanceof TvAccountSource) ((TvAccountSource) source).inspect(offer.id);
                open(TvNavigation.Screen.TRACK, offer.id);
            });
            target(outer, "card-" + offer.id);
        }
        return outer;
    }

    /** Renders the reference introduction and one featured card without manufacturing empty content. */
    private void home() {
        if (accountPanel()) return;
        demoNotice(page);
        introduction(s(R.string.tv_home_eyebrow), s(R.string.tv_home_title), s(R.string.tv_home_intro));
        LinearLayout section = ui.column();
        block(section, text(s(R.string.tv_home_section), 22, true, 650, R.color.scenevibe_foreground), 0);
        paragraph(section, s(source.isLocalDemo() ? R.string.tv_home_section_intro : R.string.tv_empty_real), 13, 3);
        List<TvOffer> offers = source.offers();
        if (offers.isEmpty()) {
            block(section, state(s(R.string.tv_empty_title), s(source.isLocalDemo() ? R.string.tv_empty_demo : R.string.tv_empty_real), false), 10);
            defaultFocus = "home-tracks";
        } else {
            TvOffer first = offers.get(0);
            block(section, card(first, true, true), 10);
            defaultFocus = "card-" + first.id;
        }
        block(page, section, 14);
        action(page, R.string.tv_home_tracks, false, "home-tracks", () -> { remember(); navigation.section(TvNavigation.Screen.TRACKS); render(); });
    }

    /** Shows real absence explicitly and retains withdrawn cards as consultable but not preparable. */
    private void tracks() {
        if (accountPanel()) return;
        introduction(s(R.string.tv_tracks), s(R.string.tv_tracks_title), s(R.string.tv_tracks_intro));
        List<TvOffer> offers = source.offers();
        if (offers.isEmpty()) {
            block(page, state(s(R.string.tv_empty_title), s(source.isLocalDemo() ? R.string.tv_empty_demo : R.string.tv_empty_real), false), 14);
            action(page, R.string.tv_back_home, false, "tracks-empty-back", () -> { remember(); navigation.section(TvNavigation.Screen.HOME); render(); });
            defaultFocus = "tracks-empty-back";
        } else {
            paragraph(page, activity.getResources().getQuantityString(source.isLocalDemo() ? R.plurals.tv_summary_demo : R.plurals.tv_summary_real, offers.size(), offers.size()), 12, 14);
            for (TvOffer offer : offers) block(page, card(offer, true, true), 16);
            defaultFocus = "card-" + offers.get(0).id;
        }
        if (source instanceof TvAccountSource) {
            TvAccountSource account = (TvAccountSource) source;
            accountAction(page, "Actualiser Mes Tracks", "tracks-refresh", account::refresh);
            accountAction(page, "Abandonner la demande d’ajout en attente", "tracks-cancel-pending", () -> source.cancel(""));
            if (account.hasMore()) accountAction(page, "Afficher la suite", "tracks-more", account::loadMore);
        }
    }

    /** Reuse native focus targets for explicit account operations; no timer advances success. */
    private void accountAction(LinearLayout parent, String label, String key, Runnable run) {
        Button button = action(parent, R.string.tv_retry, false, key, run);
        button.setText(label);
        button.setEnabled(!((TvAccountSource) source).busy());
    }

    /** Explain real account state and an explicit human code, with no fixture or automatic TV creation. */
    private boolean accountPanel() {
        if (!(source instanceof TvAccountSource)) return false;
        TvAccountSource account = (TvAccountSource) source;
        if (account.accountStage() == TvAccountSource.AccountStage.ASSOCIATED) return false;
        introduction("Compte SceneVibe", account.accountStage() == TvAccountSource.AccountStage.ERROR ? "Vérifier la connexion et le compte" : "Associer ma TV", account.accountMessage());
        if (!account.verification().isEmpty()) {
            TextView code = text(account.verification(), 24, true, 650, R.color.scenevibe_foreground);
            code.setTag("account-human-code"); block(page, code, 16);
            paragraph(page, "Saisis ce code uniquement sur le site SceneVibe indiqué. Ne partage jamais ce code.", 13, 12);
            accountAction(page, "Vérifier la confirmation", "account-check", account::refresh);
            accountAction(page, "Abandonner ce code", "account-cancel", account::cancelPairing);
            defaultFocus = "account-check";
        } else if (account.accountStage() != TvAccountSource.AccountStage.UNCONFIGURED) {
            paragraph(page, "Sur le site, tu peux créer ton compte ou te connecter. Les anciennes Tracks d’un autre compte ne seront pas réattribuées.", 14, 12);
            accountAction(page, "Nouvelle association", "account-new", () -> account.beginPairing(false));
            accountAction(page, "Récupérer une TV de mon compte", "account-recover", () -> account.beginPairing(true));
            accountAction(page, "Vérifier / Réessayer", "account-check", account::refresh);
            accountAction(page, "Abandonner le code en attente", "account-cancel", account::cancelPairing);
            defaultFocus = "account-new";
        } else {
            action(page, R.string.tv_diag_details, false, "account-diagnostics", openTechnicalDiagnostics);
            defaultFocus = "account-diagnostics";
        }
        paragraph(page, BuildConfig.CLOUD_MULTIMEDIA_SERVICE?
                "L'association autorise le téléchargement privé. Les commentaires nécessitent ensuite une activation locale explicite.":
                "Ce profil consulte et prépare le cache. Aucun rendu de commentaire, overlay ou MEDIA n’est autorisé.", 13, 16);
        return true;
    }

    /** Require a human confirmation before the trusted adapter may request an assignment. */
    private void confirmSelection(TvOffer offer) {
        new AlertDialog.Builder(activity).setTitle("Ajouter à ma TV ?")
                .setMessage("Cette sélection remplacera l’affectation courante de cette TV. Seul le cache sera préparé ; aucun commentaire ne démarrera.")
                .setPositiveButton("Ajouter à ma TV", (dialog, which) -> {
                    try { source.select(offer.id); open(TvNavigation.Screen.PREPARE, offer.id); }
                    catch (IllegalStateException | IllegalArgumentException refused) { render(); }
                }).setNegativeButton("Annuler", (dialog, which) -> dialog.dismiss())
                .setOnDismissListener(dialog -> root.post(() -> restoreFocus(true))).show();
    }

    /** Resolves only the explicitly routed offer, refusing an unknown or disappeared reference. */
    private TvOffer routedOffer() {
        if (accountPanel()) return null;
        for (TvOffer offer : source.offers()) if (offer.id.equals(navigation.current().offerId)) return offer;
        introduction(s(R.string.tv_tracks), s(R.string.tv_error_missing), s(R.string.tv_error_missing_detail));
        action(page, R.string.tv_back_tracks, false, "missing-back", () -> returnToTracks(navigation.current().offerId));
        defaultFocus = "missing-back";
        return null;
    }

    /** Creates the two native columns; the side column retains the reference's 280dp width. */
    private LinearLayout[] columns() {
        LinearLayout row = ui.row();
        LinearLayout main = ui.column(), side = ui.column();
        row.addView(main, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(d(R.dimen.tv_side_column), -2);
        p.leftMargin = d(R.dimen.tv_column_gap); row.addView(side, p);
        block(page, row, 14);
        return new LinearLayout[] {main, side};
    }

    /** Transposes the identity, availability and description panels; selection has no runtime authority. */
    private void detail() {
        TvOffer offer = routedOffer(); if (offer == null) return;
        LinearLayout[] columns = columns(); LinearLayout main = columns[0], side = columns[1];
        LinearLayout hero = ui.column();
        hero.setBackground(ui.panel(R.color.scenevibe_card, R.color.scenevibe_border, 16));
        hero.setClipToOutline(true);
        // The flexible left column is 372dp at the accepted 960dp viewport (16:7 hero).
        hero.addView(image(offer, false, false), new LinearLayout.LayoutParams(-1, ui.dp(163)));
        LinearLayout caption = ui.column(); caption.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10));
        paragraph(caption, s(R.string.tv_format) + "   " + offer.visualLabel, 12, 0);
        paragraph(caption, offer.origin, 12, 6); block(hero, caption, 0); block(main, hero, 0);
        LinearLayout identity = ui.column(); eyebrow(identity, s(R.string.tv_track_eyebrow));
        block(identity, text(offer.title, 28, true, 650, R.color.scenevibe_foreground), 4);
        block(identity, text(offer.filmTitle, 16, false, 500, R.color.scenevibe_foreground), 8);
        block(identity, editionLine(offer, 14), 5);
        paragraph(identity, source instanceof TvAccountSource ? "Langue de la référence : " + offer.language + " · Langue du commentaire : non démontrée" : s(R.string.tv_language_line, offer.language), 12, 5);
        if (offer.channel != null) {
            paragraph(identity, s(R.string.tv_channel_author), 12, 12);
            block(identity, state(offer.channel, source instanceof TvAccountSource ? "Information fournie par le compte ; absente si non renseignée." : s(R.string.tv_channel_demo), false), 6);
        }
        block(main, identity, 16);
        if (!offer.available) block(main, state(s(R.string.tv_selection_impossible), s(R.string.tv_unavailable_detail), true), 16);
        if (offer.available) {
            TvPreparation prep = source.preparation(offer.id);
            int label = prep.stage == TvPreparation.Stage.READY ? R.string.tv_view_preparation
                    : prep.stage == TvPreparation.Stage.NONE || prep.stage == TvPreparation.Stage.FAILED
                            ? R.string.tv_prepare_action : R.string.tv_follow_preparation;
            Button primary = action(main, label, true, "track-primary", () -> {
                if (source instanceof TvAccountSource && source.preparation(offer.id).stage != TvPreparation.Stage.READY) {
                    confirmSelection(offer); return;
                }
                try { if (source.preparation(offer.id).stage == TvPreparation.Stage.NONE
                        || source.preparation(offer.id).stage == TvPreparation.Stage.FAILED) source.select(offer.id);
                    open(TvNavigation.Screen.PREPARE, offer.id);
                } catch (IllegalArgumentException | IllegalStateException unavailable) { render(); }
            });
            if (source instanceof TvAccountSource) {
                primary.setText(prep.readyFor(offer.id) ? "Voir la confirmation du cache" : "Ajouter à ma TV");
                primary.setEnabled(!((TvAccountSource) source).busy());
            }
        }
        action(main, R.string.tv_back_tracks, false, "track-back", () -> returnToTracks(offer.id));
        LinearLayout availability = state(s(R.string.tv_availability), "", false);
        availability.setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16));
        availability.setBackground(ui.accentPanel(R.color.scenevibe_muted, 10));
        ui.typography((TextView) availability.getChildAt(0), 16, true, 650);
        // The empty body from the generic panel is replaced by the actual four availability rows.
        availability.removeViewAt(1);
        paragraph(availability, (offer.available ? "✓  " : "⊘  ") + (source instanceof TvAccountSource ? "FinalTrack V1 : validation lors de l’ajout" : s(offer.available ? R.string.tv_available_format : R.string.tv_track_unavailable)), 12, 0);
        paragraph(availability, source instanceof TvAccountSource ? ((TvAccountSource) source).accountMessage() : s(source.isLocalDemo() ? R.string.tv_connection_demo : R.string.tv_unconnected_header), 12, 8);
        paragraph(availability, s(R.string.tv_no_activation), 12, 8);
        paragraph(availability, s(R.string.tv_admission_boundary), 12, 8);
        block(side, availability, 0);
        LinearLayout description = ui.column();
        description.setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18));
        description.setBackground(ui.panel(R.color.scenevibe_card, R.color.scenevibe_border, 14));
        eyebrow(description, s(R.string.tv_description_eyebrow));
        block(description, text(s(R.string.tv_description), 22, true, 650, R.color.scenevibe_foreground), 4);
        paragraph(description, offer.longDescription, 13, 11);
        paragraph(description, s(R.string.tv_media_edition) + "\n" + offer.filmTitle + " · " + offer.edition, 12, 13);
        paragraph(description, s(R.string.tv_experience) + "\n" + s(R.string.tv_experience_text), 12, 7);
        paragraph(description, s(R.string.tv_metadata_language) + "\n" + offer.language, 12, 7);
        block(side, description, 16);
        preview(offer);
        defaultFocus = offer.available ? "track-primary" : "track-back";
    }

    /** Keeps the excerpt switch local and restores focus/scroll after it, with no renderer call. */
    private void preview(TvOffer offer) {
        LinearLayout panel = ui.column(); panel.setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(20));
        panel.setBackground(ui.panel(R.color.scenevibe_card, R.color.scenevibe_border, 18));
        eyebrow(panel, s(R.string.tv_preview_eyebrow));
        block(panel, text(s(R.string.tv_preview), 26, true, 650, R.color.scenevibe_foreground), 7);
        paragraph(panel, s(R.string.tv_preview_boundary), 12, 8);
        if (!offer.available || offer.previews.isEmpty()) {
            block(panel, state(s(R.string.tv_no_preview), s(R.string.tv_no_preview_detail), false), 18);
        } else {
            paragraph(panel, s(R.string.tv_preview_intro), 14, 18);
            TvOffer.Preview selected = null;
            for (TvOffer.Preview entry : offer.previews) if (entry.id.equals(navigation.current().preview)) selected = entry;
            if (selected == null) selected = offer.previews.get(0);
            LinearLayout content = ui.row(), list = ui.column(), excerpt = ui.column();
            content.addView(list, new LinearLayout.LayoutParams(ui.dp(280), -2));
            LinearLayout.LayoutParams excerptParams = new LinearLayout.LayoutParams(0, -2, 1); excerptParams.leftMargin = ui.dp(20);
            content.addView(excerpt, excerptParams);
            for (TvOffer.Preview entry : offer.previews) {
                Button item = action(list, R.string.tv_preview_selection, false, "preview-" + entry.id, () -> {
                    remember(); navigation.current().preview = entry.id; render();
                });
                item.setText(entry.label + "\nTEXT · " + entry.position); item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                item.setMinHeight(ui.dp(56)); item.setSelected(selected.id.equals(entry.id));
                if (selected.id.equals(entry.id)) item.setBackground(ui.focusBackground(R.color.scenevibe_secondary,
                        R.color.scenevibe_primary, 9, true));
                item.setContentDescription(entry.label + ". TEXT. " + entry.position);
            }
            excerpt.setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18));
            excerpt.setBackground(ui.panel(R.color.scenevibe_background, R.color.scenevibe_border, 12));
            paragraph(excerpt, "TEXT · " + selected.position, 11, 0);
            paragraph(excerpt, s(R.string.tv_preview_safe), 11, 10);
            block(excerpt, text(selected.title, 20, true, 650, R.color.scenevibe_foreground), 7);
            paragraph(excerpt, selected.context, 12, 7);
            block(excerpt, text(selected.text, 15, false, 400, R.color.scenevibe_foreground), 12);
            block(panel, content, 18);
        }
        paragraph(panel, source.isLocalDemo() ? s(R.string.tv_corpussynthetic, offer.edition, offer.revision)
                : offer.edition + " · " + offer.revision, 11, 18);
        block(page, panel, 14);
    }

    /** Maps the authored failure messages without retrying the transport or inventing success. */
    private int[] failureCopy(TvPreparation.Failure failure) {
        if (failure == null) failure = TvPreparation.Failure.PREPARATION_FAILED;
        switch (failure) {
            case CONNECTION_UNAVAILABLE: return new int[] {R.string.tv_connection_unavailable, R.string.tv_error_connection_detail};
            case REQUEST_EXPIRED: return new int[] {R.string.tv_error_expired, R.string.tv_error_expired_detail};
            case CONFIRMATION_REFUSED: return new int[] {R.string.tv_error_refused, R.string.tv_error_refused_detail};
            case TRACK_UNAVAILABLE: return new int[] {R.string.tv_track_unavailable, R.string.tv_error_withdrawn_detail};
            default: return new int[] {R.string.tv_error_preparation, R.string.tv_error_preparation_detail};
        }
    }

    /** Renders all preparation variants from explicit facts, without a transition timer. */
    private void prepare() {
        TvOffer offer = routedOffer(); if (offer == null) return;
        TvPreparation prep = source.preparation(offer.id);
        boolean demo = source.isLocalDemo() && prep.demonstration;
        boolean ready = prep.readyFor(offer.id) || demo && prep.stage == TvPreparation.Stage.READY;
        boolean unverified = prep.stage == TvPreparation.Stage.READY && !ready;
        boolean failed = prep.stage == TvPreparation.Stage.FAILED || unverified || !offer.available;
        int[] titles = {R.string.tv_stage_requested, R.string.tv_stage_waiting, R.string.tv_stage_preparing, R.string.tv_ready};
        int index = prep.stage.ordinal() - 1;
        int[] copy = unverified ? new int[] {R.string.tv_error_unverified, R.string.tv_error_unverified_detail}
                : !offer.available ? failureCopy(TvPreparation.Failure.TRACK_UNAVAILABLE) : failureCopy(prep.failure);
        String title = failed ? s(copy[0]) : prep.stage == TvPreparation.Stage.NONE
                ? s(R.string.tv_no_preparation) : s(titles[Math.max(0, Math.min(index, 3))]);
        if (demo && !failed && prep.stage != TvPreparation.Stage.NONE) title += s(R.string.tv_demo_stage_suffix);
        String message = failed ? s(copy[1]) : prep.stage == TvPreparation.Stage.NONE ? s(R.string.tv_no_preparation_detail)
                : demo ? s(R.string.tv_demo_stage_detail) : s(new int[] {R.string.tv_stage_requested_detail,
                    R.string.tv_stage_waiting_detail, R.string.tv_stage_preparing_detail, R.string.tv_stage_ready_detail}[Math.max(0, Math.min(index, 3))]);
        if (source instanceof TvAccountSource && !failed) {
            title = ready ? "Cache confirmé" : "Préparation du cache";
            message = ready ? "La sélection, l’affectation, les octets relus et le reçu Cloud concordent. Aucun rendu ni MEDIA n’est armé."
                    : "La confirmation dépend des réponses du service et de la relecture durable du cache.";
        }
        introduction(s(R.string.tv_preparation_eyebrow, offer.title), title, message);
        LinearLayout[] columns = columns(); LinearLayout main = columns[0], recap = columns[1];
        if (failed) {
            block(main, state(s(R.string.tv_not_ready), s(prep.failure == TvPreparation.Failure.TRACK_UNAVAILABLE
                    || !offer.available ? R.string.tv_no_retry_detail : R.string.tv_retry_detail), true), 0);
        } else if (prep.stage != TvPreparation.Stage.NONE) {
            for (int i = 0; i < 4; i++) {
                boolean done = i < index || ready && i == index;
                boolean current = i == index;
                LinearLayout step = ui.row(); step.setGravity(Gravity.CENTER_VERTICAL);
                step.setPadding(ui.dp(15), ui.dp(9), ui.dp(15), ui.dp(9)); step.setMinimumHeight(d(R.dimen.tv_step_height));
                step.setBackground(ui.focusBackground(current ? R.color.scenevibe_secondary : R.color.scenevibe_card,
                        current ? R.color.scenevibe_primary : R.color.scenevibe_border, 9, current));
                step.addView(icon(done ? "check" : "circle-dashed", done || current ? R.color.scenevibe_primary : R.color.scenevibe_muted_foreground, 20),
                        new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
                LinearLayout words = ui.column();
                LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(0, -2, 1); wordsParams.leftMargin = ui.dp(11);
                step.addView(words, wordsParams);
                block(words, text((source instanceof TvAccountSource && i == 3 ? "Cache confirmé" : s(titles[i])) + (demo && i == 3 ? s(R.string.tv_demo_stage_suffix) : ""), 15, false, 650,
                        done || current ? R.color.scenevibe_foreground : R.color.scenevibe_muted_foreground), 0);
                paragraph(words, s(done ? R.string.tv_done : current ? R.string.tv_current : R.string.tv_todo), 12, 0);
                block(main, step, 7);
            }
            if (ready) {
                LinearLayout recognition = state(s(prep.mediaAdmittedFor(offer.id) ? R.string.tv_program_admitted : R.string.tv_no_program),
                        source instanceof TvAccountSource ? "Ce cache n’autorise aucun commentaire, overlay, transport ni synchronisation MEDIA." : s(prep.mediaAdmittedFor(offer.id) ? R.string.tv_program_admitted_detail : R.string.tv_no_program_detail), false);
                recognition.setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16));
                recognition.setBackground(ui.accentPanel(R.color.scenevibe_muted, 10));
                ui.typography((TextView) recognition.getChildAt(0), 16, true, 650);
                ui.typography((TextView) recognition.getChildAt(1), 12, false, 400);
                ((TextView) recognition.getChildAt(1)).setTextColor(ui.color(R.color.scenevibe_muted_foreground));
                block(main, recognition, 16);
            }
            else {
                android.widget.ProgressBar progress = new android.widget.ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
                progress.setIndeterminate(true); progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ui.color(R.color.scenevibe_primary)));
                progress.setContentDescription(s(R.string.tv_stage_preparing));
                LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, ui.dp(6));
                progressParams.topMargin = ui.dp(12);
                main.addView(progress, progressParams);
            }
        }
        TvActionRow actions = new TvActionRow(activity);
        block(main, actions, 16);
        if (ready) {
            action(actions, R.string.tv_back_home, true, "prepare-primary", () -> { remember(); navigation.section(TvNavigation.Screen.HOME); render(); });
            action(actions, R.string.tv_tracks, false, "prepare-tracks", () -> returnToTracks(offer.id));
        } else if (failed && offer.available && prep.failure != TvPreparation.Failure.TRACK_UNAVAILABLE) {
            action(actions, R.string.tv_retry, true, "prepare-primary", () -> {
                try { source.select(offer.id); render(); } catch (IllegalStateException | IllegalArgumentException refused) { render(); }
            });
            action(actions, R.string.tv_back_tracks, false, "prepare-tracks", () -> returnToTracks(offer.id));
        } else {
            action(actions, R.string.tv_back_tracks, false, "prepare-primary", () -> returnToTracks(offer.id));
            if (!failed && prep.stage != TvPreparation.Stage.NONE) action(actions, R.string.tv_cancel, false, "prepare-cancel", () -> {
                source.cancel(offer.id); back();
            });
        }
        if (source instanceof TvAccountSource && !ready) {
            accountAction(main, "Vérifier le compte et l’affectation", "prepare-refresh", ((TvAccountSource) source)::refresh);
            if (prep.stage == TvPreparation.Stage.AWAITING_CONFIRMATION || prep.stage == TvPreparation.Stage.PREPARING)
                accountAction(main, "Reprendre la confirmation du cache", "prepare-resume", () -> confirmSelection(offer));
        }
        block(recap, card(offer, false, false), 0);
        defaultFocus = "prepare-primary";
    }

    /** Creates the three reference state cards with real status suppliers instead of placeholders. */
    private LinearLayout[] stateGrid(String[] titles, String[] messages) {
        LinearLayout row = ui.row(); LinearLayout[] panels = new LinearLayout[3];
        for (int i = 0; i < 3; i++) {
            panels[i] = state(titles[i], messages[i], false);
            ui.typography((TextView) panels[i].getChildAt(0), 17, true, 650);
            ui.typography((TextView) panels[i].getChildAt(1), 13, false, 400);
            ((TextView) panels[i].getChildAt(1)).setTextColor(ui.color(R.color.scenevibe_muted_foreground));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
            if (i > 0) p.leftMargin = ui.dp(16); row.addView(panels[i], p);
        }
        block(page, row, 14); return panels;
    }

    /** Restyles the existing controls while retaining listeners, labels and permission fallbacks. */
    private void styleRuntimeControls(View view, int[] buttonIndex) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            ui.typography(text, view instanceof Button ? 14 : 13, false, view instanceof Button ? 600 : 400);
            text.setTextColor(ui.color(R.color.scenevibe_foreground));
        }
        if (view instanceof Button) {
            Button button = (Button) view;
            button.setTextColor(ui.buttonTextColors(false));
            button.setBackgroundTintList(null); button.setStateListAnimator(null);
            button.setBackground(ui.focusBackground(R.color.scenevibe_card, R.color.scenevibe_public_action_border, 8, false));
            target(button, "runtime-control-" + buttonIndex[0]++);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view; group.setClipChildren(false); group.setClipToPadding(false);
            for (int i = 0; i < group.getChildCount(); i++) styleRuntimeControls(group.getChildAt(i), buttonIndex);
        }
    }

    /** Preserves every actual Companion action; no Cloud action is executed by entering this screen. */
    private void settings() {
        introduction(s(R.string.tv_settings), s(R.string.tv_settings_title), s(R.string.tv_settings_intro));
        RuntimeStatus status = runtime.read();
        LinearLayout[] cards = stateGrid(new String[] {s(R.string.tv_settings_account), s(R.string.tv_settings_language), s(R.string.tv_settings_format)},
                new String[] {source instanceof TvAccountSource ? ((TvAccountSource) source).accountMessage() : status.cloud, s(R.string.tv_french), s(R.string.tv_settings_format_detail)});
        accountStatus = (TextView) cards[0].getChildAt(1);
        action(page, R.string.tv_back_home, false, "settings-back", () -> { remember(); navigation.section(TvNavigation.Screen.HOME); render(); });
        block(page, text(s(R.string.tv_runtime_controls), 22, true, 650, R.color.scenevibe_foreground), 16);
        paragraph(page, s(R.string.tv_runtime_controls_intro), 13, 6);
        runtimeControls.setPadding(0, ui.dp(8), 0, ui.dp(12));
        runtimeControls.setGravity(Gravity.START);
        runtimeControls.setBackgroundColor(ui.color(R.color.scenevibe_background));
        styleRuntimeControls(runtimeControls, new int[] {0});
        block(page, runtimeControls, 8);
        if (!source.scenarioNames().isEmpty()) action(page, R.string.tv_local_scenarios, false, "local-scenarios", this::scenarioDialog);
        defaultFocus = "settings-back";
    }

    /** Uses real observational data and preserves the original detailed diagnostics and reset entry. */
    private void diagnostics() {
        introduction(s(R.string.tv_diagnostics), s(R.string.tv_diagnostics_title), s(R.string.tv_diagnostics_intro));
        RuntimeStatus status = runtime.read();
        LinearLayout[] panels = stateGrid(new String[] {s(R.string.tv_diag_connection), s(R.string.tv_diag_preparation), s(R.string.tv_diag_device)},
                new String[] {status.cloud, status.service + "\n" + status.permissions, status.device});
        diagnosticConnection = (TextView) panels[0].getChildAt(1);
        diagnosticService = (TextView) panels[1].getChildAt(1);
        diagnosticDevice = (TextView) panels[2].getChildAt(1);
        action(page, R.string.tv_diag_refresh, false, "diag-check", () -> {
            refreshRuntime();
            connection.announceForAccessibility(s(R.string.tv_diag_refreshed));
        });
        action(page, R.string.tv_diag_details, false, "diag-details", () -> { remember(); openTechnicalDiagnostics.run(); });
        defaultFocus = "diag-check";
    }

    /** Debug-only native dialog replaces the designer tool, without a global F2/remote hook. */
    private void scenarioDialog() {
        remember();
        List<String> scenarios = source.scenarioNames();
        if (scenarios.isEmpty()) return;
        new AlertDialog.Builder(activity).setTitle(s(R.string.tv_local_scenarios_title))
                .setSingleChoiceItems(scenarios.toArray(new String[0]), source.scenarioIndex(), (dialog, which) -> {
                    source.chooseScenario(which); dialog.dismiss(); render();
                }).setNegativeButton(s(R.string.tv_close), (dialog, which) -> dialog.dismiss())
                .setOnDismissListener(dialog -> root.post(() -> restoreFocus(true))).show();
    }

    /** Saves only route, focus, excerpt and scroll memory in Android's transient instance state. */
    public void save(Bundle out) {
        remember(); ArrayList<Bundle> stack = new ArrayList<>();
        for (TvNavigation.Visit visit : navigation.visits()) {
            Bundle item = new Bundle(); item.putString("screen", visit.screen.name()); item.putString("offer", visit.offerId);
            item.putString("focus", visit.focus); item.putInt("scroll", visit.scroll); item.putString("preview", visit.preview); stack.add(item);
        }
        out.putParcelableArrayList("tv.presentation.visits", stack);
        if (source.isLocalDemo()) {
            out.putInt("tv.presentation.scenario", source.scenarioIndex());
            out.putString("tv.presentation.localSelection", source.localSelection());
        }
    }

    /** Validates saved routes and ignores corrupt UI state without touching runtime persistence. */
    @SuppressWarnings("deprecation")
    private void restore(Bundle saved) {
        if (saved == null) return;
        try {
            if (source.isLocalDemo() && saved.containsKey("tv.presentation.scenario")) source.chooseScenario(saved.getInt("tv.presentation.scenario"));
            if (source.isLocalDemo() && saved.getString("tv.presentation.localSelection") != null) source.select(saved.getString("tv.presentation.localSelection"));
            ArrayList<Bundle> items = saved.getParcelableArrayList("tv.presentation.visits");
            if (items == null) return;
            List<TvNavigation.Visit> visits = new ArrayList<>();
            for (Bundle item : items) {
                TvNavigation.Visit visit = new TvNavigation.Visit(TvNavigation.Screen.valueOf(item.getString("screen")), item.getString("offer"));
                visit.focus = item.getString("focus"); visit.scroll = Math.max(0, item.getInt("scroll")); visit.preview = item.getString("preview"); visits.add(visit);
            }
            navigation.restore(visits);
        } catch (IllegalArgumentException | NullPointerException malformed) {
            // Corrupt presentation memory is discarded; pairing/cache are never reset.
        }
    }
}
