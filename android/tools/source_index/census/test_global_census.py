import hashlib
import json
import unittest

from census import discover_all_torrents, parse_dashboard, stable_collection_id
from global_census import TELEMETRY_SEMANTICS, aggregate, classify_platform, normalize_snapshot, normalize_torrent, priority_for


TORRENTS = [
    {"name": "Minerva_Myrient - No-Intro - Nintendo - Super Nintendo Entertainment System", "category_group": "No-Intro", "info_hash": "1" * 40, "size_bytes": 100, "seeders": 7, "leechers": 2, "timestamp": "2026-04-14T00:11:35.000Z"},
    {"name": "Minerva_Myrient - No-Intro - Sega - Mega Drive - Genesis", "category_group": "No-Intro", "info_hash": "2" * 40, "size_bytes": 200, "seeders": 3, "leechers": 1, "timestamp": "2026-04-14T00:11:35.000Z"},
    {"name": "Minerva_Myrient - Redump - Sony - PlayStation", "category_group": "Redump", "info_hash": "3" * 40, "size_bytes": 300, "seeders": 1, "leechers": 9, "timestamp": "2026-04-14T00:11:35.000Z"},
    {"name": "Minerva_Myrient - MAME - ROMs (non-merged)", "category_group": "MAME", "info_hash": "4" * 40, "size_bytes": 400, "seeders": 0, "leechers": 0, "timestamp": "2026-04-14T00:11:35.000Z"},
    {"name": "Minerva_Myrient - Mystery - Unmapped Computer", "category_group": "Mystery", "info_hash": "5" * 40, "size_bytes": 500, "timestamp": "2026-04-14T00:11:35.000Z"},
]
RAW = json.dumps({"metadata": {"timestamp": "2026-04-14T00:11:35.000Z", "total_torrents": len(TORRENTS)}, "torrents": TORRENTS}, separators=(",", ":")).encode()


class GlobalCensusTest(unittest.TestCase):
    def test_raw_dashboard_parse_retains_all_torrents(self):
        parsed = parse_dashboard(RAW)
        self.assertEqual(5, len(discover_all_torrents(parsed)))
        with self.assertRaises(ValueError):
            parse_dashboard(json.dumps({"metadata": {"total_torrents": 2}, "torrents": [{}]}).encode())

    def test_category_group_and_timestamp_are_preserved(self):
        normalized = normalize_snapshot(RAW)
        self.assertEqual("2026-04-14T00:11:35.000Z", normalized["dashboardTimestamp"])
        self.assertEqual({"MAME", "Mystery", "No-Intro", "Redump"}, {item["categoryGroup"] for item in normalized["collections"]})

    def test_nintendo_sega_sony_and_arcade_examples(self):
        self.assertEqual(("nintendo-snes", "snes"), classify_platform(TORRENTS[0]["name"]))
        self.assertEqual(("sega-genesis", "genesis"), classify_platform(TORRENTS[1]["name"]))
        self.assertEqual(("sony-ps1", "ps1"), classify_platform(TORRENTS[2]["name"]))
        self.assertEqual(("arcade-mame", "UNKNOWN"), classify_platform(TORRENTS[3]["name"]))

    def test_unknown_platform_remains_unknown(self):
        self.assertEqual(("UNKNOWN", "UNKNOWN"), classify_platform(TORRENTS[4]["name"]))

    def test_collection_identity_and_snapshot_hash_are_deterministic(self):
        self.assertEqual(stable_collection_id("No-Intro", "SNES"), stable_collection_id("no-intro", "snes"))
        self.assertEqual(hashlib.sha256(RAW).hexdigest(), hashlib.sha256(bytes(RAW)).hexdigest())

    def test_stale_telemetry_never_becomes_live_health(self):
        item = normalize_torrent(TORRENTS[0], "fallback")
        self.assertEqual(TELEMETRY_SEMANTICS, item["telemetry"]["semantics"])
        self.assertEqual(7, item["telemetry"]["reportedSeeders"])
        self.assertEqual("UNKNOWN", item["telemetry"]["currentSwarmHealth"])

    def test_priority_rules_are_deterministic(self):
        self.assertEqual("PRIORITY_A", priority_for("ngpc", "LIBRETRO_AVAILABLE", "snk-ngpc"))
        self.assertEqual("PRIORITY_B", priority_for("snes", "LIBRETRO_AVAILABLE", "nintendo-snes"))
        self.assertEqual("NEEDS_RESEARCH", priority_for("UNKNOWN", "RESEARCH_REQUIRED", "UNKNOWN"))

    def test_catalog_emulation_and_product_certification_are_independent(self):
        item = normalize_torrent(TORRENTS[0], "fallback")
        self.assertEqual("PRESENT", item["catalogPresence"])
        self.assertEqual("LIBRETRO_AVAILABLE", item["emulationCapability"])
        self.assertEqual("DISCOVERED", item["productCertification"])

    def test_same_snapshot_produces_identical_normalized_output(self):
        self.assertEqual(normalize_snapshot(RAW), normalize_snapshot(bytes(RAW)))
        groups, platforms, opportunities = aggregate(normalize_snapshot(RAW))
        self.assertEqual(4, len(groups))
        self.assertEqual(5, sum(item["collectionCount"] for item in platforms))
        self.assertTrue(any(item["ozSystemId"] == "snes" for item in opportunities))


if __name__ == "__main__":
    unittest.main()
