package cl.gakuto.kizuna.catalog.background

import android.app.job.JobParameters

internal fun catalogPreparationShouldRetry(stopReason: Int): Boolean = when (stopReason) {
    JobParameters.STOP_REASON_CANCELLED_BY_APP,
    JobParameters.STOP_REASON_USER -> false

    else -> true
}

internal fun catalogPreparationStopReasonLabel(stopReason: Int): String = when (stopReason) {
    JobParameters.STOP_REASON_CANCELLED_BY_APP -> "cancelación solicitada por Oz"
    JobParameters.STOP_REASON_PREEMPT -> "Android dio prioridad a otra tarea"
    JobParameters.STOP_REASON_TIMEOUT -> "límite temporal de Android"
    JobParameters.STOP_REASON_DEVICE_STATE -> "estado del dispositivo"
    JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "conectividad no disponible"
    JobParameters.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "batería baja"
    JobParameters.STOP_REASON_CONSTRAINT_CHARGING -> "cambio del estado de carga"
    JobParameters.STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "cambio del estado de inactividad"
    JobParameters.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "almacenamiento bajo"
    JobParameters.STOP_REASON_QUOTA -> "cuota de ejecución de Android"
    JobParameters.STOP_REASON_BACKGROUND_RESTRICTION -> "restricción de segundo plano"
    JobParameters.STOP_REASON_APP_STANDBY -> "aplicación en espera"
    JobParameters.STOP_REASON_USER -> "detención solicitada por el usuario"
    JobParameters.STOP_REASON_SYSTEM_PROCESSING -> "carga, memoria o temperatura del sistema"
    JobParameters.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED -> "reprogramación del sistema"
    JobParameters.STOP_REASON_TIMEOUT_ABANDONED -> "tarea sin respuesta al vencer el tiempo"
    else -> "interrupción del sistema"
}

internal fun catalogPreparationSuspendedMessage(stopReason: Int, willRetry: Boolean): String {
    val action = if (willRetry) {
        "Android reintentará; también puedes abrir Oz y pulsar Continuar"
    } else {
        "abre Oz y pulsa Continuar"
    }
    return "Preparación suspendida por ${catalogPreparationStopReasonLabel(stopReason)} · $action"
}
