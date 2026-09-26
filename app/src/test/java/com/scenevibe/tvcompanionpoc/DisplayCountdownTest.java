package com.scenevibe.tvcompanionpoc;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** A paused card remains displayed across multiple pause/resume cycles. */
public class DisplayCountdownTest {
    @Test public void pausedTimeDoesNotConsumeVisibleDuration() {
        DisplayCountdown countdown = new DisplayCountdown();
        countdown.start(5000L, 1000L, true);
        assertEquals(3000L, countdown.update(false, 3000L));
        assertEquals(3000L, countdown.update(false, 30000L));
        assertEquals(3000L, countdown.update(true, 31000L));
        assertEquals(2000L, countdown.update(false, 32000L));
        assertEquals(2000L, countdown.update(true, 40000L));
        assertEquals(0L, countdown.update(true, 42000L));
    }
}
