package cl.gakuto.kizuna.catalog

data class CatalogSystem(
    val id: String,
    val name: String,
    val datUrl: String,
    val genreDatUrl: String?,
    val thumbnailRepo: String,
    val platform: String,
    val coreId: String?,
    val virtualExtension: String,
    val launchValidated: Boolean,
)
