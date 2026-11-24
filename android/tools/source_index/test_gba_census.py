import unittest

from gba_census import classify_collection, exact_filename_overlaps, naming_convention, v1_disposition


class GbaCensusTest(unittest.TestCase):
    def test_all_known_gba_collections_have_controlled_classification(self):
        cases = {
            ("Nintendo - Game Boy Advance", "No-Intro"): "PRIMARY_CONTROLLED",
            ("Nintendo - Game Boy Advance (Aftermarket)", "No-Intro"): "AFTERMARKET",
            ("Nintendo - Game Boy Advance [T-En] Collection", "T-En Collection"): "TRANSLATION",
            ("Nintendo - Game Boy Advance (e-Reader)", "No-Intro"): "SPECIALTY",
            ("Nintendo - Game Boy Advance (Multiboot)", "No-Intro"): "SPECIALTY",
            ("Nintendo - Game Boy Advance (Play-Yan)", "No-Intro"): "SPECIALTY",
            ("Nintendo - Game Boy Advance (Private)", "No-Intro"): "PRIVATE",
            ("RA - Nintendo Game Boy Advance", "RetroAchievements"): "RETROACHIEVEMENTS_NOISE",
            ("Source Code - Nintendo - Game Boy Advance", "No-Intro"): "SPECIALTY",
        }
        for (name, group), expected in cases.items():
            with self.subTest(name=name):
                self.assertEqual(expected, classify_collection(name, group))

    def test_only_primary_and_plain_aftermarket_enter_v1(self):
        self.assertEqual("INCLUDED_V1", v1_disposition("PRIMARY_CONTROLLED"))
        self.assertEqual("INCLUDED_V1", v1_disposition("AFTERMARKET"))
        for classification in ("TRANSLATION", "SPECIALTY", "PRIVATE", "RETROACHIEVEMENTS_NOISE", "UNKNOWN"):
            self.assertEqual("DEFERRED", v1_disposition(classification))

    def test_exact_overlap_is_diagnostic_and_does_not_merge(self):
        overlaps, warnings = exact_filename_overlaps({
            "base": ["Game (USA).zip", "Only base (Japan).zip"],
            "ra": ["Game (USA).zip", "Only RA (World).zip"],
        })
        self.assertEqual({"ra": 1}, overlaps["base"])
        self.assertEqual({"base": 1}, overlaps["ra"])
        self.assertEqual([{"fileName": "Game (USA).zip", "collections": ["base", "ra"]}], warnings)

    def test_naming_convention_requires_real_parenthetical_evidence(self):
        self.assertEqual(
            "NO_INTRO_TITLE_WITH_PARENTHETICAL_TAGS_ZIP",
            naming_convention(["A (USA).zip", "B (Japan).zip"]),
        )
        self.assertEqual("ZIP_WRAPPED_PROVIDER_NAMES", naming_convention(["README.zip"]))


if __name__ == "__main__":
    unittest.main()
