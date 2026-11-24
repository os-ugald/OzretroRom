#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
read = lambda rel: (ROOT / rel).read_text(encoding='utf-8')

manifest = read('app/src/main/AndroidManifest.xml')
scheduler = read('app/src/main/java/cl/gakuto/kizuna/launch/RomDownloadScheduler.kt')
job = read('app/src/main/java/cl/gakuto/kizuna/launch/RomDownloadJobService.kt')
fgs = read('app/src/main/java/cl/gakuto/kizuna/launch/RomDownloadForegroundService.kt')
notify = read('app/src/main/java/cl/gakuto/kizuna/launch/RomDownloadNotification.kt')
tasks = read('app/src/main/java/cl/gakuto/kizuna/launch/RomDownloadTaskStore.kt')
downloader = read('app/src/main/java/cl/gakuto/kizuna/launch/AuthorizedSourceDownloader.kt')
session_store = read('app/src/main/java/cl/gakuto/kizuna/launch/SessionFileStore.kt')
session = read('app/src/main/java/cl/gakuto/kizuna/launch/SessionActivity.kt')
persistent = read('app/src/main/java/cl/gakuto/kizuna/launch/PersistentRomLibrary.kt')
app = read('app/src/main/java/cl/gakuto/kizuna/ui/compose/OzApp.kt')

checks = {
    'permission_uidt': 'android.permission.RUN_USER_INITIATED_JOBS' in manifest,
    'permission_notifications': 'android.permission.POST_NOTIFICATIONS' in manifest,
    'permission_fgs_data_sync': 'android.permission.FOREGROUND_SERVICE_DATA_SYNC' in manifest,
    'job_service_bound': 'android.permission.BIND_JOB_SERVICE' in manifest and '.launch.RomDownloadJobService' in manifest,
    'fgs_data_sync': 'android:foregroundServiceType="dataSync"' in manifest,
    'uidt_api34': '.setUserInitiated(true)' in scheduler and 'UPSIDE_DOWN_CAKE' in scheduler,
    'uidt_network_constraint': '.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)' in scheduler,
    'uidt_estimate_remaining': 'store.partialBytes(snapshot)' in scheduler and '.setEstimatedNetworkBytes(remainingBytes, 0L)' in scheduler,
    'uidt_minimum_chunk': '.setMinimumNetworkChunkBytes' in scheduler,
    'job_notification': 'setNotification(' in job and 'JOB_END_NOTIFICATION_POLICY_REMOVE' in job,
    'job_finished': 'jobFinished(params, false)' in job,
    'fgs_start_foreground': 'ServiceCompat.startForeground' in fgs,
    'fgs_type_runtime': 'FOREGROUND_SERVICE_TYPE_DATA_SYNC' in fgs,
    'fgs_timeout': 'override fun onTimeout(startId: Int, fgsType: Int)' in fgs,
    'progress_notification': 'setProgress(100' in notify and 'descarga en segundo plano' in notify,
    'play_notification_action': '"Jugar ahora"' in notify and 'autoLaunch = true' in notify and 'PendingIntent.getActivity' in notify,
    'notification_tap_does_not_force_launch': 'requestCode(snapshot, 3), autoLaunch = false' in notify,
    'delete_partial_notification_action': '"Borrar parcial"' in notify,
    'uidt_bound_network': 'params.network' in job and 'network?.openConnection(currentUrl)' in downloader,
    'range_resume': 'setRequestProperty("Range", "bytes=$resumeOffset-")' in downloader,
    'append_on_206_only': 'responseCode == HttpURLConnection.HTTP_PARTIAL' in downloader and 'contentRangeStart == resumeOffset' in downloader and 'FileOutputStream(target, canAppend)' in downloader,
    'partial_survives_failure': 'status = RomDownloadStatus.FAILED' in tasks and 'deleteStaging(current)' not in tasks[tasks.index('fun markFailed'):tasks.index('fun markCancelled')],
    'cancelled_partial_resumable': 'previous.status != RomDownloadStatus.CANCELLED || partialBytes(previous) > 0L' in tasks,
    'retry_directory_recreated': 'if (!directory.exists()) directory.mkdirs()' in session_store,
    'already_persisted_short_circuit': 'BACKGROUND_DOWNLOAD_ALREADY_PERSISTED' in read('app/src/main/java/cl/gakuto/kizuna/launch/BackgroundRomDownloadEngine.kt'),
    'activity_uses_background_scheduler': 'RomDownloadScheduler.schedule' in session and 'startOrAttachBackgroundDownload' in session,
    'partial_delete_ui': 'deletePartialButton' in session and 'Borrar descarga parcial' in session,
    'full_download_delete_safe': 'suspend fun deleteDownloaded' in persistent and 'LibraryOrigin.DOWNLOADED' in persistent,
    'imported_never_deleted_copy': 'Los archivos importados manualmente nunca se borran' in app,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('FIX68_BACKGROUND_DOWNLOAD_FAILED ' + ','.join(failed))
print(f'FIX68_BACKGROUND_DOWNLOAD_OK checks={len(checks)}')
