"""Provider-neutral Oz platform qualification state and public visibility gate."""

from enum import Enum


class CatalogPresence(str, Enum):
    PRESENT = "PRESENT"
    ABSENT = "ABSENT"
    UNKNOWN = "UNKNOWN"


class EmulationCapability(str, Enum):
    LIBRETRO_AVAILABLE = "LIBRETRO_AVAILABLE"
    EXTERNAL_AVAILABLE = "EXTERNAL_AVAILABLE"
    BOTH_AVAILABLE = "BOTH_AVAILABLE"
    RESEARCH_REQUIRED = "RESEARCH_REQUIRED"
    UNAVAILABLE = "UNAVAILABLE"


class ProductCertification(str, Enum):
    DISCOVERED = "DISCOVERED"
    EMULATION_RESEARCH = "EMULATION_RESEARCH"
    CERTIFICATION_CANDIDATE = "CERTIFICATION_CANDIDATE"
    OZ_CERTIFIED_LIBRETRO = "OZ_CERTIFIED_LIBRETRO"
    OZ_CERTIFIED_EXTERNAL = "OZ_CERTIFIED_EXTERNAL"
    OZ_CERTIFIED_CONDITIONAL = "OZ_CERTIFIED_CONDITIONAL"
    REJECTED_QUALITY = "REJECTED_QUALITY"
    UNSUPPORTED = "UNSUPPORTED"


PUBLIC_CERTIFICATIONS = frozenset({
    ProductCertification.OZ_CERTIFIED_LIBRETRO,
    ProductCertification.OZ_CERTIFIED_EXTERNAL,
    ProductCertification.OZ_CERTIFIED_CONDITIONAL,
})


def is_publicly_eligible(certification: ProductCertification) -> bool:
    return certification in PUBLIC_CERTIFICATIONS
