package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.databinding.ItemOnboardingPageBinding
import com.resq.app.databinding.ItemOnboardingProfileBinding

class OnboardingPagerAdapter(
    private val pages: List<OnboardingPage>,
    private val onLocationChanged: (LocationField, Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // ── Page model ────────────────────────────────────────────────────────────

    sealed class OnboardingPage {
        data class InfoPage(
            val title: String,
            val description: String,
            val imageRes: Int
        ) : OnboardingPage()

        data class LocationPage(
            val locationState: LocationState = LocationState()
        ) : OnboardingPage()
    }

    data class LocationState(
        val isNearWater: Boolean = false,
        val isMountainous: Boolean = false,
        val isLandslideProne: Boolean = false,
        val isFloodProne: Boolean = false,
        val isNearVolcano: Boolean = false,
        val isUrban: Boolean = false,
        val isCoastal: Boolean = false
    )

    enum class LocationField {
        NEAR_WATER, MOUNTAINOUS, LANDSLIDE, FLOOD, VOLCANO, URBAN, COASTAL
    }

    // ── View types ────────────────────────────────────────────────────────────

    companion object {
        private const val TYPE_INFO = 0
        private const val TYPE_LOCATION = 1
    }

    override fun getItemViewType(position: Int): Int =
        when (pages[position]) {
            is OnboardingPage.InfoPage -> TYPE_INFO
            is OnboardingPage.LocationPage -> TYPE_LOCATION
        }

    // ── ViewHolder creation ───────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_INFO -> InfoViewHolder(
                ItemOnboardingPageBinding.inflate(inflater, parent, false)
            )
            else -> LocationViewHolder(
                ItemOnboardingProfileBinding.inflate(inflater, parent, false),
                onLocationChanged
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val page = pages[position]) {
            is OnboardingPage.InfoPage -> (holder as InfoViewHolder).bind(page)
            is OnboardingPage.LocationPage -> (holder as LocationViewHolder).bind(page)
        }
    }

    override fun getItemCount(): Int = pages.size

    // ── Info ViewHolder (unchanged behaviour) ─────────────────────────────────

    class InfoViewHolder(
        private val binding: ItemOnboardingPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: OnboardingPage.InfoPage) {
            binding.tvTitle.text = page.title
            binding.tvDescription.text = page.description
            binding.ivPageImage.setImageResource(page.imageRes)
        }
    }

    // ── Location ViewHolder ───────────────────────────────────────────────────

    class LocationViewHolder(
        private val binding: ItemOnboardingProfileBinding,
        private val onLocationChanged: (LocationField, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        // Track whether we're in a programmatic bind so listeners don't fire
        private var isBinding = false

        init {
            binding.cbNearWater.setOnCheckedChangeListener { _, checked ->
                if (!isBinding) onLocationChanged(LocationField.NEAR_WATER, checked)
            }
            binding.cbMountainous.setOnCheckedChangeListener { _, checked ->
                if (!isBinding) onLocationChanged(LocationField.MOUNTAINOUS, checked)
            }
            binding.cbLandslide.setOnCheckedChangeListener { _, checked ->
                if (!isBinding) onLocationChanged(LocationField.LANDSLIDE, checked)
            }
            binding.cbFlood.setOnCheckedChangeListener { _, checked ->
                if (!isBinding) onLocationChanged(LocationField.FLOOD, checked)
            }
            binding.cbVolcano.setOnCheckedChangeListener { _, checked ->
                if (!isBinding) onLocationChanged(LocationField.VOLCANO, checked)
            }
            binding.cbUrban.setOnCheckedChangeListener { _, checked ->
                if (!isBinding) onLocationChanged(LocationField.URBAN, checked)
            }
            binding.cbCoastal.setOnCheckedChangeListener { _, checked ->
                if (!isBinding) onLocationChanged(LocationField.COASTAL, checked)
            }
        }

        fun bind(page: OnboardingPage.LocationPage) {
            isBinding = true
            binding.cbNearWater.isChecked = page.locationState.isNearWater
            binding.cbMountainous.isChecked = page.locationState.isMountainous
            binding.cbLandslide.isChecked = page.locationState.isLandslideProne
            binding.cbFlood.isChecked = page.locationState.isFloodProne
            binding.cbVolcano.isChecked = page.locationState.isNearVolcano
            binding.cbUrban.isChecked = page.locationState.isUrban
            binding.cbCoastal.isChecked = page.locationState.isCoastal
            isBinding = false
        }
    }
}