package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.databinding.ItemOnboardingAgreementBinding
import com.resq.app.databinding.ItemOnboardingPageBinding
import com.resq.app.databinding.ItemOnboardingProfileBinding

class OnboardingPagerAdapter(
    private val pages: List<OnboardingPage>,
    private val onLocationChanged: (LocationField, Boolean) -> Unit,
    private val onAgreementChanged: (AgreementField, Boolean) -> Unit
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

        data class AgreementPage(
            val agreementState: AgreementState = AgreementState()
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

    data class AgreementState(
        val termsAccepted: Boolean = false,
        val privacyAccepted: Boolean = false
    )

    enum class LocationField {
        NEAR_WATER, MOUNTAINOUS, LANDSLIDE, FLOOD, VOLCANO, URBAN, COASTAL
    }

    enum class AgreementField {
        TERMS, PRIVACY
    }

    // ── View types ────────────────────────────────────────────────────────────

    companion object {
        private const val TYPE_INFO = 0
        private const val TYPE_LOCATION = 1
        private const val TYPE_AGREEMENT = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (pages[position]) {
            is OnboardingPage.InfoPage -> TYPE_INFO
            is OnboardingPage.LocationPage -> TYPE_LOCATION
            is OnboardingPage.AgreementPage -> TYPE_AGREEMENT
        }
    }

    // ── ViewHolder creation ───────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_INFO -> InfoViewHolder(
                ItemOnboardingPageBinding.inflate(inflater, parent, false)
            )
            TYPE_LOCATION -> LocationViewHolder(
                ItemOnboardingProfileBinding.inflate(inflater, parent, false),
                onLocationChanged
            )
            TYPE_AGREEMENT -> AgreementViewHolder(
                ItemOnboardingAgreementBinding.inflate(inflater, parent, false),
                onAgreementChanged
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val page = pages[position]) {
            is OnboardingPage.InfoPage -> (holder as InfoViewHolder).bind(page)
            is OnboardingPage.LocationPage -> (holder as LocationViewHolder).bind(page)
            is OnboardingPage.AgreementPage -> (holder as AgreementViewHolder).bind(page)
        }
    }

    override fun getItemCount(): Int = pages.size

    // ── Info ViewHolder ─────────────────────────────────────────────────────

    class InfoViewHolder(
        private val binding: ItemOnboardingPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: OnboardingPage.InfoPage) {
            binding.tvTitle.text = page.title
            binding.tvDescription.text = page.description
            binding.ivPageImage.setImageResource(page.imageRes)
        }
    }

    // ── Location ViewHolder ─────────────────────────────────────────────────

    class LocationViewHolder(
        private val binding: ItemOnboardingProfileBinding,
        private val onLocationChanged: (LocationField, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

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

    // ── Agreement ViewHolder ───────────────────────────────────────────────

    class AgreementViewHolder(
        val binding: ItemOnboardingAgreementBinding,
        private val onAgreementChanged: (AgreementField, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var isBinding = false

        init {
            // Checkbox listeners
            binding.cbTermsAccepted.setOnCheckedChangeListener { _, checked ->
                if (!isBinding) onAgreementChanged(AgreementField.TERMS, checked)
            }
            binding.cbPrivacyAccepted.setOnCheckedChangeListener { _, checked ->
                if (!isBinding) onAgreementChanged(AgreementField.PRIVACY, checked)
            }
        }

        fun bind(page: OnboardingPage.AgreementPage) {
            isBinding = true
            binding.cbTermsAccepted.isChecked = page.agreementState.termsAccepted
            binding.cbPrivacyAccepted.isChecked = page.agreementState.privacyAccepted
            isBinding = false
        }
    }
}