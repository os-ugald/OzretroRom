package cl.gakuto.kizuna.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cl.gakuto.kizuna.R
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.EngineMode
import cl.gakuto.kizuna.launch.EmulatorProfile
import cl.gakuto.kizuna.launch.ExternalEmulatorManager
import cl.gakuto.kizuna.launch.ExternalLaunchKind
import com.google.android.material.button.MaterialButton

class EmulatorSystemAdapter(
    private val preferences: AppPreferences,
    private val externalManager: ExternalEmulatorManager,
    private val profiles: List<EmulatorProfile>,
    private val onTest: (EmulatorProfile) -> Unit,
) : RecyclerView.Adapter<EmulatorSystemAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_emulator_system, parent, false),
    )

    override fun getItemCount(): Int = profiles.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(profiles[position])

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.systemNameText)
        private val recommended: TextView = view.findViewById(R.id.recommendedEngineText)
        private val modeSpinner: Spinner = view.findViewById(R.id.engineModeSpinner)
        private val appSpinner: Spinner = view.findViewById(R.id.externalAppSpinner)
        private val details: TextView = view.findViewById(R.id.emulatorDetailsText)
        private val bios: TextView = view.findViewById(R.id.biosWarningText)
        private val test: MaterialButton = view.findViewById(R.id.testLaunchButton)
        private val reset: MaterialButton = view.findViewById(R.id.resetEngineButton)
        private var binding = false

        fun bind(profile: EmulatorProfile) {
            binding = true
            name.text = profile.systemName
            recommended.text = "Recomendación: ${profile.recommendedEngine.label} · core ${profile.retroArchCore}"
            modeSpinner.adapter = ArrayAdapter(
                itemView.context,
                android.R.layout.simple_spinner_dropdown_item,
                EngineMode.entries.map(EngineMode::label),
            )
            modeSpinner.setSelection(EngineMode.entries.indexOf(preferences.engineMode(profile.systemId)).coerceAtLeast(0))

            val detections = externalManager.detect(profile)
            val appLabels = if (detections.isEmpty()) {
                listOf("Sin integración externa verificada")
            } else detections.map { detection ->
                val state = when {
                    !detection.installed -> "no instalado"
                    detection.directLaunchAvailable -> "instalado · lanzamiento disponible"
                    detection.app.launchKind == ExternalLaunchKind.APP_ONLY -> "instalado · abrir/configurar manualmente"
                    else -> "instalado · intent no disponible"
                }
                "${detection.label} ($state)"
            }
            appSpinner.adapter = ArrayAdapter(
                itemView.context,
                android.R.layout.simple_spinner_dropdown_item,
                appLabels,
            )
            val selectedPackage = preferences.externalEmulatorPackage(profile.systemId)
            val selectedIndex = detections.indexOfFirst { it.app.packageName == selectedPackage }.takeIf { it >= 0 } ?: 0
            if (detections.isNotEmpty()) appSpinner.setSelection(selectedIndex)
            appSpinner.isEnabled = detections.isNotEmpty()

            details.text = buildString {
                append(profile.rationale)
                append("\nFormatos: ")
                append(profile.acceptedExtensions.sorted().joinToString(", ") { ".$it" })
                append("\nAlternativa: ")
                append(profile.secondary)
                detections.getOrNull(selectedIndex)?.app?.let { app ->
                    append("\n")
                    append(app.officialStatus)
                }
            }
            bios.text = "BIOS: ${profile.bios}"
            test.isEnabled = detections.isNotEmpty()

            modeSpinner.onItemSelectedListener = SimpleItemSelectedListener {
                if (!binding) {
                    val mode = EngineMode.entries.getOrElse(modeSpinner.selectedItemPosition) { EngineMode.AUTO }
                    preferences.setEngineMode(profile.systemId, mode)
                }
            }
            appSpinner.onItemSelectedListener = SimpleItemSelectedListener {
                if (!binding && detections.isNotEmpty()) {
                    preferences.setExternalEmulatorPackage(
                        profile.systemId,
                        detections.getOrNull(appSpinner.selectedItemPosition)?.app?.packageName,
                    )
                }
            }
            test.setOnClickListener {
                if (detections.isNotEmpty()) {
                    preferences.setExternalEmulatorPackage(
                        profile.systemId,
                        detections.getOrNull(appSpinner.selectedItemPosition)?.app?.packageName,
                    )
                }
                onTest(profile)
            }
            reset.setOnClickListener {
                preferences.restoreAutomaticEngine(profile.systemId)
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) notifyItemChanged(position)
            }
            binding = false
        }
    }
}
