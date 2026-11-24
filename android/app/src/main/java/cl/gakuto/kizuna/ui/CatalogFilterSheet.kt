package cl.gakuto.kizuna.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import cl.gakuto.kizuna.catalog.CatalogSystem
import cl.gakuto.kizuna.databinding.SheetCatalogFiltersBinding
import cl.gakuto.kizuna.util.CatalogFilterState
import cl.gakuto.kizuna.util.CatalogFilters
import cl.gakuto.kizuna.util.CatalogSort
import cl.gakuto.kizuna.util.OriginStatusFilter
import cl.gakuto.kizuna.util.PlatformFamily
import cl.gakuto.kizuna.util.RegionMode
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

object CatalogFilterSheet {
    enum class Mode { FULL, SOURCE_INDEX }

    fun show(
        context: Context,
        initial: CatalogFilterState,
        systems: List<CatalogSystem>,
        mode: Mode = Mode.FULL,
        onApply: (CatalogFilterState) -> Unit,
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = SheetCatalogFiltersBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        val regionChips = RegionMode.entries.associateWith { mode ->
            createChip(context, mode.label, mode in initial.regions).also(binding.regionGroup::addView)
        }
        val genreChips = if (mode == Mode.FULL) CatalogFilters.requestedGenreChoices().associate { choice ->
            val key = requireNotNull(choice.key)
            key to createChip(context, choice.label, key in initial.genres).also(binding.genreGroup::addView)
        } else emptyMap()
        val statusChips = if (mode == Mode.FULL) OriginStatusFilter.entries.associateWith { status ->
            createChip(context, status.label, status in initial.status).also(binding.statusGroup::addView)
        } else emptyMap()

        val familyChips = linkedMapOf<PlatformFamily, Chip>()
        val systemChips = linkedMapOf<String, Chip>()
        val grouped = groupSystems(systems)
        if (mode == Mode.FULL) PlatformFamily.entries.forEach { family ->
            val familySystems = grouped.getValue(family)
            if (familySystems.isEmpty() && family != PlatformFamily.OTHER) return@forEach
            val title = TextView(context).apply {
                text = family.label
                textSize = 15f
                setPadding(0, 12, 0, 4)
            }
            binding.platformContainer.addView(title)
            val group = ChipGroup(context).apply {
                isSingleSelection = false
                chipSpacingHorizontal = 6
                chipSpacingVertical = 6
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            val familyChip = createChip(context, "Toda la familia", family in initial.platformFamilies)
            familyChips[family] = familyChip
            group.addView(familyChip)
            familySystems.forEach { system ->
                val chip = createChip(context, system.name, system.id in initial.systemIds)
                systemChips[system.id] = chip
                group.addView(chip)
            }
            familyChip.setOnCheckedChangeListener { _, checked ->
                if (checked) familySystems.forEach { systemChips[it.id]?.isChecked = false }
            }
            familySystems.forEach { system ->
                systemChips[system.id]?.setOnCheckedChangeListener { _, checked ->
                    if (checked) familyChip.isChecked = false
                }
            }
            binding.platformContainer.addView(group)
        }

        val availableSorts = if (mode == Mode.SOURCE_INDEX) {
            listOf(CatalogSort.NAME_ASC, CatalogSort.NAME_DESC)
        } else CatalogSort.entries
        val sortButtons = availableSorts.associateWith { sort ->
            RadioButton(context).apply {
                text = sort.label
                id = android.view.View.generateViewId()
                isChecked = sort == initial.sort
                binding.sortGroup.addView(this)
            }
        }

        if (mode == Mode.SOURCE_INDEX) {
            binding.platformTitle.visibility = View.GONE
            binding.platformContainer.visibility = View.GONE
            binding.genreTitle.visibility = View.GONE
            binding.genreGroup.visibility = View.GONE
            binding.statusTitle.visibility = View.GONE
            binding.statusGroup.visibility = View.GONE
        }

        fun clearAll() {
            regionChips.values.forEach { it.isChecked = false }
            regionChips.getValue(RegionMode.PREFERRED).isChecked = true
            genreChips.values.forEach { it.isChecked = false }
            statusChips.values.forEach { it.isChecked = false }
            familyChips.values.forEach { it.isChecked = false }
            systemChips.values.forEach { it.isChecked = false }
            sortButtons.getValue(CatalogSort.NAME_ASC).isChecked = true
        }

        binding.clearButton.setOnClickListener { clearAll() }
        binding.cancelButton.setOnClickListener { dialog.dismiss() }
        binding.applyButton.setOnClickListener {
            val state = CatalogFilterState(
                regions = regionChips.filterValues { it.isChecked }.keys,
                platformFamilies = familyChips.filterValues { it.isChecked }.keys,
                systemIds = systemChips.filterValues { it.isChecked }.keys,
                genres = genreChips.filterValues { it.isChecked }.keys,
                status = statusChips.filterValues { it.isChecked }.keys,
                sort = sortButtons.entries.firstOrNull { it.value.isChecked }?.key ?: CatalogSort.NAME_ASC,
            ).normalized()
            onApply(state)
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            val sheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let { target ->
                target.layoutParams = target.layoutParams.apply {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
                BottomSheetBehavior.from(target).state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
    }

    private fun createChip(context: Context, label: String, checked: Boolean): Chip = Chip(context).apply {
        text = label
        isCheckable = true
        isChecked = checked
        isClickable = true
    }

    private fun groupSystems(systems: List<CatalogSystem>): Map<PlatformFamily, List<CatalogSystem>> {
        val assigned = mutableSetOf<String>()
        val result = linkedMapOf<PlatformFamily, List<CatalogSystem>>()
        PlatformFamily.entries.filter { it != PlatformFamily.OTHER }.forEach { family ->
            val values = systems.filter { it.id in family.systemIds }.sortedBy { it.name }
            assigned += values.map { it.id }
            result[family] = values
        }
        result[PlatformFamily.OTHER] = systems.filterNot { it.id in assigned }.sortedBy { it.name }
        return result
    }
}
