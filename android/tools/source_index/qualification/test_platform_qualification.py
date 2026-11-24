import unittest

from platform_qualification import (
    CatalogPresence,
    EmulationCapability,
    ProductCertification,
    is_publicly_eligible,
)


class PlatformQualificationTest(unittest.TestCase):
    def test_catalog_presence_never_implies_public_support(self):
        self.assertEqual("PRESENT", CatalogPresence.PRESENT.value)
        self.assertFalse(is_publicly_eligible(ProductCertification.DISCOVERED))
        self.assertFalse(is_publicly_eligible(ProductCertification.CERTIFICATION_CANDIDATE))

    def test_only_certified_states_are_publicly_eligible(self):
        eligible = {state for state in ProductCertification if is_publicly_eligible(state)}
        self.assertEqual({
            ProductCertification.OZ_CERTIFIED_LIBRETRO,
            ProductCertification.OZ_CERTIFIED_EXTERNAL,
            ProductCertification.OZ_CERTIFIED_CONDITIONAL,
        }, eligible)

    def test_emulation_route_is_independent_from_certification(self):
        self.assertEqual("BOTH_AVAILABLE", EmulationCapability.BOTH_AVAILABLE.value)
        self.assertFalse(is_publicly_eligible(ProductCertification.EMULATION_RESEARCH))


if __name__ == "__main__":
    unittest.main()
