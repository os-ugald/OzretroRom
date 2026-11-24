import unittest

from genesis_census import classify_collection, included_v1, inventory
from snes_benchmark import canonical_metadata


class GenesisCensusTest(unittest.TestCase):
    def test_every_genesis_adjacent_collection_is_classified(self):
        rows = inventory([
            {"collectionName": "No-Intro - Sega - Mega Drive - Genesis", "categoryGroup": "No-Intro"},
            {"collectionName": "T-En Collection - Sega - Mega Drive [T-En] Collection", "categoryGroup": "T-En Collection"},
            {"collectionName": "No-Intro - Sega - 32X", "categoryGroup": "No-Intro"},
        ])
        self.assertEqual(3, len(rows))
        self.assertNotIn("UNKNOWN_REQUIRES_REVIEW", {row["classification"] for row in rows})

    def test_32x_and_specialty_are_not_included(self):
        self.assertEqual("SEPARATE_SYSTEM", classify_collection("No-Intro - Sega - 32X", "No-Intro"))
        self.assertEqual("SEPARATE_MEDIA_GATE", classify_collection("T-En Collection - Sega - Mega Drive - MSU-MD", "T-En Collection"))
        self.assertEqual("DEFERRED_SPECIALTY", classify_collection("T-En Collection - Sega - Mega Drive - Enhanced Colors", "T-En Collection"))
        self.assertEqual("DEFERRED_TRANSLATION", classify_collection("T-En Collection - Sega - Mega Drive [T-En] Collection", "T-En Collection"))

    def test_v1_filter_cannot_silently_include_deferred_or_32x(self):
        rows = [
            {"classification": "INCLUDED_V1", "collection": "base"},
            {"classification": "DEFERRED_TRANSLATION", "collection": "translation"},
            {"classification": "SEPARATE_SYSTEM", "collection": "32x"},
        ]
        self.assertEqual(["base"], [row["collection"] for row in included_v1(rows)])

    def test_aftermarket_and_region_evidence_are_preserved(self):
        metadata = canonical_metadata("Example (Japan) (Aftermarket) (Unl).zip", "aftermarket", "genesis")
        self.assertEqual(["Japan"], metadata["releaseFacets"]["regions"])
        self.assertEqual("AFTERMARKET", metadata["releaseFacets"]["publicationClass"])
        self.assertEqual("UNKNOWN", metadata["releaseFacets"]["regionalExclusivity"]["status"])

    def test_non_region_parenthetical_does_not_pollute_region(self):
        metadata = canonical_metadata("Example (Pocket Monsters) (Beta).zip", "base", "genesis")
        self.assertEqual([], metadata["releaseFacets"]["regions"])
        self.assertEqual("UNKNOWN", metadata["region"])

    def test_platform_partitions_identical_release_metadata(self):
        keys = {canonical_metadata("Example (World).zip", "base", platform)["canonicalReleaseKey"]
                for platform in ("genesis", "nes", "snes")}
        self.assertEqual(3, len(keys))


if __name__ == "__main__":
    unittest.main()
