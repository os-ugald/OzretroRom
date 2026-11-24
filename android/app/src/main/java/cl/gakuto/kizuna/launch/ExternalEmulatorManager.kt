package cl.gakuto.kizuna.launch

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

class ExternalEmulatorManager(private val context: Context) {
    data class Detection(
        val app: ExternalEmulatorApp,
        val installed: Boolean,
        val directLaunchAvailable: Boolean,
        val label: String,
    )

    fun detect(profile: EmulatorProfile, testUri: Uri? = null, displayName: String = "test.rom"): List<Detection> =
        profile.externalApps.map { app ->
            val installed = isInstalled(app.packageName)
            val direct = installed && when (app.launchKind) {
                ExternalLaunchKind.APP_ONLY -> false
                ExternalLaunchKind.VERIFIED_EXPLICIT_CONTENT -> {
                    val uri = testUri ?: Uri.parse("content://${context.packageName}.files/probe/${displayName}")
                    resolve(buildIntent(app, uri, displayName))
                }
                ExternalLaunchKind.RUNTIME_RESOLVED_VIEW -> {
                    val uri = testUri ?: Uri.parse("content://${context.packageName}.files/probe/${displayName}")
                    resolve(buildIntent(app, uri, displayName))
                }
            }
            val appLabel = if (installed) applicationLabel(app.packageName) ?: app.name else app.name
            Detection(app, installed, direct, appLabel)
        }

    fun isInstalled(packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    }.getOrDefault(false)

    fun canLaunch(app: ExternalEmulatorApp, uri: Uri, displayName: String): Boolean =
        isInstalled(app.packageName) && app.launchKind != ExternalLaunchKind.APP_ONLY && resolve(buildIntent(app, uri, displayName))

    fun launch(app: ExternalEmulatorApp, uri: Uri, displayName: String): Boolean {
        if (!canLaunch(app, uri, displayName)) return false
        return runCatching {
            context.startActivity(buildIntent(app, uri, displayName))
            true
        }.getOrDefault(false)
    }

    fun openApp(app: ExternalEmulatorApp): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    fun selectedOrRecommended(profile: EmulatorProfile, selectedPackage: String?): ExternalEmulatorApp? {
        val selected = selectedPackage?.let { packageName ->
            profile.externalApps.firstOrNull { it.packageName == packageName }
        }
        return selected?.takeIf { isInstalled(it.packageName) }
            ?: profile.externalApps.firstOrNull { isInstalled(it.packageName) }
            ?: selected
            ?: profile.externalApps.firstOrNull()
    }

    fun buildIntent(app: ExternalEmulatorApp, uri: Uri, displayName: String): Intent {
        require(uri.scheme.equals("content", true)) {
            "Los emuladores externos reciben únicamente content URI con permiso temporal de lectura."
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = uri
            type = mimeType(displayName)
            setPackage(app.packageName)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            clipData = ClipData.newRawUri("Oz RetroRom", uri)
        }
        app.activityName?.let { intent.component = ComponentName(app.packageName, it) }
        return intent
    }

    private fun resolve(intent: Intent): Boolean = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        info?.activityInfo?.exported == true
    }.getOrDefault(false)

    private fun applicationLabel(packageName: String): String? = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0)
        }
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrNull()

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "zip", "7z" -> "application/zip"
        "iso", "cso", "chd", "rvz", "wbfs", "gdi", "cue", "bin" -> "application/octet-stream"
        else -> "application/octet-stream"
    }
}
