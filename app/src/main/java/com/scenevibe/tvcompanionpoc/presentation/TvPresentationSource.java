package com.scenevibe.tvcompanionpoc.presentation;

import java.util.List;

/**
 * Presentation boundary for a future trusted adapter, not a Cloud wire contract.
 * The UI cannot assign, install, acknowledge or admit a media program through this port.
 * Demo implementations live in src/debug; release has an explicitly unavailable source.
 */
public interface TvPresentationSource {
    /** Returns immutable offers, never a fixture fallback after a network error. */
    List<TvOffer> offers();

    /** Identifies the source on every screen; demo must never be described as Cloud. */
    boolean isLocalDemo();

    /** Records a selection intention only; it confers no execution authority. */
    void select(String offerId);

    /** Returns separate account, assignment, installation, ACK and admission facts. */
    TvPreparation preparation(String offerId);

    /** Cancels an intention without deleting the existing Companion cache or identity. */
    void cancel(String offerId);

    /** Returns debug-only manual scenarios; the release implementation returns no entries. */
    List<String> scenarioNames();

    /** Applies an explicit local scenario, with no timer, network or runtime side effect. */
    void chooseScenario(int index);

    /** Saves only the transient presentation scenario, never a runtime credential or revision. */
    int scenarioIndex();

    /** Returns transient debug intention memory only; release never persists an intention here. */
    default String localSelection() { return null; }
}
