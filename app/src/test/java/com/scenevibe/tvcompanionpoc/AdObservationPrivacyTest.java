package com.scenevibe.tvcompanionpoc;

import android.media.MediaMetadata;
import android.os.Bundle;
import com.scenevibe.tvcompanionpoc.ad.AdEvidenceDetector.Marker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Public Android metadata/Bundle privacy boundaries, not claims about Prime's published fields. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32)
public final class AdObservationPrivacyTest {
    /** The platform's numeric default zero must not hide the absence of the advertising key. */
    @Test public void absentAndExplicitZeroAreDifferent(){MediaMetadata absent=new MediaMetadata.Builder().build();
        assertEquals(0,absent.getLong(AdObservationProbe.AD_KEY));assertEquals(Marker.ABSENT,AdObservationProbe.readMarker(absent));
        assertEquals(Marker.CONTENT,AdObservationProbe.readMarker(new MediaMetadata.Builder().putLong(AdObservationProbe.AD_KEY,0).build()));
        assertEquals(Marker.AD,AdObservationProbe.readMarker(new MediaMetadata.Builder().putLong(AdObservationProbe.AD_KEY,1).build()));}
    /** Keys and values containing credentials or publisher content never enter the exported shape. */
    @Test public void metadataInventoryOnlyEmitsKnownNamesAndUnknownCount(){MediaMetadata metadata=new MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE,"PRIVATE_TITLE_123").putString("private.token.SECRET_KEY_123","SECRET_VALUE_123").build();
        String shape=AdObservationProbe.metadataShape(metadata);assertTrue(shape.contains("TITLE"));assertTrue(shape.contains("otherKeys=1"));
        assertFalse(shape.contains("PRIVATE_TITLE"));assertFalse(shape.contains("SECRET"));assertFalse(shape.contains("token"));}
    /** Extras outside the standardized metadata location are inventoried but never classified as AD_START. */
    @Test public void extrasAreNotAnAdvertisingProtocol(){Bundle extras=new Bundle();extras.putString("private.account.USER_123","TOKEN_123");
        extras.putLong(AdObservationProbe.AD_KEY,1);String shape=AdObservationProbe.extrasShape(extras);
        assertEquals("keys=2;standardAdKey=EXPLICIT_NONZERO",shape);assertFalse(shape.contains("USER"));assertFalse(shape.contains("TOKEN"));}
    /** Malformed extras never become false/content merely because a getter has a numeric default. */
    @Test public void wrongExtrasTypeIsExplicit(){Bundle extras=new Bundle();extras.putString(AdObservationProbe.AD_KEY,"not-a-number");
        assertEquals("keys=1;standardAdKey=WRONG_TYPE",AdObservationProbe.extrasShape(extras));}
    /** Missing bundles and metadata are represented explicitly without a synthetic negative advertising flag. */
    @Test public void missingPublicFamiliesAreExplicit(){assertEquals(Marker.ABSENT,AdObservationProbe.readMarker(null));
        assertEquals("ABSENT",AdObservationProbe.metadataShape(null));assertEquals("ABSENT",AdObservationProbe.extrasShape(null));}
}
