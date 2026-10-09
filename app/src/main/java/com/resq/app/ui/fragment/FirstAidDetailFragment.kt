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
import androidx.recyclerview.widget.LinearLayoutManager
import com.resq.app.MainActivity
import com.resq.app.databinding.FragmentFirstAidDetailBinding
import com.resq.app.ui.adapter.FirstAidStepAdapter
import com.resq.app.ui.viewmodel.FirstAidViewModel
import com.resq.app.util.TextToSpeechManager
import com.resq.app.util.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FirstAidDetailFragment : Fragment() {

    private var _binding: FragmentFirstAidDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FirstAidViewModel by viewModels()
    private val args: FirstAidDetailFragmentArgs by navArgs()

    @Inject
    lateinit var ttsManager: TextToSpeechManager

    private lateinit var stepAdapter: FirstAidStepAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstAidDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        viewModel.loadCategory(args.categoryId)
        observeViewModel()
        setupVoiceListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setupWithNavController(this)
    }

    private fun setupRecyclerView() {
        stepAdapter = FirstAidStepAdapter()
        binding.rvSteps.adapter = stepAdapter
        if (binding.rvSteps.layoutManager == null) {
            binding.rvSteps.layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedCategory.collect { category ->
                        category ?: return@collect
                        binding.toolbar.title = category.name
                        binding.tvDescription.text      = category.description

                        val resId = requireContext().resources.getIdentifier(
                            category.imageResName,
                            "drawable",
                            requireContext().packageName
                        )
                        if (resId != 0) {
                            binding.ivHeader.setImageResource(resId)
                            binding.ivHeader.visibility = View.VISIBLE
                        } else {
                            binding.ivHeader.setImageDrawable(null)
                            binding.ivHeader.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.categorySteps.collect { steps ->
                        stepAdapter.submitList(steps)
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
        val category = viewModel.selectedCategory.value ?: return
        val steps = viewModel.categorySteps.value

        val stringBuilder = StringBuilder()
        stringBuilder.append(category.name).append(". ")
        stringBuilder.append(category.description).append(". ")
        stringBuilder.append("Here are the steps. ")

        steps.forEachIndexed { index, step ->
            stringBuilder.append("Step ${index + 1}: ").append(step.title).append(". ")
            stringBuilder.append(step.content).append(". ")

            val sectionLower = step.sectionTitle.lowercase()
            if (sectionLower.contains("warning") || sectionLower.contains("important")) {
                stringBuilder.append("Warning: ").append(step.sectionTitle).append(". ")
            }
        }

        ttsManager.speak(stringBuilder.toString())
    }

    override fun onDestroyView() {
        ttsManager.stop()
        super.onDestroyView()
        _binding = null
    }
}
