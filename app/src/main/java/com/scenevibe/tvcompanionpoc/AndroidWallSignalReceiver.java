package com.scenevibe.tvcompanionpoc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * The genuinely Android-only time/interactivity signal glue (section 3). It is a DYNAMIC receiver
 * (registered/unregistered by the owner, never a manifest receiver) that forwards OS signals into
 * the testable {@link WallClockDriver}:
 *
 * <ul>
 *   <li>{@code ACTION_TIME_CHANGED} / {@code ACTION_TIMEZONE_CHANGED} and a resume/service-reentry
 *       trigger {@link WallClockDriver#onSignal()} to force a reanchor/re-evaluation even below the
 *       drift threshold (the UTC calendar is unchanged; only the local projection is re-sampled).</li>
 *   <li>{@code ACTION_SCREEN_OFF} is treated as a local suspension hint:
 *       {@link WallClockDriver#onSuspend()} suspends and hides presentation and disarms the wait.</li>
 *   <li>{@code ACTION_SCREEN_ON} is an interactivity hint, NOT proof of panel state:
 *       {@link WallClockDriver#onResume()} re-evaluates the still-active window on return.</li>
 * </ul>
 *
 * <p>It adds NO new permission, AlarmManager-exact, wake lock, process wake or Direct Boot: these
 * are ordinary system broadcasts observed only while the owner process is alive. Registration and
 * unregistration are owner-thread lifecycle operations the composition performs in
 * {@code onStartCommand}/{@code onDestroy}.</p>
 */
final class AndroidWallSignalReceiver extends BroadcastReceiver {

    private final WallClockDriver driver;

    /** Bind the receiver to the single driver instance it drives; it holds no clock itself. */
    AndroidWallSignalReceiver(WallClockDriver driver) {
        if (driver == null) throw new IllegalArgumentException("Missing WALL driver");
        this.driver = driver;
    }

    /** The dynamic filter the owner registers; all entries are ordinary broadcasts, no new grant. */
    static IntentFilter filter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        return filter;
    }

    /** Route an OS signal to the driver; an unknown action is ignored and never reads a clock here. */
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        switch (intent.getAction()) {
            case Intent.ACTION_TIME_CHANGED:
            case Intent.ACTION_TIMEZONE_CHANGED:
                driver.onSignal();
                break;
            case Intent.ACTION_SCREEN_OFF:
                driver.onSuspend();
                break;
            case Intent.ACTION_SCREEN_ON:
                driver.onResume();
                break;
            default:
                // Unknown action: observational no-op; the next wait/signal still re-reads the clock.
                break;
        }
    }
}
