package com.resq.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.resq.app.databinding.FragmentTipDetailBinding
import com.resq.app.ui.adapter.TipDetailAdapter
import com.resq.app.ui.viewmodel.DisasterTipsViewModel
import com.resq.app.util.TextToSpeechManager
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
        setupFab()
        viewModel.loadDisasterDetail(args.disasterId)
        viewModel.loadTipsForDisaster(args.disasterId)
        observeViewModel()
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        // Set a fallback title immediately from args in case disasterDetail is slow
        binding.collapsingToolbar.title = args.disasterName
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        tipDetailAdapter = TipDetailAdapter()
        binding.rvTips.adapter = tipDetailAdapter
    }

    // ── FAB read aloud ────────────────────────────────────────────────────────

    private fun setupFab() {
        binding.fabReadAloud.setOnClickListener {
            readCurrentContentAloud()
        }
    }

    private fun readCurrentContentAloud() {
        val tips = viewModel.tipsForDisaster.value
        val languageCode = viewModel.uiState.value.languageCode
        if (tips.isEmpty()) return

        val sb = StringBuilder()
        sb.append(args.disasterName)
        sb.append(". ")
        tips.forEachIndexed { index, tip ->
            val text = if (languageCode == "tl") tip.tipTextTagalog else tip.tipText
            sb.append("Tip ${index + 1}: $text. ")
        }
        ttsManager.speak(sb.toString())
    }

    // ── Observation ───────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.disasterDetail.collect { disaster ->
                        disaster?.let {
                            val languageCode = viewModel.uiState.value.languageCode
                            binding.collapsingToolbar.title =
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

    // ── Header image ──────────────────────────────────────────────────────────

    private fun loadHeaderImage(imageResName: String) {
        val context = requireContext()
        val resId = context.resources.getIdentifier(
            imageResName, "drawable", context.packageName
        )
        if (resId != 0) {
            binding.ivHeader.setImageResource(resId)
        }
    }

    override fun onDestroyView() {
        ttsManager.stop()
        super.onDestroyView()
        _binding = null
    }
}