package cl.gakuto.kizuna.catalog

data class CatalogProgress(
    val stage: String,
    val downloaded: Long = 0L,
    val total: Long = -1L,
)
