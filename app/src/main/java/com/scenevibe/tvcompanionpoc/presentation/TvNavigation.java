package com.scenevibe.tvcompanionpoc.presentation;

import java.util.ArrayList;
import java.util.List;

/**
 * A small presentation-only back stack. Each visit owns its focus/scroll memory.
 * It contains no Android service, preferences, cache, tokens or preparation authority.
 */
public final class TvNavigation {
    public enum Screen { HOME, TRACKS, TRACK, PREPARE, SETTINGS, DIAGNOSTICS, DEVELOPER }
    private final List<Visit> stack = new ArrayList<>();

    /** Starts at the existing launcher home surface. */
    public TvNavigation() { stack.add(new Visit(Screen.HOME, null)); }

    /** One actual visit, so opening an old route does not resurrect stale focus memory. */
    public static final class Visit {
        public final Screen screen;
        public final String offerId;
        public String focus;
        public int scroll;
        public String preview;

        /** Detail routes require an explicit reference, with no default offer. */
        public Visit(Screen screen, String offerId) {
            if ((screen == Screen.TRACK || screen == Screen.PREPARE)
                    && (offerId == null || offerId.trim().isEmpty())) {
                throw new IllegalArgumentException("Missing offer reference");
            }
            this.screen = screen;
            this.offerId = offerId;
        }
    }

    /** Returns the visible visit; callers may update only its presentation memory. */
    public Visit current() { return stack.get(stack.size() - 1); }

    /** Stores focus before navigation or activity pause, including sidebar focus. */
    public void remember(String focus, int scroll) {
        current().focus = focus;
        current().scroll = Math.max(0, scroll);
    }

    /** Opens a fresh visit without borrowing a previous visit's position. */
    public void push(Screen screen, String offerId) { stack.add(new Visit(screen, offerId)); }

    /** Returns false at home so Android can leave the application normally. */
    public boolean back() {
        if (stack.size() <= 1) return false;
        stack.remove(stack.size() - 1);
        return true;
    }

    /** Selects a sidebar destination and remembers its entry for Back to home. */
    public void section(Screen screen) {
        if (screen == Screen.TRACK || screen == Screen.PREPARE) throw new IllegalArgumentException("Not a section");
        Visit home = stack.get(0);
        stack.clear();
        stack.add(home);
        if (screen != Screen.HOME) {
            home.focus = "nav-" + screen.name();
            stack.add(new Visit(screen, null));
        }
    }

    /** Returns to the selected card without changing any content or runtime installation. */
    public void tracks(String offerId) {
        Visit tracks = null;
        for (Visit visit : stack) if (visit.screen == Screen.TRACKS) tracks = visit;
        Visit home = stack.get(0);
        stack.clear();
        stack.add(home);
        if (tracks == null) tracks = new Visit(Screen.TRACKS, null);
        tracks.focus = "card-" + offerId;
        stack.add(tracks);
    }

    /** Retains the section actually used to enter a detail, exactly like the prototype stack. */
    public Screen activeSection() {
        Screen section = Screen.HOME;
        for (Visit visit : stack) {
            if (visit.screen != Screen.TRACK && visit.screen != Screen.PREPARE) section = visit.screen;
        }
        return section;
    }

    /** Copies only the minimal serializable UI state for Android activity recreation. */
    public List<Visit> visits() { return new ArrayList<>(stack); }

    /** Restores a validated stack whose first destination must remain home. */
    public void restore(List<Visit> visits) {
        if (visits == null || visits.isEmpty() || visits.get(0).screen != Screen.HOME) return;
        stack.clear();
        stack.addAll(visits);
    }
}
