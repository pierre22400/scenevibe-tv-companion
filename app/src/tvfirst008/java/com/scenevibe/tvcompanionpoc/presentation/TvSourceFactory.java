package com.scenevibe.tvcompanionpoc.presentation;

import android.content.Context;
import com.scenevibe.tvcompanionpoc.TvFirst008Source;

/** This build alone binds the account/cache adapter; release and observation remain closed. */
public final class TvSourceFactory {
    /** Compose an Activity-owned adapter without starting a service, activation or download. */
    public static TvPresentationSource create(Context context) { return new TvFirst008Source(context); }
    /** Static build composition has no mutable test hook. */
    private TvSourceFactory() {}
}
