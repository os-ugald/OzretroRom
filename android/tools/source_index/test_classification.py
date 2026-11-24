import copy
import json
import unittest
from pathlib import Path

from builder import build_shard

FIXTURE = Path(__file__).parent / "fixtures" / "minerva_snes.json"


class Adapter:
    provider = "minerva"

    def __init__(self, records):
        self._records = records

    def records(self):
        return self._records


class SourceClassificationTest(unittest.TestCase):
    def source(self):
        return json.loads(FIXTURE.read_text(encoding="utf-8"))["records"][0]

    def shard(self, source):
        return build_shard([Adapter([source])])

    def facets(self, publication, modification, regions=None, languages=None, translation=None):
        return {
            "regions": regions or [], "languages": languages or [],
            "publicationClass": publication, "modificationClass": modification,
            "translation": translation or {"sourceLanguage": None, "targetLanguages": [], "status": "UNKNOWN"},
            "regionalExclusivity": {"status": "UNKNOWN", "region": None},
            "classificationEvidence": [{"source": "TEST_CURATOR", "status": "CURATED"}],
            "evidenceStatus": "CURATED",
        }

    def test_worldwide_facets_keep_independent_concepts(self):
        cases = [
            ("RETAIL", "ORIGINAL", ["Japan"], [], None),
            ("AFTERMARKET", "ORIGINAL", ["World"], [], None),
            ("HOMEBREW", "ORIGINAL", [], [], None),
            ("PROTOTYPE", "UNKNOWN", ["Europe"], [], None),
            ("RETAIL", "TRANSLATION", ["Japan"], ["English"], {"sourceLanguage": "Japanese", "targetLanguages": ["English"], "status": "CURATED"}),
            ("UNKNOWN", "UNKNOWN", [], [], None),
        ]
        for publication, modification, regions, languages, translation in cases:
            source = self.source()
            source["releaseFacets"] = self.facets(publication, modification, regions, languages, translation)
            facets = self.shard(source)["releases"][0]["facets"]
            self.assertEqual(publication, facets["publicationClass"])
            self.assertEqual(modification, facets["modificationClass"])

    def test_region_never_implies_exclusivity_or_language(self):
        source = self.source()
        source["releaseFacets"] = self.facets("RETAIL", "ORIGINAL", ["Japan"])
        facets = self.shard(source)["releases"][0]["facets"]
        self.assertEqual(["Japan"], facets["regions"])
        self.assertEqual([], facets["languages"])
        self.assertEqual("UNKNOWN", facets["regionalExclusivity"]["status"])

    def test_facet_enrichment_never_changes_release_id(self):
        source = self.source()
        before = self.shard(source)["releases"][0]["releaseId"]
        source["releaseFacets"] = self.facets("RETAIL", "TRANSLATION", ["Japan"], ["English"], {
            "sourceLanguage": "Japanese", "targetLanguages": ["English"], "status": "CURATED"})
        after = self.shard(source)["releases"][0]["releaseId"]
        self.assertEqual(before, after)

    def test_provider_classification_and_tracker_churn_do_not_change_ids(self):
        source = self.source()
        first = self.shard(source)
        changed = copy.deepcopy(source)
        changed["providerClassifications"] = ["T-En Collection", "translation-evidence"]
        changed["trackerUrls"] = ["udp://changed.invalid/announce"]
        changed["collection"] = "Changed provider display classification"
        second = self.shard(changed)
        self.assertEqual(first["games"][0]["gameId"], second["games"][0]["gameId"])
        self.assertEqual(first["releases"][0]["releaseId"], second["releases"][0]["releaseId"])
        self.assertEqual(first["candidates"][0]["candidateId"], second["candidates"][0]["candidateId"])
        self.assertIn("T-En Collection", second["candidates"][0]["providerClassifications"])

    def test_publication_and_modification_are_orthogonal(self):
        for publication, modification in (("RETAIL", "ORIGINAL"), ("RETAIL", "TRANSLATION"),
                                          ("HOMEBREW", "ORIGINAL"), ("AFTERMARKET", "ORIGINAL"),
                                          ("PROTOTYPE", "TRANSLATION")):
            source = self.source()
            source["releaseFacets"] = self.facets(publication, modification)
            facets = self.shard(source)["releases"][0]["facets"]
            self.assertEqual((publication, modification), (facets["publicationClass"], facets["modificationClass"]))


if __name__ == "__main__":
    unittest.main()
