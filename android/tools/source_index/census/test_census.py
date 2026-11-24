import copy
import json
import unittest

from census import UNKNOWN, classify, discover_torrents, normalized_collection, parse_entries, platform_family, stable_collection_id


TORRENT = {"name": "Minerva_Myrient - No-Intro - Nintendo - Nintendo 64 (BigEndian)",
           "category_group": "No-Intro", "info_hash": "a" * 40, "size_bytes": 123, "timestamp": "2026-01-01T00:00:00Z"}
HTML = '''<div class="entry" data-name="same (usa).zip"><a href="/rom?id=1">Same (USA).zip</a><span>1 KB</span><a onclick="downloadMagnet('magnet:?xt=urn:btih:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')" data-m="0">x</a></div>
<div class="entry" data-name="same (usa).zip"><a href="/rom?id=2">Same (USA).zip</a><span>2 KB</span><a data-m="1">x</a></div>'''


class CensusTest(unittest.TestCase):
    def test_unknown_is_preserved_and_not_zero(self):
        item = normalized_collection(TORRENT, None, None)
        self.assertEqual(UNKNOWN, item["recordCount"])
        self.assertNotEqual(0, item["recordCount"])
        self.assertEqual(UNKNOWN, item["hashAvailability"]["SHA1"])

    def test_duplicate_filename_detection(self):
        item = normalized_collection(TORRENT, parse_entries(HTML), "url")
        self.assertEqual(2, item["recordCount"])
        self.assertEqual(1, item["uniqueFilenameCount"])
        self.assertEqual(1, item["duplicateFilenameCount"])

    def test_collection_identity_is_deterministic(self):
        self.assertEqual(stable_collection_id("No-Intro", "Nintendo - Game Boy"), stable_collection_id(" no-intro ", "nintendo - game boy"))
        self.assertNotEqual(stable_collection_id("No-Intro", "Nintendo - Game Boy"), stable_collection_id("No-Intro", "Nintendo - Game Boy Color"))

    def test_platform_classification_is_deterministic(self):
        self.assertEqual("NEEDS_NORMALIZATION", classify("Nintendo - Nintendo 64 (BigEndian)", "n64"))
        self.assertEqual("UNSUPPORTED_BY_OZ_CURRENTLY", classify("Nintendo - Nintendo DS (Decrypted)", "nds"))
        self.assertEqual("snes", platform_family("Nintendo - Super Nintendo Entertainment System"))
        self.assertEqual("nes", platform_family("Nintendo - Nintendo Entertainment System (Headerless)"))

    def test_hash_scope_header_and_byte_order_are_not_invented(self):
        base = copy.deepcopy(TORRENT); base["name"] = "Minerva_Myrient - No-Intro - Nintendo - Game Boy"
        item = normalized_collection(base, [], "url")
        self.assertEqual(UNKNOWN, item["hashScopeConfidence"])
        self.assertEqual(UNKNOWN, item["headerState"])
        self.assertEqual(UNKNOWN, item["byteOrder"])

    def test_explicit_header_and_byte_order_are_retained(self):
        item = normalized_collection(TORRENT, [], "url")
        self.assertEqual("BIG_ENDIAN", item["byteOrder"])
        headered = copy.deepcopy(TORRENT); headered["name"] = "Minerva_Myrient - No-Intro - Nintendo - Nintendo Entertainment System (Headered)"
        self.assertEqual("HEADERED", normalized_collection(headered, [], "url")["headerState"])

    def test_discovery_uses_live_names_not_a_fixed_collection_list(self):
        dashboard = {"torrents": [TORRENT, {"name": "Minerva_Myrient - No-Intro - Sega - Saturn"}]}
        self.assertEqual([TORRENT], discover_torrents(dashboard))

    def test_normalization_is_deterministic_and_unknown_fields_survive(self):
        first = normalized_collection(TORRENT, parse_entries(HTML), "url")
        second = normalized_collection(json.loads(json.dumps(TORRENT)), parse_entries(HTML), "url")
        self.assertEqual(first, second)
        self.assertEqual(UNKNOWN, first["releaseTypeDistribution"])


if __name__ == "__main__":
    unittest.main()
