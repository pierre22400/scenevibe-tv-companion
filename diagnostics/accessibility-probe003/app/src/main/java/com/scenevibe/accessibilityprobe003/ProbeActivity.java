package com.scenevibe.accessibilityprobe003;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;

/**
 * Voluntary TV setup/results screen. No automatic launch or overlay exists. The synthetic
 * control acts only on this Activity; exports are stopped, explicit, local file copies.
 */
public final class ProbeActivity extends Activity implements View.OnClickListener {
    private Spinner profiles;
    private TextView status;
    private TextView fixture;
    private Button settings;
    private Button arm;
    private Button export;
    private Button delete;
    private int fixtureStep;

    /** Build a light, readable diagnostic UI without importing any Companion control. */
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(32, 24, 32, 24);
        scroll.addView(body);
        text(body, "SceneVibe Probe 003 — diagnostic privé", 25);
        text(body, "Lecture locale minimale du seul profil choisi. Aucun contrôle du lecteur, aucune capture, aucun réseau. "
            + "Dix minutes et 2 Mo maximum. Les catégories publicitaires sont des candidats, jamais une détection. "
            + "Les textes bruts ne sont pas conservés. Active volontairement le service dans Android, puis arme un essai autorisé. "
            + "Le réglage Android peut rester activé après redémarrage ; la collecte, elle, reste désarmée.", 18);
        settings = button(body, "Ouvrir les réglages d’accessibilité Android");
        profiles = new Spinner(this);
        profiles.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ProbeProfile.values()));
        body.addView(profiles);
        status = text(body, "Service non connecté — aucune collecte", 18);
        arm = button(body, "Armer le profil sélectionné");
        button(body, "Arrêter la collecte");
        button(body, "Actualiser l’état");
        fixture = text(body, "Contrôle synthétique local uniquement", 20);
        fixture.setId(R.id.fixture_label);
        Button control = button(body, "Contrôle positif — modifier le texte et annoncer");
        control.setId(R.id.fixture_button);
        export = button(body, "Exporter le dernier journal arrêté — fichier local");
        delete = button(body, "Supprimer les journaux de cette sonde");
        text(body, "Après armement, quitte cette Activity manuellement pour observer le lecteur. "
            + "Aucun bouton de marquage ni écran ne s’ouvre pendant Netflix. Reviens ici pour arrêter/exporter. "
            + "Si le processus est tué, un journal sans SESSION_END reste incomplet. "
            + "Désactive ensuite le service dans Android. Aucune campagne Sony n’est autorisée par la livraison logicielle.", 18);
        setContentView(scroll);
    }

    /** Refresh only on voluntary entry; no polling service or background UI loop is introduced. */
    @Override public void onResume() { super.onResume(); refresh(); }

    /** Create a readable text block in this app's own Activity. */
    private TextView text(LinearLayout body, String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setPadding(0, 8, 0, 8); body.addView(view); return view;
    }

    /** Create a voluntary local button; no focus request or simulated input is used. */
    private Button button(LinearLayout body, String value) {
        Button view = new Button(this); view.setText(value); view.setTag(value);
        view.setOnClickListener(this); body.addView(view); return view;
    }

    /** Handle owner actions only inside this Activity; active profiles cannot be changed. */
    @Override public void onClick(View view) {
        ProbeService service = ProbeService.current();
        try {
            if (view == settings) {
                if (service == null || !service.controller().active()) startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } else if (view == arm) {
                if (service == null || !service.arm((ProbeProfile) profiles.getSelectedItem())) {
                    status.setText("Armement refusé : service absent, déjà actif ou stockage indisponible."); return;
                }
            } else if (view == export) {
                File copy = stoppedStore().exportLocal(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS));
                status.setText("Export local : " + copy.getAbsolutePath() + "\nSHA-256 : " + JournalStore.sha256(copy));
                return;
            } else if (view == delete) {
                JournalStore internal = stoppedStore();
                boolean ok = internal.deleteStopped();
                File external = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                if (external != null) ok &= new JournalStore(external).deleteStopped();
                status.setText(ok ? "Journaux de la sonde supprimés." : "Suppression partielle ; vérifie les fichiers."); return;
            } else if (view.getId() == R.id.fixture_button) {
                if (service == null || !service.controller().active()
                    || service.controller().profile() != ProbeProfile.OWN_CONTROL) {
                    status.setText("Contrôle positif disponible uniquement après armement OWN_CONTROL."); return;
                }
                fixtureStep++;
                fixture.setText(fixtureStep % 2 == 1 ? "Publicité" : "Programme");
                fixture.setContentDescription(fixtureStep % 2 == 1 ? "Publicité 1 sur 2" : "Programme");
                fixture.announceForAccessibility(fixtureStep % 2 == 1 ? "Publicité" : "Programme");
            } else if ("Arrêter la collecte".equals(view.getTag()) && service != null) service.stop(SafeRow.Reason.OWNER_STOP);
            refresh();
        } catch (IOException | RuntimeException failure) {
            status.setText("Opération indisponible. Aucun contenu ni détail privé d’erreur n’est exporté.");
        }
    }

    /** Refuse active export/deletion; recover stopped/incomplete private logs after process restart. */
    private JournalStore stoppedStore() throws IOException {
        ProbeService service = ProbeService.current();
        if (service != null && service.controller().active()) throw new IOException("Stop first");
        if (service != null) return service.store();
        JournalStore local = new JournalStore(new File(getFilesDir(), "probe003"));
        local.prune(System.currentTimeMillis()); return local;
    }

    /** Display known connection, profile and counts; an absent signal is never 'no advertisement'. */
    private void refresh() {
        if (status == null) return;
        ProbeService service = ProbeService.current();
        boolean active = service != null && service.controller().active();
        profiles.setEnabled(!active); settings.setEnabled(!active); arm.setEnabled(service != null && !active);
        export.setEnabled(!active); delete.setEnabled(!active);
        if (active) profiles.setSelection(service.controller().profile().ordinal());
        status.setText(service == null ? "Service non connecté — aucune collecte. Active-le volontairement puis actualise."
            : (active ? "ARMED — " : "CONNECTED_UNARMED — ") + service.controller().profile().name()
            + "\nLignes : " + service.controller().sequence() + " ; octets : " + service.store().bytes()
            + "\nDernier arrêt : " + service.controller().lastReason() + "\nAucune décision publicitaire automatique.");
    }
}
