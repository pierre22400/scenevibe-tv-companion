package com.scenevibe.tvcompanionpoc;

/**
 * Projection de présentation des commandes et du pointeur de cache publics.
 * Elle ne contient aucun credential ni chemin de stockage et ne permet
 * d'armer aucun commentaire. Les états positifs ont une échéance monotone.
 */
final class PublicLibraryEntry {
    enum State {
        WAITING, DOWNLOADING, VERIFIED, CHECKING, UNAVAILABLE
    }

    final String command;
    private final String title;
    private final long revision;
    private final long expiresAt;
    final State state;

    /** Retient uniquement le titre vérifié et les faits nécessaires à la bibliothèque. */
    PublicLibraryEntry(String command, String title, long revision, State state, long expiresAt) {
        this.command = command;
        this.title = title.replaceAll("[\\p{Cc}\\p{Cf}]", " ").trim();
        this.revision = revision;
        this.state = state;
        this.expiresAt = expiresAt;
    }

    /** Conserve l'identité de présentation quand un contrôle change son état. */
    PublicLibraryEntry withState(State next, long deadline) {
        return new PublicLibraryEntry(command, title, revision, next, deadline);
    }

    /** Distingue le cache revalidé, le différé et la perte d'autorité sans inférer un rendu. */
    String describe(String activeCommand, long now) {
        String label = title.isEmpty() ? "Nouvel envoi du catalogue" : title;
        if (revision > 0) label += " · révision " + revision;
        String status;
        if (state == State.UNAVAILABLE) {
            status = "Publication ou autorisation indisponible";
        } else if (now >= expiresAt || state == State.CHECKING) {
            status = "Disponibilité à vérifier";
        } else {
            status = switch (state) {
                case WAITING -> "En attente de la fin des commentaires en cours";
                case DOWNLOADING -> "Téléchargement et vérification";
                case VERIFIED -> command.equals(activeCommand)
                        ? "Cache vérifié · Track sélectionnée pour la restitution" : "Cache vérifié";
                default -> throw new IllegalStateException("PUBLIC_LIBRARY_STATE");
            };
        }
        return label + "\n" + status;
    }
}
