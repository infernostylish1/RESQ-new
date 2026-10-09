package com.resq.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.resq.app.MainActivity
import com.resq.app.databinding.FragmentTipDetailBinding
import com.resq.app.ui.adapter.TipDetailAdapter
import com.resq.app.ui.viewmodel.DisasterTipsViewModel
import com.resq.app.util.TextToSpeechManager
import com.resq.app.util.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TipDetailFragment : Fragment() {

    private var _binding: FragmentTipDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DisasterTipsViewModel by viewModels()
    private val args: TipDetailFragmentArgs by navArgs()

    @Inject
    lateinit var ttsManager: TextToSpeechManager

    private lateinit var tipDetailAdapter: TipDetailAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTipDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        viewModel.loadDisasterDetail(args.disasterId)
        viewModel.loadTipsForDisaster(args.disasterId)
        observeViewModel()
        setupVoiceListeners()
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.toolbar.setupWithNavController(this)
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        tipDetailAdapter = TipDetailAdapter()
        binding.rvTips.adapter = tipDetailAdapter
    }

    // ── Observation ───────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.disasterDetail.collect { disaster ->
                        disaster?.let {
                            val languageCode = viewModel.uiState.value.languageCode
                            binding.toolbar.title =
                                if (languageCode == "tl") it.nameTagalog else it.name
                            binding.tvDescription.text =
                                if (languageCode == "tl") it.descriptionTagalog else it.description
                            loadHeaderImage(it.imageResName)
                        }
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        tipDetailAdapter.updateLanguage(state.languageCode)
                    }
                }

                launch {
                    viewModel.tipsForDisaster.collect { tips ->
                        tipDetailAdapter.submitList(tips)
                    }
                }
            }
        }
    }

    private fun setupVoiceListeners() {
        setFragmentResultListener(MainActivity.REQUEST_SCROLL) { _, bundle ->
            when (bundle.getString(MainActivity.KEY_SCROLL)) {
                MainActivity.SCROLL_UP   -> binding.nestedScrollView.smoothScrollTo(0, 0)
                MainActivity.SCROLL_DOWN -> binding.nestedScrollView.smoothScrollTo(
                    0, binding.nestedScrollView.getChildAt(0).height
                )
            }
        }

        setFragmentResultListener(MainActivity.REQUEST_TTS) { _, bundle ->
            when (bundle.getString(MainActivity.KEY_TTS)) {
                MainActivity.TTS_READ -> readContentAloud()
                MainActivity.TTS_STOP -> ttsManager.stop()
            }
        }
    }

    private fun readContentAloud() {
        val disaster = viewModel.disasterDetail.value ?: return
        val tips = viewModel.tipsForDisaster.value
        val languageCode = viewModel.uiState.value.languageCode
        val isTagalog = languageCode == "tl" || languageCode == "fil"

        val stringBuilder = StringBuilder()
        stringBuilder.append(if (isTagalog) disaster.nameTagalog else disaster.name).append(". ")
        stringBuilder.append(if (isTagalog) disaster.descriptionTagalog else disaster.description).append(". ")
        stringBuilder.append(if (isTagalog) "Narito ang mga tip para sa kaligtasan. " else "Here are the safety tips. ")

        tips.forEachIndexed { index, tip ->
            stringBuilder.append(if (isTagalog) "Tip bilang ${index + 1}: " else "Tip number ${index + 1}: ")
            stringBuilder.append(if (isTagalog) tip.tipTextTagalog else tip.tipText).append(". ")
        }

        ttsManager.speak(stringBuilder.toString())
    }

    // ── Header image ──────────────────────────────────────────────────────────

    private fun loadHeaderImage(imageResName: String) {
        val context = requireContext()
        val resId = context.resources.getIdentifier(
            imageResName, "drawable", context.packageName
        )
        if (resId != 0) {
            binding.ivHeader.setImageResource(resId)
            binding.ivHeader.visibility = View.VISIBLE
        } else {
            binding.ivHeader.setImageDrawable(null)
            binding.ivHeader.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        ttsManager.stop()
        super.onDestroyView()
        _binding = null
    }
}
