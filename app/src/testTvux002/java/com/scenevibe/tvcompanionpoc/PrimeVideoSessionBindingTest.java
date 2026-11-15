package com.scenevibe.tvcompanionpoc;

import android.media.session.PlaybackState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Vérifie l'autorité et l'horloge d'une sélection indépendamment des métadonnées Android.
 */
public final class PrimeVideoSessionBindingTest {
    private static final String PRIME="com.amazon.amazonvideo.livingroom";
    private static final long NOW=10_000L;

    /** Un paquet tiers et une horloge périmée ou invalide ne peuvent jamais armer la Track. */
    @Test public void rejectsWrongPlatformAndInvalidClocks() {
        PrimeVideoSessionBinding binding=new PrimeVideoSessionBinding();
        assertEquals(PrimeVideoSessionBinding.State.WAITING,
                binding.observe(PRIME,sample("wrong",1,0,NOW,PlaybackState.STATE_PLAYING),NOW));
        assertEquals(PrimeVideoSessionBinding.State.CLOCK_UNAVAILABLE,
                binding.observe(PRIME,sample(PRIME,1,0,NOW-2501,PlaybackState.STATE_PLAYING),NOW));
        assertEquals(PrimeVideoSessionBinding.State.CLOCK_UNAVAILABLE,
                binding.observe(PRIME,sample(PRIME,1,0,NOW+1,PlaybackState.STATE_PLAYING),NOW));
        assertEquals(PrimeVideoSessionBinding.State.CLOCK_UNAVAILABLE,
                binding.observe(PRIME,sample(PRIME,1,43_200_001,NOW,PlaybackState.STATE_PLAYING),NOW));
        assertEquals(PrimeVideoSessionBinding.State.CLOCK_UNAVAILABLE,
                binding.observe(PRIME,sample(PRIME,1,-1,NOW,PlaybackState.STATE_PLAYING),NOW));
    }

    /** Après la perte du signal, seul le jeton initial peut reprendre le choix en cours. */
    @Test public void restoresOnlyBoundSession() {
        PrimeVideoSessionBinding binding=new PrimeVideoSessionBinding();
        MediaSessionProbe.Snapshot original=sample(PRIME,1,0,NOW,PlaybackState.STATE_PLAYING);
        assertEquals(PrimeVideoSessionBinding.State.READY,binding.observe(PRIME,original,NOW));
        assertEquals(PrimeVideoSessionBinding.State.WAITING,binding.observe(PRIME,null,NOW));
        MediaSessionProbe.Snapshot paused=sample(PRIME,1,0,NOW,PlaybackState.STATE_PAUSED);
        assertTrue(binding.bound(paused));
        assertEquals(PrimeVideoSessionBinding.State.READY,binding.observe(PRIME,paused,NOW));
        assertEquals(PrimeVideoSessionBinding.State.BUFFERING,
                binding.observe(PRIME,sample(PRIME,1,0,NOW,PlaybackState.STATE_BUFFERING),NOW));
        MediaSessionProbe.Snapshot replacement=sample(PRIME,2,0,NOW,PlaybackState.STATE_PLAYING);
        assertEquals(PrimeVideoSessionBinding.State.RESELECT_REQUIRED,binding.observe(PRIME,replacement,NOW));
        assertEquals(PrimeVideoSessionBinding.State.RESELECT_REQUIRED,binding.observe(PRIME,original,NOW));
        assertEquals(PrimeVideoSessionBinding.State.READY,
                new PrimeVideoSessionBinding().observe(PRIME,replacement,NOW));
    }

    /** Les métadonnées volontairement discordantes ne servent jamais au rattachement. */
    private static MediaSessionProbe.Snapshot sample(String pkg,int token,long position,long captured,int state) {
        return new MediaSessionProbe.Snapshot(pkg,state,"fixture",position,position,1f,100,
                "unrelated-media","Autre titre","Autre épisode",5_000L,token,captured,captured-100);
    }
}
