package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.Intent;

/**
 * One explicit request envelope for Diagnostics and the qualification-only ADB adapter.
 * It grants no transport authority: OverlayService and the unchanged E5 controller
 * still own target, foreground, session, playback, preparation and recovery guards.
 */
final class E5LocalRequest {
    /** Create the fixed local VOD request; never accept a caller-selected component or payload. */
    static Intent confirmedVod(Context context) {
        return new Intent(context, OverlayService.class)
                .setAction(OverlayService.ACTION_E5_LOCAL).putExtra("e5VodConfirmed", true);
    }
}
