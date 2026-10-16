package com.scenevibe.tvcompanionpoc.presentation;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Debug-only local provider. No network, timer, store, ACK or service reference exists here. */
public final class TvSourceFactory {
    /** Creates the explicitly labelled development source only in the debug source set. */
    public static TvPresentationSource create(Context context) { return new LocalDemo(context); }

    /** Manual, deterministic visual scenarios; never a simulation of runtime authority. */
    private static final class LocalDemo implements TvPresentationSource {
        private final List<TvOffer> corpus = new ArrayList<>();
        private String selected;
        private int scenario;
        private boolean cancelled;
        private final List<String> names = Collections.unmodifiableList(java.util.Arrays.asList(
                "Démo : état initial", "Démo : attente de confirmation", "Démo : préparation",
                "Démo : prête (aucun runtime)", "Démo : confirmation refusée", "Démo : demande expirée",
                "Démo : préparation échouée", "Démo : connexion indisponible", "Démo : Track retirée",
                "Démo : liste vide"));

        /** Loads the exact synthetic corpus from the accepted handoff, failing visibly if invalid. */
        LocalDemo(Context context) {
            try (InputStream in = context.getAssets().open("tvfirst002/demo-offers.json")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                for (int count; (count = in.read(buffer)) != -1;) bytes.write(buffer, 0, count);
                JSONArray tracks = new JSONArray(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                for (int i = 0; i < tracks.length(); i++) {
                    JSONObject t = tracks.getJSONObject(i);
                    List<TvOffer.Preview> previews = new ArrayList<>();
                    JSONArray entries = t.getJSONArray("preview");
                    for (int p = 0; p < entries.length(); p++) {
                        JSONObject v = entries.getJSONObject(p);
                        previews.add(new TvOffer.Preview(v.getString("id"), v.getString("label"),
                                v.getString("position"), v.getString("title"), v.getString("context"), v.getString("text")));
                    }
                    corpus.add(new TvOffer("demo:" + t.getString("id"), t.getString("title"),
                            t.getString("description"), t.getString("longDescription"), t.getString("filmTitle"),
                            t.getString("edition"), t.getString("language"), t.getString("illustration"),
                            t.getString("visualLabel"), t.getString("origin"), t.getString("revision"),
                            t.isNull("channel") ? null : t.getJSONObject("channel").getString("title"), i == 0, previews));
                }
            } catch (Exception invalid) {
                throw new IllegalStateException("Invalid local development corpus", invalid);
            }
        }

        /** Returns no content for the explicit empty-catalogue scenario. */
        @Override public List<TvOffer> offers() {
            if (scenario == 9) return Collections.emptyList();
            if (scenario != 8) return Collections.unmodifiableList(corpus);
            List<TvOffer> withdrawn = new ArrayList<>();
            for (TvOffer t : corpus) withdrawn.add(new TvOffer(t.id, t.title, t.description,
                    t.longDescription, t.filmTitle, t.edition, t.language, t.illustration,
                    t.visualLabel, t.origin, t.revision, t.channel, false, t.previews));
            return Collections.unmodifiableList(withdrawn);
        }

        /** All caller-visible states retain the development provenance. */
        @Override public boolean isLocalDemo() { return true; }

        /** Selection changes local UI memory only; unknown and withdrawn references are refused. */
        @Override public void select(String id) {
            TvOffer offer = null;
            for (TvOffer t : offers()) if (t.id.equals(id)) offer = t;
            if (offer == null || !offer.available) throw new IllegalArgumentException("Unavailable local offer");
            if (id.equals(selected)) scenario = 0;
            selected = id;
            cancelled = false;
        }

        /** Displays only explicitly chosen scenarios, never an automatic confirmation or ACK. */
        @Override public TvPreparation preparation(String id) {
            TvPreparation.Stage stage = TvPreparation.Stage.NONE;
            TvPreparation.Failure error = null;
            boolean known = false;
            for (TvOffer offer : corpus) if (offer.id.equals(id)) known = true;
            if (!known) return new TvPreparation(TvPreparation.Stage.FAILED,
                    TvPreparation.Failure.TRACK_UNAVAILABLE, true, null);
            if (!cancelled && (id.equals(selected) || scenario > 0)) {
                stage = TvPreparation.Stage.REQUESTED;
                if (scenario == 1) stage = TvPreparation.Stage.AWAITING_CONFIRMATION;
                else if (scenario == 2) stage = TvPreparation.Stage.PREPARING;
                else if (scenario == 3) stage = TvPreparation.Stage.READY;
                else if (scenario >= 4 && scenario <= 8) {
                    stage = TvPreparation.Stage.FAILED;
                    error = new TvPreparation.Failure[] {TvPreparation.Failure.CONFIRMATION_REFUSED,
                            TvPreparation.Failure.REQUEST_EXPIRED, TvPreparation.Failure.PREPARATION_FAILED,
                            TvPreparation.Failure.CONNECTION_UNAVAILABLE, TvPreparation.Failure.TRACK_UNAVAILABLE}[scenario - 4];
                }
            }
            return new TvPreparation(stage, error, true, null);
        }

        /** Cancels only local intention memory, leaving Companion data untouched. */
        @Override public void cancel(String id) { selected = null; scenario = 0; cancelled = true; }

        /** Makes every development scenario explicit in the qualification dialog. */
        @Override public List<String> scenarioNames() { return names; }

        /** Rejects unknown scenarios and changes no runtime state. */
        @Override public void chooseScenario(int index) {
            if (index < 0 || index >= names.size()) throw new IllegalArgumentException("Unknown local scenario");
            scenario = index;
            selected = null;
            cancelled = false;
        }

        /** Returns a presentation-only integer safe to save during activity recreation. */
        @Override public int scenarioIndex() { return scenario; }

        /** Allows Activity recreation to retain a local request without replaying any real API. */
        @Override public String localSelection() { return selected; }
    }
}
