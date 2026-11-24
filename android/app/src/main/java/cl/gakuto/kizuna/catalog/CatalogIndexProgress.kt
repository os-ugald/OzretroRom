package cl.gakuto.kizuna.catalog

data class CatalogIndexProgress(
    val stage: String,
    val preparedGames: Int = 0,
)
