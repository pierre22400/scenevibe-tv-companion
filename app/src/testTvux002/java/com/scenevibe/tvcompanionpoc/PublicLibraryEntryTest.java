package com.scenevibe.tvcompanionpoc;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Vérifie la projection temporelle et les états visibles sans accorder une
 * capacité de lecture à partir d'une ligne de bibliothèque.
 */
public final class PublicLibraryEntryTest {
    /** Un pointeur vérifié ne reste pas annoncé comme disponible après son échéance. */
    @Test public void verifiedCacheRequiresRecentAuthorityAndIdentifiesCurrentTrack() {
        PublicLibraryEntry cached = new PublicLibraryEntry("command", "Columbo\n— Eaux troubles", 2,
                PublicLibraryEntry.State.VERIFIED, 30);
        assertEquals("Columbo — Eaux troubles · révision 2\nCache vérifié", cached.describe("other", 29));
        assertTrue(cached.describe("command", 29).contains("sélectionnée pour la restitution"));
        assertTrue(cached.describe("command", 30).contains("Disponibilité à vérifier"));
        assertFalse(cached.describe("command", 30).contains("Cache vérifié"));
    }

    /** Un retrait explicite est distinct d'une préparation différée et d'un réseau incertain. */
    @Test public void waitingAndUnavailableNeverClaimAnInstalledCache() {
        PublicLibraryEntry waiting = new PublicLibraryEntry("command", "", 0, PublicLibraryEntry.State.WAITING, 30);
        assertEquals("Nouvel envoi du catalogue\nEn attente de la fin des commentaires en cours", waiting.describe("", 1));
        assertTrue(waiting.withState(PublicLibraryEntry.State.UNAVAILABLE, 0)
                .describe("command", 1).contains("Publication ou autorisation indisponible"));
    }
}
