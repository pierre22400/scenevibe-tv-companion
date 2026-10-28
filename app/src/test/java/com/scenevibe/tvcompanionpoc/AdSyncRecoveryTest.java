package com.scenevibe.tvcompanionpoc;

import org.junit.Test;
import static org.junit.Assert.*;

/** v22 matrix: valid MEDIA recovers; unknown advertising never overrides real identity/clock or user intent. */
public final class AdSyncRecoveryTest {
    private final Object session=new Object();
    /** Describe a compatible fresh capture, including an old but valid raw anchor. */
    private AdSyncController.Sample frame(long position,long time){return new AdSyncController.Sample(session,true,true,1000,position,100000,time,1f,1_000_000);}
    /** Select the new policy explicitly; historical v18/v19 tests continue to exercise their frozen policy. */
    private AdSyncController controller(){var c=new AdSyncController(true);c.setEnabled(true);return c;}
    /** Ordinary continuous content has the same selected time as OFF. */
    @Test public void validContinuousOnMatchesOff(){var on=controller();var off=new AdSyncController(true);for(int i=0;i<40;i++){var s=frame(1000+i*250,100000+i*250);assertTrue(on.observe(s).allow);assertEquals(off.observe(s).contentMs,on.status().contentMs);}}
    /** A single metadata loss cannot latch the guard indefinitely after compatible captures return. */
    @Test public void missingMetadataRecoversWithTwoFreshCompatibleCaptures(){var c=controller();c.observe(frame(1000,100000));c.unavailable();assertFalse(c.observe(frame(2000,101000)).allow);assertTrue(c.observe(frame(2250,101250)).allow);assertEquals(AdSyncController.Reason.AUTO_RECOVERED,c.status().reason);}
    /** Duplicates, future and stale captures cannot satisfy automatic confirmation. */
    @Test public void staleAndDuplicateCannotAutoRecover(){var c=controller();c.unavailable();assertFalse(c.observe(frame(1000,100000),103000).allow);assertFalse(c.observe(frame(2000,103000)).allow);assertFalse(c.observe(frame(2000,103000)).allow);assertFalse(c.observe(frame(2250,103250),103200).allow);assertFalse(c.observe(frame(2500,103500)).allow);assertTrue(c.observe(frame(2750,103750)).allow);}
    /** An incompatible film stays blocked, even after many stable timestamps. */
    @Test public void realIdentityMismatchNeverAutoOpens(){var c=controller();for(int i=0;i<8;i++)assertFalse(c.observe(new AdSyncController.Sample(session,false,true,1000,1000,100000,100000+i*250,1f,100000,false)).allow);assertEquals(AdSyncController.Reason.MEDIA_IDENTITY_INVALID,c.status().reason);}
    /** Once the independently verified exact track returns, two observations recover without new admission. */
    @Test public void identityReturnCanRecoverButNotBypassTheFirstCapture(){var c=controller();c.observe(new AdSyncController.Sample(session,false,true,0,-1,100000,100000,1f,100000,false));assertFalse(c.observe(frame(2000,101000)).allow);assertTrue(c.observe(frame(2250,101250)).allow);}
    /** Current but unusable clock values do not become a guessed film position. */
    @Test public void invalidClockRemainsClosed(){var c=controller();assertFalse(c.observe(new AdSyncController.Sample(session,false,true,-1,-1,0,100000,1f,100000,true)).allow);assertEquals(AdSyncController.Reason.MEDIA_CLOCK_INVALID,c.status().reason);assertFalse(c.observe(frame(2000,101000)).allow);assertTrue(c.observe(frame(2250,101250)).allow);}
    /** A manual hold is not advertising suspicion and survives automatic recovery and opt-in changes. */
    @Test public void manualSuspensionSurvivesOnOffOn(){var c=controller();c.observe(frame(1000,100000));c.suspend();c.setEnabled(false);assertFalse(c.observe(frame(1250,100250)).allow);c.setEnabled(true);c.unavailable();for(int i=0;i<6;i++)assertFalse(c.observe(frame(2000+i*250,101000+i*250)).allow);assertEquals(AdSyncController.Reason.USER_SUSPENDED,c.status().reason);}
    /** Explicit resume intent is retained while MEDIA is absent; it still needs two fresh captures. */
    @Test public void explicitResumeWaitsThroughAbsence(){var c=controller();c.suspend();c.requestResume();c.unavailable();assertFalse(c.observe(frame(2000,101000)).allow);assertTrue(c.observe(frame(2250,101250)).allow);}
    /** A valid old anchor is extrapolated by the existing probe, not treated as a detected ad. */
    @Test public void oldAnchorDoesNotPreventRecovery(){var c=controller();c.unavailable();assertFalse(c.observe(frame(61000,160000)).allow);assertTrue(c.observe(frame(61250,160250)).allow);}
    /** A pause is a usable stable clock; recovery does not manufacture PLAY. */
    @Test public void normalPauseCanRecoverWithoutPlaying(){var c=controller();c.unavailable();assertFalse(c.observe(new AdSyncController.Sample(session,true,false,1000,1000,100000,101000,0f,100000)).allow);assertTrue(c.observe(new AdSyncController.Sample(session,true,false,1000,1000,100000,101250,0f,100000)).allow);}
    /** Session replacement is first uncertain and can recover only with its own compatible progression. */
    @Test public void replacementRequiresItsOwnTwoCaptures(){var c=controller();c.observe(frame(1000,100000));Object next=new Object();assertFalse(c.observe(new AdSyncController.Sample(next,true,true,2000,2000,101000,101000,1f,100000)).allow);assertTrue(c.observe(new AdSyncController.Sample(next,true,true,2000,2250,101000,101250,1f,100000)).allow);}
    /** A discontinuity is held temporarily, not called advertising and not permanently latched. */
    @Test public void discontinuityRecoversWithoutRearmingTheCalendar(){var c=controller();c.observe(frame(1000,100000));assertFalse(c.observe(frame(15000,100250)).allow);assertTrue(c.observe(frame(15250,100500)).allow);assertEquals(15250,c.status().contentMs);}
    /** Duration metadata is not a reliable ad flag once the native track matcher accepts the identity. */
    @Test public void compatibleDurationVariationAloneDoesNotSuppress(){var c=controller();c.observe(frame(1000,100000));assertTrue(c.observe(new AdSyncController.Sample(session,true,true,1000,1250,100000,100250,1f,-1)).allow);}
    /** Local user calibration remains bounded and is preserved through transient observation loss. */
    @Test public void calibrationOffsetSurvivesRecovery(){var c=controller();c.observe(frame(1000,100000));assertTrue(c.calibrate(5000,100000));c.unavailable();assertFalse(c.observe(frame(2000,101000)).allow);assertTrue(c.observe(frame(2250,101250)).allow);assertEquals(6250,c.status().contentMs);}
    /** No wall-time estimate can override missing identity during a manual calibration. */
    @Test public void recoveryDoesNotWeakenCalibrationRefusals(){var c=controller();c.observe(new AdSyncController.Sample(session,false,true,0,-1,100000,100000,1f,100000,false));assertEquals(AdSyncController.Calibration.IDENTITY_INCOMPATIBLE,c.calibrateResult(1000,100000));}
}
