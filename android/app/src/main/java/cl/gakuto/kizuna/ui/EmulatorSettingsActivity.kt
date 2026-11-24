package cl.gakuto.kizuna.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cl.gakuto.kizuna.R
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.launch.EmulatorProfile
import cl.gakuto.kizuna.launch.EmulatorProfiles
import cl.gakuto.kizuna.launch.ExternalEmulatorManager
import com.google.android.material.button.MaterialButton

class EmulatorSettingsActivity : AppCompatActivity() {
    private lateinit var preferences: AppPreferences
    private lateinit var manager: ExternalEmulatorManager
    private lateinit var adapter: EmulatorSystemAdapter
    private var pendingTestProfile: EmulatorProfile? = null

    private val testFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val profile = pendingTestProfile.also { pendingTestProfile = null } ?: return@registerForActivityResult
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        testExternalLaunch(profile, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emulator_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.emulatorSystemsList)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom + 16)
            insets
        }
        preferences = AppPreferences(this)
        manager = ExternalEmulatorManager(this)
        adapter = EmulatorSystemAdapter(preferences, manager, EmulatorProfiles.all) { profile ->
            pendingTestProfile = profile
            testFilePicker.launch(profile.acceptedExtensions.map { extensionMime(it) }.distinct().toTypedArray())
        }
        findViewById<RecyclerView>(R.id.emulatorSystemsList).apply {
            layoutManager = LinearLayoutManager(this@EmulatorSettingsActivity)
            adapter = this@EmulatorSettingsActivity.adapter
            setHasFixedSize(false)
        }
        findViewById<MaterialButton>(R.id.detectEmulatorsButton).setOnClickListener { refreshDetection() }
        findViewById<MaterialButton>(R.id.restoreAllEnginesButton).setOnClickListener {
            preferences.restoreAllAutomaticEngines(EmulatorProfiles.all.map(EmulatorProfile::systemId))
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "Recomendaciones automáticas restauradas", Toast.LENGTH_SHORT).show()
        }
        refreshDetection()
    }

    private fun refreshDetection() {
        val installed = EmulatorProfiles.all
            .flatMap(EmulatorProfile::externalApps)
            .distinctBy { it.packageName }
            .count { manager.isInstalled(it.packageName) }
        findViewById<TextView>(R.id.emulatorSummaryText).text =
            "$installed emulador(es) externo(s) reconocido(s). Oz comprueba intents exportados antes de cada lanzamiento y nunca escribe en carpetas privadas."
        adapter.notifyDataSetChanged()
    }

    private fun testExternalLaunch(profile: EmulatorProfile, uri: Uri) {
        val app = manager.selectedOrRecommended(profile, preferences.externalEmulatorPackage(profile.systemId))
        if (app == null) {
            showInfo("Sin emulador externo", "${profile.systemName} no tiene una integración externa verificable. Usa RetroArch o configura otra aplicación cuando publique una API estable.")
            return
        }
        val displayName = queryName(uri)
        val extension = displayName.substringAfterLast('.', "").lowercase()
        if (extension !in app.acceptedExtensions) {
            showInfo(
                "Formato no declarado",
                "$displayName no pertenece a los formatos declarados para ${app.name}: ${app.acceptedExtensions.sorted().joinToString()}. No se intentará abrir.",
            )
            return
        }
        val available = manager.canLaunch(app, uri, displayName)
        AlertDialog.Builder(this)
            .setTitle("Prueba de lanzamiento")
            .setMessage(
                "Sistema: ${profile.systemName}\nAplicación: ${app.name}\nPaquete: ${app.packageName}\n" +
                    "URI: $uri\nFormato: .$extension\nBIOS: ${profile.bios}\n\n" +
                    if (available) "Android confirmó una actividad exportada compatible." else "La versión instalada no expone un intent compatible para este archivo.",
            )
            .setNegativeButton("Cerrar", null)
            .apply {
                if (available) setPositiveButton("Abrir prueba") { _, _ ->
                    if (!manager.launch(app, uri, displayName)) {
                        showInfo("No se pudo abrir", "Android dejó de resolver el intent. No se modificó ninguna carpeta del emulador.")
                    }
                } else if (manager.isInstalled(app.packageName)) {
                    setNeutralButton("Abrir aplicación") { _, _ -> manager.openApp(app) }
                }
            }
            .show()
    }

    private fun queryName(uri: Uri): String = contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "test.rom"

    private fun extensionMime(extension: String): String = when (extension) {
        "zip", "7z" -> "application/zip"
        else -> "application/octet-stream"
    }

    private fun showInfo(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }
}
