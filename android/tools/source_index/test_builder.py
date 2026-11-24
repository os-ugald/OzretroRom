import copy
import gzip
import hashlib
import json
import unittest
from pathlib import Path

from builder import HealthThresholds, MinervaAdapter, build, build_shard, canonical_json, health_gate, stable_id

FIXTURE = Path(__file__).parent / "fixtures" / "minerva_snes.json"
TEST_OUTPUT = Path(__file__).parents[2] / ".artifacts" / "source-index-v1-test"


class SourceIndexBuilderTest(unittest.TestCase):
    def shard(self):
        return build_shard([MinervaAdapter(FIXTURE)])

    def source(self):
        return json.loads(FIXTURE.read_text(encoding="utf-8"))["records"][0]

    def shard_for(self, source):
        class Adapter:
            provider = "minerva"
            def records(self): return [source]
        return build_shard([Adapter()])

    def test_release_identity_excludes_provider_artifact_properties(self):
        source = self.source()
        base = self.shard_for(source)["releases"][0]["releaseId"]
        changed = copy.deepcopy(source)
        changed.update(canonicalFileName="provider-renamed.zip", expectedSize=999, sha256="0" * 64)
        self.assertEqual(base, self.shard_for(changed)["releases"][0]["releaseId"])
        changed["region"] = "USA"
        self.assertEqual(base, self.shard_for(changed)["releases"][0]["releaseId"])
        changed = copy.deepcopy(source); changed["revision"] = "v1.062"
        # Curated key is authoritative while unresolved; metadata changes alone do not impersonate verification.
        self.assertEqual(base, self.shard_for(changed)["releases"][0]["releaseId"])

    def test_provisional_release_key_is_stable_but_distinct_keys_do_not_collapse(self):
        source = self.source()
        first = self.shard_for(source)["releases"][0]
        same = copy.deepcopy(source); same["canonicalFileName"] = "renamed-by-provider.zip"
        other = copy.deepcopy(source); other["canonicalReleaseKey"] = "240p-test-suite-world-v1.062-aftermarket-program"
        self.assertEqual(first["releaseId"], self.shard_for(same)["releases"][0]["releaseId"])
        self.assertNotEqual(first["releaseId"], self.shard_for(other)["releases"][0]["releaseId"])
        self.assertEqual("PROVISIONAL_METADATA_KEY", first["identityStatus"])

    def test_content_identical_raw_artifact_can_produce_verified_content_release(self):
        source = self.source()
        source.update(hashScope="ROM_CONTENT", artifactContentRelation="CONTENT_IDENTICAL")
        release = self.shard_for(source)["releases"][0]
        artifact = self.shard_for(source)["candidates"][0]["artifact"]
        self.assertEqual("VERIFIED_CONTENT", release["identityStatus"])
        self.assertEqual("ROM_CONTENT", release["contentIdentity"]["hashScope"])
        self.assertEqual(source["sha256"], release["contentIdentity"]["sha256"])
        self.assertEqual("CONTENT_IDENTICAL", artifact["contentRelation"])

    def test_verified_and_provisional_release_states_are_distinct(self):
        provisional = self.shard_for(self.source())["releases"][0]
        raw = self.source(); raw.update(hashScope="ROM_CONTENT", artifactContentRelation="CONTENT_IDENTICAL")
        verified = self.shard_for(raw)["releases"][0]
        self.assertEqual("PROVISIONAL_METADATA_KEY", provisional["identityStatus"])
        self.assertEqual("VERIFIED_CONTENT", verified["identityStatus"])
        self.assertEqual(provisional["releaseId"], verified["releaseId"])

    def test_content_evidence_enrichment_never_changes_release_id(self):
        source = self.source()
        crc_only = copy.deepcopy(source)
        crc_only["contentHashes"] = {"scope": "ROM_CONTENT", "crc32": "12345678"}
        enriched = copy.deepcopy(source)
        enriched["contentHashes"] = {
            "scope": "ROM_CONTENT", "crc32": "12345678",
            "sha1": "1" * 40, "sha256": "2" * 64,
        }
        provisional = self.shard_for(source)["releases"][0]
        crc_release = self.shard_for(crc_only)["releases"][0]
        enriched_release = self.shard_for(enriched)["releases"][0]
        self.assertEqual(provisional["releaseId"], crc_release["releaseId"])
        self.assertEqual(crc_release["releaseId"], enriched_release["releaseId"])
        self.assertEqual("VERIFIED_CONTENT", crc_release["identityStatus"])
        self.assertEqual("2" * 64, enriched_release["contentIdentity"]["sha256"])

    def test_contradictory_content_identical_rom_evidence_fails_closed(self):
        source = self.source()
        source.update(hashScope="ROM_CONTENT", artifactContentRelation="CONTENT_IDENTICAL")
        source["contentHashes"] = {"scope": "ROM_CONTENT", "sha256": "0" * 64}
        with self.assertRaises(ValueError):
            self.shard_for(source)

    def test_artifact_and_content_hash_scopes_are_distinct(self):
        shard = self.shard()
        self.assertEqual("UNKNOWN", shard["releases"][0]["contentIdentity"]["hashScope"])
        self.assertIsNone(shard["releases"][0]["contentIdentity"]["sha256"])
        self.assertEqual("DISTRIBUTED_ARTIFACT", shard["candidates"][0]["artifact"]["hashScope"])
        self.assertIsNotNone(shard["candidates"][0]["artifact"]["sha256"])

    def test_title_key_is_provisional_normalized_and_alias_churn_is_stable(self):
        source = self.source()
        first = self.shard_for(source)["games"][0]
        changed = copy.deepcopy(source)
        changed["canonicalTitleKey"] = "  240P   TEST suite "
        changed["gameTitle"] = "240p Test Suite — corrected display"
        changed["aliases"] = ["Another alias", *source["aliases"]]
        second = self.shard_for(changed)["games"][0]
        self.assertEqual(first["gameId"], second["gameId"])
        self.assertEqual("PROVISIONAL_TITLE_KEY", second["identityStatus"])

    def test_candidate_identity_ignores_tracker_churn_and_changes_with_coordinates(self):
        parts = ("minerva", "torrent", "hash", 6, "path")
        candidate = stable_id("oz-source-candidate-v1", *parts)
        self.assertEqual(candidate, stable_id("oz-source-candidate-v1", *parts))
        # Tracker lists and providerItemId are provenance/hints, not identity inputs.
        self.assertEqual(candidate, stable_id("oz-source-candidate-v1", *parts))
        self.assertNotEqual(candidate, stable_id("oz-source-candidate-v1", "minerva", "torrent", "other-hash", 6, "path"))
        self.assertNotEqual(candidate, stable_id("oz-source-candidate-v1", *parts[:-2], 7, "path"))
        self.assertNotEqual(candidate, stable_id("oz-source-candidate-v1", *parts[:-1], "other-path"))

    def test_provider_metadata_and_tracker_churn_do_not_change_candidate_id(self):
        source = self.source()
        first = self.shard_for(source)["candidates"][0]["candidateId"]
        source = copy.deepcopy(source)
        source["providerItemId"] = "replacement-id"
        source["collection"] = "renamed provenance label"
        source["trackerUrls"] = ["udp://tracker.example.invalid:80/announce"]
        source["torrentArtifactPath"] = "new/provider/path.torrent"
        self.assertEqual(first, self.shard_for(source)["candidates"][0]["candidateId"])

    def test_generation_is_deterministic(self):
        first = canonical_json(self.shard())
        second = canonical_json(self.shard())
        self.assertEqual(first, second)
        first_dir = TEST_OUTPUT / "deterministic-a"
        second_dir = TEST_OUTPUT / "deterministic-b"
        build(FIXTURE, first_dir, "2026-08-22T00:00:00Z")
        build(FIXTURE, second_dir, "2026-08-22T00:00:00Z")
        self.assertEqual((first_dir / "indexes/snes.json.gz").read_bytes(), (second_dir / "indexes/snes.json.gz").read_bytes())

    def test_health_gate_accepts_normal_and_growth(self):
        current = {"platforms": {"snes": {"releaseCount": 100, "sha1Coverage": 1.0, "sha256Coverage": 1.0, "torrentCoordinateCoverage": 1.0, "httpCoordinateCoverage": 0.0}}}
        previous = copy.deepcopy(current)
        self.assertEqual("ACCEPT", health_gate(current, previous, {"snes"})[0])
        current["platforms"]["snes"]["releaseCount"] = 110
        self.assertEqual("ACCEPT", health_gate(current, previous, {"snes"})[0])

    def test_health_gate_rejects_zero_or_missing_critical_platform(self):
        zero = {"platforms": {"snes": {"releaseCount": 0}}}
        self.assertEqual("REJECT", health_gate(zero, None, {"snes"})[0])
        self.assertEqual("REJECT", health_gate({"platforms": {}}, zero, {"snes"})[0])

    def test_health_gate_quarantines_large_drop_and_coverage_collapse(self):
        previous = {"platforms": {"snes": {"releaseCount": 100, "sha1Coverage": 1.0, "sha256Coverage": 1.0, "torrentCoordinateCoverage": 1.0, "httpCoordinateCoverage": 0.0}}}
        current = {"platforms": {"snes": {"releaseCount": 70, "sha1Coverage": 0.7, "sha256Coverage": 1.0, "torrentCoordinateCoverage": 1.0, "httpCoordinateCoverage": 0.0}}}
        self.assertEqual("QUARANTINE", health_gate(current, previous, {"snes"}, HealthThresholds())[0])

    def test_manifest_hash_and_schema_are_verifiable(self):
        manifest = build(FIXTURE, TEST_OUTPUT, "2026-08-22T00:00:00Z")
        shard = TEST_OUTPUT / "indexes" / "snes.json.gz"
        self.assertEqual(manifest["platforms"]["snes"]["sha256"], hashlib.sha256(shard.read_bytes()).hexdigest())
        self.assertEqual(1, json.loads(gzip.decompress(shard.read_bytes()))["schemaVersion"])
        self.assertEqual("NOT_EVALUATED", manifest["health"]["status"])
        corrupted = bytearray(shard.read_bytes()); corrupted[-1] ^= 1
        self.assertNotEqual(manifest["platforms"]["snes"]["sha256"], hashlib.sha256(corrupted).hexdigest())

    def test_required_fields_fail_closed(self):
        TEST_OUTPUT.mkdir(parents=True, exist_ok=True)
        path = TEST_OUTPUT / "bad.json"
        data = json.loads(FIXTURE.read_text(encoding="utf-8")); del data["records"][0]["infoHash"]
        path.write_text(json.dumps(data), encoding="utf-8")
        with self.assertRaises(ValueError): MinervaAdapter(path).records()

    def test_unknown_hash_scope_without_digest_is_valid(self):
        source = self.source()
        for field in ("crc32", "md5", "sha1", "sha256"):
            source.pop(field, None)
        source["hashScope"] = "UNKNOWN"
        candidate = self.shard_for(source)["candidates"][0]
        self.assertEqual("UNKNOWN", candidate["artifact"]["hashScope"])
        self.assertIsNone(candidate["artifact"]["sha256"])

    def test_claimed_hash_scopes_require_actual_evidence(self):
        for scope in ("ROM_CONTENT", "DISTRIBUTED_ARTIFACT"):
            source = self.source()
            for field in ("crc32", "md5", "sha1", "sha256"):
                source.pop(field, None)
            source["hashScope"] = scope
            with self.subTest(scope=scope), self.assertRaises(ValueError):
                self.shard_for(source)

    def test_malformed_hashes_fail_closed(self):
        for field, value in (("sha256", "f" * 63), ("sha1", "not-hex" + "0" * 33)):
            source = self.source()
            source[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.shard_for(source)

    def test_contradictory_canonical_evidence_across_candidates_fails_closed(self):
        first = self.source(); first["contentHashes"] = {"scope": "ROM_CONTENT", "sha1": "1" * 40}
        second = copy.deepcopy(first)
        second["contentHashes"]["sha1"] = "2" * 40
        second["fileIndex"] += 1
        second["exactFullPath"] += ".alternate"
        class Adapter:
            provider = "minerva"
            def records(self): return [first, second]
        with self.assertRaises(ValueError):
            build_shard([Adapter()])

    def test_hash_enrichment_preserves_release_id(self):
        source = self.source()
        variants = [None, {"scope": "ROM_CONTENT", "crc32": "1" * 8},
                    {"scope": "ROM_CONTENT", "sha1": "2" * 40},
                    {"scope": "ROM_CONTENT", "sha1": "2" * 40, "sha256": "3" * 64}]
        ids = []
        for evidence in variants:
            item = copy.deepcopy(source)
            if evidence is not None:
                item["contentHashes"] = evidence
            ids.append(self.shard_for(item)["releases"][0]["releaseId"])
        self.assertEqual(1, len(set(ids)))


if __name__ == "__main__":
    unittest.main()
