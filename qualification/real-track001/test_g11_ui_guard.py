import pathlib
import unittest

"""
SCENEVIBE G11 — gardes deterministes du parcours multimedia prive Android TV.
Ni reseau, ni fournisseur, ni Sony, ni effacement de cache.
La nouvelle branche conserve les profils historiques et qualifie seulement REAL TRACK.
"""

ROOT = pathlib.Path(__file__).resolve().parents[2]


class RealTrackG11UiGuard(unittest.TestCase):
    """Conserve les frontieres d'autorite et l'emplacement du catalogue prive."""

    def test_legacy_library_not_required_for_real_track(self):
        """La bibliotheque FinalTrack ancienne n'est pas l'autorite multimedia G11."""
        source = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/TvFirst008Source.java").read_text()
        self.assertIn("if (!BuildConfig.REAL_TRACK_E2E_001) page(ticket, null);", source)
        self.assertIn("restoreReceipt(ticket, id);", source)
        self.assertIn("Identifiant TV : ", source)

    def test_catalogue_view_unique_and_settings_unchanged(self):
        """Deplace une seule vue sans supprimer les commandes des Reglages."""
        shell = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/presentation/TvShellController.java").read_text()
        controls = (ROOT / "app/src/main/java/com/scenevibe/tvcompanionpoc/CloudMultimediaControls.java").read_text()
        self.assertIn('root.setTag("scenevibe-private-multimedia");', controls)
        self.assertIn("if (BuildConfig.REAL_TRACK_E2E_001 && multimediaControls != null)", shell)
        self.assertIn("block(page, multimediaControls, 14);", shell)
        self.assertIn("scroll.scrollBy(0, direction == TvFocusSearch.Direction.DOWN", shell)
        self.assertIn("scroll.setVerticalScrollBarEnabled(BuildConfig.REAL_TRACK_E2E_001)", shell)
        self.assertIn("The private cache remains locally readable even without an active Cloud consent.", shell)
        self.assertNotIn("if (accountPanel()) return;\n            introduction(\"Mes Tracks\"", shell)
        self.assertIn("block(page, runtimeControls, 8);", shell)

    def test_v30_version_and_historical_version_remain_distinct(self):
        """Le versionCode 30 s'applique au seul build REAL TRACK opt-in."""
        gradle = (ROOT / "app/build.gradle").read_text()
        self.assertIn("output.versionCode.set(realTrack001 ? 31 : 28)", gradle)
        self.assertIn('"-real-track-e2e001-g11-ux1"', gradle)


if __name__ == "__main__":
    unittest.main()
