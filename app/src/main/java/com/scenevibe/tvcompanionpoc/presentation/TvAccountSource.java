package com.scenevibe.tvcompanionpoc.presentation;

/**
 * Explicit account/delivery extension of the existing native shell. The UI requests
 * human-confirmed intentions; only the Cloud adapter can supply verified cache facts.
 */
public interface TvAccountSource extends TvPresentationSource {
    enum AccountStage { UNCONFIGURED, UNASSOCIATED, CONNECTING, CODE, ASSOCIATED, ERROR }
    /** Return a closed account stage, without exposing credentials or account identifiers. */
    AccountStage accountStage();
    /** Return safe French status text, never raw exceptions or network responses. */
    String accountMessage();
    /** Return the public verification address and temporary human code only. */
    String verification();
    /** True only during an actual request, never from a UI timer. */
    boolean busy();
    /** True when the server explicitly advertises another metadata page. */
    boolean hasMore();
    /** Explicitly begin a new or recovery challenge; never create a Cloud device implicitly. */
    void beginPairing(boolean recovery);
    /** Explicitly abandon only a pending local challenge, retaining identity and content. */
    void cancelPairing();
    /** Verify association or poll the current challenge; no automatic new challenge. */
    void refresh();
    /** Request one additional bounded page after a human action. */
    void loadMore();
    /** Read only the chosen Track's current metadata and ownership. */
    void inspect(String offerId);
    /** Subscribe to source changes on the Android main thread. */
    void onChange(Runnable listener);
    /** Resume bounded work owned by this Activity. */
    void resume();
    /** Invalidate callbacks before the Activity stops owning work. */
    void pause();
    /** Cancel owned timers and release the private executor. */
    void close();
}
