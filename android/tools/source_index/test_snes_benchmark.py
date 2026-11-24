import hashlib
import unittest

from snes_benchmark import (
    BencodeDecoder,
    canonical_metadata,
    legacy_prefix_collision_risks,
    overlap_warnings,
    parse_torrent,
)


class SnesBenchmarkTest(unittest.TestCase):
    def test_bencode_parser_preserves_exact_info_bytes(self):
        info = b"d4:name4:root5:fileslee"
        raw = b"d8:announce15:https://tracker4:info" + info + b"e"
        root, actual_info = parse_torrent(raw)
        self.assertEqual(info, actual_info)
        self.assertEqual("root", root[b"info"][b"name"].decode())
        self.assertEqual(hashlib.sha1(info).hexdigest(), hashlib.sha1(actual_info).hexdigest())

    def test_decoder_rejects_no_fields_by_guessing(self):
        decoded = BencodeDecoder(b"d3:fooi1ee").decode()
        self.assertEqual({b"foo": 1}, decoded)

    def test_release_metadata_is_deterministic_and_provisional(self):
        first = canonical_metadata("Game (USA) (Rev 1).zip", "base")
        second = canonical_metadata("Game (USA) (Rev 1).zip", "base")
        self.assertEqual(first, second)
        self.assertEqual("USA", first["region"])
        self.assertEqual("Rev 1", first["revision"])
        self.assertEqual("snes|game|usa|rev 1", first["canonicalReleaseKey"])

    def test_platform_parameter_keeps_release_identity_cross_system_safe(self):
        snes = canonical_metadata("Game (USA) (Rev 1).zip", "base", "snes")
        nes = canonical_metadata("Game (USA) (Rev 1).zip", "base", "nes")
        self.assertEqual("nes|game|usa|rev 1", nes["canonicalReleaseKey"])
        self.assertNotEqual(snes["canonicalReleaseKey"], nes["canonicalReleaseKey"])

    def test_aftermarket_is_explicit_not_inferred_as_retail(self):
        item = canonical_metadata("Homebrew (World).zip", "aftermarket")
        self.assertEqual("aftermarket", item["releaseType"])
        self.assertEqual("AFTERMARKET", item["releaseFacets"]["publicationClass"])
        self.assertEqual("ORIGINAL", item["releaseFacets"]["modificationClass"])
        tagged = canonical_metadata("Homebrew (World) (Aftermarket).zip", "base")
        self.assertEqual("AFTERMARKET", tagged["releaseFacets"]["publicationClass"])

    def test_filename_region_is_provider_evidence_not_exclusivity_or_language(self):
        item = canonical_metadata("Game (Japan).zip", "base")
        facets = item["releaseFacets"]
        self.assertEqual(["Japan"], facets["regions"])
        self.assertEqual([], facets["languages"])
        self.assertEqual("UNKNOWN", facets["regionalExclusivity"]["status"])
        self.assertEqual("PROVIDER_DERIVED", facets["evidenceStatus"])

    def test_explicit_language_tag_is_preserved_without_region_inference(self):
        item = canonical_metadata("Game (Japan) (En,Fr).zip", "base", "gba")
        self.assertEqual(["English", "French"], item["releaseFacets"]["languages"])
        self.assertEqual("UNKNOWN", item["releaseFacets"]["regionalExclusivity"]["status"])

    def test_prototype_tag_variants_are_explicit_without_changing_title_key(self):
        proto = canonical_metadata("Game (USA) (Proto).zip", "base", "gba")
        prototype = canonical_metadata("Game (USA) (Prototype).zip", "base", "gba")
        self.assertEqual("PROTOTYPE", proto["releaseFacets"]["publicationClass"])
        self.assertEqual("PROTOTYPE", prototype["releaseFacets"]["publicationClass"])
        self.assertEqual("game", proto["canonicalTitleKey"])
        self.assertEqual("game", prototype["canonicalTitleKey"])

    def test_release_classes_remain_distinct(self):
        self.assertEqual("RETAIL", canonical_metadata("Game (USA).zip", "base", "gba")["releaseFacets"]["publicationClass"])
        self.assertEqual("BETA", canonical_metadata("Game (USA) (Beta).zip", "base", "gba")["releaseFacets"]["publicationClass"])
        self.assertEqual("DEMO", canonical_metadata("Game (USA) (Demo).zip", "base", "gba")["releaseFacets"]["publicationClass"])
        self.assertEqual("AFTERMARKET", canonical_metadata("Game (World).zip", "aftermarket", "gba")["releaseFacets"]["publicationClass"])

    def test_prototype_classification_preserves_identity_inputs(self):
        retail = canonical_metadata("Game (USA).zip", "base", "gba")
        proto = canonical_metadata("Game (USA) (Proto).zip", "base", "gba")
        self.assertEqual(retail["canonicalTitleKey"], proto["canonicalTitleKey"])
        self.assertNotEqual(retail["canonicalReleaseKey"], proto["canonicalReleaseKey"])
        self.assertEqual("PROTOTYPE", proto["releaseFacets"]["publicationClass"])

    def test_proto_abbreviation_does_not_change_frozen_non_gba_platforms(self):
        item = canonical_metadata("Game (USA) (Proto).zip", "base", "snes")
        self.assertEqual("RETAIL", item["releaseFacets"]["publicationClass"])

    def test_t_en_is_translation_release_metadata_without_new_game(self):
        original = canonical_metadata("Game (Japan).zip", "base", "gba")
        translated = canonical_metadata(
            "Game (Japan) [T-En by Translator v1.0] [i].zip", "base", "gba"
        )
        self.assertEqual(original["canonicalTitleKey"], translated["canonicalTitleKey"])
        self.assertNotEqual(original["canonicalReleaseKey"], translated["canonicalReleaseKey"])
        self.assertEqual("TRANSLATION", translated["releaseFacets"]["modificationClass"])
        self.assertEqual(
            {"sourceLanguage": None, "targetLanguages": [], "status": "UNKNOWN"},
            translated["releaseFacets"]["translation"],
        )

    def test_t_en_versions_and_translators_remain_distinct(self):
        first = canonical_metadata("Game (Japan) [T-En by A v1.0].zip", "base", "gba")
        second = canonical_metadata("Game (Japan) [T-En by B v1.0].zip", "base", "gba")
        revision = canonical_metadata("Game (Japan) [T-En by A v2.0].zip", "base", "gba")
        self.assertEqual(first["canonicalTitleKey"], second["canonicalTitleKey"])
        self.assertNotEqual(first["canonicalReleaseKey"], second["canonicalReleaseKey"])
        self.assertNotEqual(first["canonicalReleaseKey"], revision["canonicalReleaseKey"])

    def test_t_en_is_ignored_for_frozen_non_gba_platforms(self):
        item = canonical_metadata("Game (Japan) [T-En by A v1.0].zip", "base", "snes")
        self.assertEqual("RETAIL", item["releaseFacets"]["publicationClass"])
        self.assertEqual("ORIGINAL", item["releaseFacets"]["modificationClass"])
        self.assertNotIn("t-en by a v1.0", item["canonicalReleaseKey"])

    def test_legacy_prefix_risk_is_diagnostic_only(self):
        shard = {"games": [
            {"gameId": "one", "canonicalTitle": "Game", "canonicalTitleKey": "game"},
            {"gameId": "two", "canonicalTitle": "Game 2", "canonicalTitleKey": "game 2"},
            {"gameId": "other", "canonicalTitle": "Other", "canonicalTitleKey": "other"},
        ]}
        risks = legacy_prefix_collision_risks(shard)
        self.assertEqual(1, len(risks))
        self.assertEqual("one", risks[0]["targetGameId"])
        self.assertEqual("two", risks[0]["examples"][0]["gameId"])

    def test_overlap_warning_preserves_candidate_transport_coordinates(self):
        shard = {
            "games": [{"gameId": "game", "canonicalTitle": "Game"}],
            "releases": [{
                "releaseId": "release",
                "gameId": "game",
                "canonicalReleaseKey": "gba|game|world",
            }],
            "candidates": [{
                "candidateId": f"candidate-{index}",
                "releaseId": "release",
                "collection": f"collection-{index}",
                "torrent": {
                    "infoHash": f"hash-{index}",
                    "fileIndex": index,
                    "exactFullPath": f"path-{index}.zip",
                },
            } for index in range(2)],
        }
        warnings = overlap_warnings(shard)
        self.assertEqual(1, len(warnings))
        self.assertEqual("release", warnings[0]["releaseId"])
        self.assertEqual("hash-0", warnings[0]["candidates"][0]["infoHash"])
        self.assertEqual(1, warnings[0]["candidates"][1]["fileIndex"])
        self.assertEqual("path-1.zip", warnings[0]["candidates"][1]["exactFullPath"])


if __name__ == "__main__":
    unittest.main()
