import gzip
import hashlib
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / "tools/source_index/fixtures/gba_coverage05_contract.json"
PRODUCTION_ASSET = ROOT / "app/src/main/assets/source-index/gba.json.gz.bin"


class GbaReproContractTest(unittest.TestCase):
    def test_contract_pins_portable_inputs_and_expected_output(self):
        contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
        self.assertEqual("gba", contract["platform"])
        self.assertEqual("source-index-v1.0", contract["pipeline"]["builderVersion"])
        self.assertEqual(3, len(contract["collections"]))
        self.assertEqual(
            ["PRIMARY_CONTROLLED", "AFTERMARKET", "TRANSLATION"],
            [item["role"] for item in contract["collections"]],
        )
        expected = contract["expectedOutput"]
        self.assertEqual(2368, expected["gameCount"])
        self.assertEqual(3962, expected["releaseCount"])
        self.assertEqual(4021, expected["candidateCount"])
        self.assertEqual(1313406, expected["compressedBytes"])
        self.assertEqual(16725666, expected["uncompressedBytes"])
        self.assertEqual(
            "6ff70a3763fbbd6871c7e9a29135161c84deec4144d470ff5bb993d519d2af50",
            expected["compressedSha256"],
        )

    def test_production_asset_matches_contract(self):
        contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
        compressed = PRODUCTION_ASSET.read_bytes()
        document = json.loads(gzip.decompress(compressed))
        expected = contract["expectedOutput"]
        self.assertEqual(expected["compressedBytes"], len(compressed))
        self.assertEqual(expected["uncompressedBytes"], len(gzip.decompress(compressed)))
        self.assertEqual(expected["compressedSha256"], hashlib.sha256(compressed).hexdigest())
        self.assertEqual(expected["gameCount"], len(document["games"]))
        self.assertEqual(expected["releaseCount"], len(document["releases"]))
        self.assertEqual(expected["candidateCount"], len(document["candidates"]))


if __name__ == "__main__":
    unittest.main()
