package cl.gakuto.kizuna.catalog.background

import android.content.Context
import cl.gakuto.kizuna.catalog.LibretroCatalogRepository
import cl.gakuto.kizuna.data.OzDatabase
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class CatalogPreparationManager(
    context: Context,
    database: OzDatabase,
) {
    data class Progress(
        val processedSystems: Int,
        val totalTargets: Int,
        val currentSystem: String,
        val stage: String,
        val failedCount: Int,
        val currentPreparedGames: Int = 0,
    )

    data class Summary(
        val processedSystems: Int,
        val totalTargets: Int,
        val readyTargets: Int,
        val remainingSystems: Int,
        val failedCount: Int,
        val errors: List<String>,
    )

    private val catalog = LibretroCatalogRepository(context.applicationContext, database)

    suspend fun prepare(
        requestedSystemIds: Collection<String>,
        onProgress: (Progress) -> Unit,
    ): Summary {
        val requested = requestedSystemIds.toSet()
        val targets = catalog.systems().filter { it.id in requested }
        require(targets.isNotEmpty()) { "No hay sistemas válidos para preparar." }

        var processed = 0
        val errors = mutableListOf<String>()
        fun report(progress: Progress) {
            runCatching { onProgress(progress) }
        }

        targets.forEachIndexed { index, system ->
            coroutineContext.ensureActive()
            if (!catalog.needsIndex(system)) {
                processed++
                report(
                    Progress(
                        processedSystems = processed,
                        totalTargets = targets.size,
                        currentSystem = system.name,
                        stage = "${system.name}: ya estaba preparado",
                        failedCount = errors.size,
                    ),
                )
                return@forEachIndexed
            }

            report(
                Progress(
                    processedSystems = processed,
                    totalTargets = targets.size,
                    currentSystem = system.name,
                    stage = "Preparando ${index + 1}/${targets.size}: ${system.name}",
                    failedCount = errors.size,
                ),
            )
            val result = catalog.index(system) { indexProgress ->
                report(
                    Progress(
                        processedSystems = processed,
                        totalTargets = targets.size,
                        currentSystem = system.name,
                        stage = indexProgress.stage,
                        failedCount = errors.size,
                        currentPreparedGames = indexProgress.preparedGames,
                    ),
                )
            }
            processed++
            result.exceptionOrNull()?.let { error ->
                errors += "${system.name}: ${error.message ?: error.javaClass.simpleName}".take(240)
            }
            report(
                Progress(
                    processedSystems = processed,
                    totalTargets = targets.size,
                    currentSystem = system.name,
                    stage = if (result.isSuccess) {
                        "${system.name}: preparado"
                    } else {
                        "${system.name}: pendiente; continuando con el siguiente"
                    },
                    failedCount = errors.size,
                ),
            )
        }

        coroutineContext.ensureActive()
        val readyTargets = targets.count { !catalog.needsIndex(it) }
        return Summary(
            processedSystems = processed,
            totalTargets = targets.size,
            readyTargets = readyTargets,
            remainingSystems = targets.size - readyTargets,
            failedCount = errors.size,
            errors = errors,
        )
    }
}
