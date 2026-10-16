package com.scenevibe.tvcompanionpoc.presentation;

import android.content.Context;
import java.util.Collections;
import java.util.List;

/** Release has no development corpus and no Cloud selection capability in this presentation lot. */
public final class TvSourceFactory {
    /** Returns a closed, unavailable port until a separately authorized adapter is supplied. */
    public static TvPresentationSource create(Context context) { return new Unavailable(); }

    /** No fixture, request, timer or default Track can enter the release presentation. */
    private static final class Unavailable implements TvPresentationSource {
        /** An unavailable catalogue remains empty; existing runtime cache is unaffected. */
        @Override public List<TvOffer> offers() { return Collections.emptyList(); }
        /** Release never represents itself as a demonstration source. */
        @Override public boolean isLocalDemo() { return false; }
        /** No selection API exists in this lot. */
        @Override public void select(String id) { throw new IllegalStateException("Selection adapter unavailable"); }
        /** Missing runtime evidence can never be presented as ready. */
        @Override public TvPreparation preparation(String id) {
            return new TvPreparation(TvPreparation.Stage.FAILED,
                    TvPreparation.Failure.CONNECTION_UNAVAILABLE, false, null);
        }
        /** There is no intention to cancel and no installed content is changed. */
        @Override public void cancel(String id) { }
        /** Development controls are not compiled into release. */
        @Override public List<String> scenarioNames() { return Collections.emptyList(); }
        /** No scenario command is accepted in release. */
        @Override public void chooseScenario(int index) { throw new IllegalStateException("No development source"); }
        /** No development state is persisted. */
        @Override public int scenarioIndex() { return -1; }
    }
}
