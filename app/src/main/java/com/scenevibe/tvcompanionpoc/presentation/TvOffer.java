package com.scenevibe.tvcompanionpoc.presentation;

import java.util.List;
import java.util.Objects;

/** Immutable editorial presentation. An offer id is never an executable FinalTrack id. */
public final class TvOffer {
    public final String id, title, description, longDescription, filmTitle, edition;
    public final String language, illustration, visualLabel, origin, revision, channel;
    public final boolean available;
    public final List<Preview> previews;

    /** Validates stable local identity without inventing a default Track reference. */
    public TvOffer(String id, String title, String description, String longDescription,
            String filmTitle, String edition, String language, String illustration,
            String visualLabel, String origin, String revision, String channel,
            boolean available, List<Preview> previews) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Missing offer id");
        this.id = id;
        this.title = Objects.requireNonNull(title);
        this.description = description;
        this.longDescription = longDescription;
        this.filmTitle = filmTitle;
        this.edition = edition;
        this.language = language;
        this.illustration = illustration;
        this.visualLabel = visualLabel;
        this.origin = origin;
        this.revision = revision;
        this.channel = channel;
        this.available = available;
        this.previews = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(previews));
    }

    /** A read-only excerpt; its position is editorial copy, never a scheduler timecode. */
    public static final class Preview {
        public final String id, label, position, title, context, text;

        /** Retains the authored text and a stable focus key. */
        public Preview(String id, String label, String position, String title, String context, String text) {
            this.id = Objects.requireNonNull(id);
            this.label = label;
            this.position = position;
            this.title = title;
            this.context = context;
            this.text = text;
        }
    }
}
