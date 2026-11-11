package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/**
 * Préférences du spectateur public, indépendantes de la commande et du privé.
 * Les valeurs initiales s'appliquent une seule fois, au premier grant valide.
 */
final class PublicViewerPreferences {
    private static final String TEXT = "text";
    private static final String AUDIO = "audio";
    private final SharedPreferences values;

    /** Ouvre uniquement le fichier des préférences publiques. */
    PublicViewerPreferences(Context context) {
        this(context.getSharedPreferences("public_viewer_preferences", Context.MODE_PRIVATE));
    }

    /** Réutilise la persistance Android dans les essais de redémarrage. */
    PublicViewerPreferences(SharedPreferences values) {
        this.values = values;
    }

    /** Initialise les clés absentes, en reprenant un choix public antérieur quand il existe. */
    synchronized void initialize(JSONObject previous) {
        if (initialized()) return;
        SharedPreferences.Editor edit = values.edit();
        if (!values.contains(TEXT)) edit.putBoolean(TEXT, previous == null || previous.optBoolean(TEXT, true));
        if (!values.contains(AUDIO)) edit.putBoolean(AUDIO, previous == null || previous.optBoolean(AUDIO, true));
        if (!edit.commit()) throw new IllegalStateException("PUBLIC_PREFERENCES_UNAVAILABLE");
    }

    /** L'état prêt requiert les deux choix enregistrés. */
    boolean initialized() { return values.contains(TEXT) && values.contains(AUDIO); }

    /** Lit le choix écrit courant sans valeur activée pour une TV non initialisée. */
    boolean text() { return values.getBoolean(TEXT, false); }

    /** Lit le choix audio courant sans valeur activée pour une TV non initialisée. */
    boolean audio() { return values.getBoolean(AUDIO, false); }

    /** Persiste uniquement le canal choisi. */
    boolean setText(boolean enabled) { return initialized() && values.edit().putBoolean(TEXT, enabled).commit(); }

    /** Persiste uniquement le canal choisi. */
    boolean setAudio(boolean enabled) { return initialized() && values.edit().putBoolean(AUDIO, enabled).commit(); }
}
