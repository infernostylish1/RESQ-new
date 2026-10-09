package com.resq.app.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.resq.app.R
import com.resq.app.databinding.BottomSheetHazardInfoBinding
import com.resq.app.map.HazardType

class HazardInfoBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetHazardInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetHazardInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val type = arguments?.getString(ARG_TYPE) ?: return
        val level = arguments?.getInt(ARG_LEVEL, 0) ?: 0
        val ht = HazardType.valueOf(type)

        binding.tvHazardType.text = getString(typeLabel(ht))
        binding.tvHazardLevel.text = getString(levelLabel(level))
        binding.tvHazardDetail.text = getString(detailFor(ht, level))
    }

    private fun typeLabel(t: HazardType) = when (t) {
        HazardType.FLOOD        -> R.string.map_layer_flood_100yr
        HazardType.LANDSLIDE    -> R.string.map_layer_landslide
        HazardType.DEBRIS_FLOW  -> R.string.map_layer_debris_flow
        HazardType.STORM_SURGE  -> R.string.map_layer_ssa1
    }

    private fun levelLabel(level: Int) = when (level) {
        1 -> R.string.map_legend_low
        2 -> R.string.map_legend_medium
        else -> R.string.map_legend_high
    }

    private fun detailFor(t: HazardType, level: Int) = when (t) {
        HazardType.FLOOD -> when (level) {
            1 -> R.string.map_detail_flood_low
            2 -> R.string.map_detail_flood_med
            else -> R.string.map_detail_flood_high
        }
        HazardType.LANDSLIDE -> when (level) {
            1 -> R.string.map_detail_landslide_low
            2 -> R.string.map_detail_landslide_med
            else -> R.string.map_detail_landslide_high
        }
        HazardType.DEBRIS_FLOW -> R.string.map_detail_debris
        HazardType.STORM_SURGE -> when (level) {
            1 -> R.string.map_detail_ssa_low
            2 -> R.string.map_detail_ssa_med
            else -> R.string.map_detail_ssa_high
        }
    }

    override fun onDestroyView() {
        super.onDestroyView(); _binding = null
    }

    companion object {
        const val TAG = "HazardInfoBottomSheet"
        private const val ARG_TYPE = "type"
        private const val ARG_LEVEL = "level"
        private const val ARG_LAYER = "layer"

        fun newInstance(hazardType: String, level: Int, layerId: String) =
            HazardInfoBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TYPE, hazardType)
                    putInt(ARG_LEVEL, level)
                    putString(ARG_LAYER, layerId)
                }
            }
    }
}