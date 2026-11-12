package com.scenevibe.tvcompanionpoc;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * Vérifie que le transport historique refuse une demande de nouveau code
 * et une commande sans preuve avant toute connexion HTTPS.
 */
@RunWith(RobolectricTestRunner.class)
public final class HistoricalViewerTransportTest {
    /** Un ancien credential ne peut être envoyé au chemin des challenges publics. */
    @Test public void historicalRouteRejectsPairingEvenWithCredential() {
        PublicCloudClient.Https transport=new PublicCloudClient.Https("https://qualification.invalid",true);
        assertEquals("HISTORICAL_POSSESSION_REQUIRED",assertThrows(IllegalArgumentException.class,
                ()->transport.request("challenge",new JSONObject(),"example-credential")).getMessage());
        assertEquals("HISTORICAL_POSSESSION_REQUIRED",assertThrows(IllegalArgumentException.class,
                ()->transport.request("challenge-status",new JSONObject(),"example-credential")).getMessage());
    }

    /** Un DeviceId seul ne déclenche aucune requête au serveur. */
    @Test public void historicalRouteRejectsRequestsWithoutPossession() {
        PublicCloudClient.Https transport=new PublicCloudClient.Https("https://qualification.invalid",true);
        assertEquals("HISTORICAL_POSSESSION_REQUIRED",assertThrows(IllegalArgumentException.class,
                ()->transport.request("poll",new JSONObject(),null)).getMessage());
    }
}
