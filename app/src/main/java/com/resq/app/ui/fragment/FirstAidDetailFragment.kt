package com.resq.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.resq.app.R
import com.resq.app.databinding.FragmentFirstAidDetailBinding
import com.resq.app.data.entity.FirstAidContentEntity
import com.resq.app.ui.viewmodel.FirstAidViewModel
import com.resq.app.util.TextToSpeechManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * First Aid detail fragment — step-by-step pager view.
 *
 * Fixes applied:
 * - Complete rewrite to match fragment_first_aid_detail.xml structure:
 *   Layout has: tv_step_indicator, progress_steps, tv_step_title,
 *   tv_step_description, card_warning, tv_warning,
 *   navigation_container with btn_previous / btn_read_aloud / btn_next
 * - No RecyclerView in this layout — uses manual step index navigation
 * - No FAB in this layout — read aloud is btn_read_aloud in navigation_container
 * - No tvCategoryDescription in layout — removed
 * - categoryId argument is now Long (nav_graph updated)
 */
@AndroidEntryPoint
class FirstAidDetailFragment : Fragment() {

    private var _binding: FragmentFirstAidDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FirstAidViewModel by viewModels()
    private val args: FirstAidDetailFragmentArgs by navArgs()

    @Inject
    lateinit var ttsManager: TextToSpeechManager

    private var steps: List<FirstAidContentEntity> = emptyList()
    private var currentStepIndex: Int = 0

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
        setupNavigationButtons()
        viewModel.loadCategory(args.categoryId)
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.title = args.categoryName
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupNavigationButtons() {
        binding.btnPrevious.setOnClickListener {
            if (currentStepIndex > 0) {
                currentStepIndex--
                displayCurrentStep()
            }
        }

        binding.btnNext.setOnClickListener {
            if (currentStepIndex < steps.lastIndex) {
                currentStepIndex++
                displayCurrentStep()
            }
        }

        // btn_read_aloud is inside navigation_container (not a FAB)
        binding.btnReadAloud.setOnClickListener {
            readCurrentStepAloud()
        }
    }

    private fun displayCurrentStep() {
        if (steps.isEmpty()) return

        val step      = steps[currentStepIndex]
        val total     = steps.size
        val stepNum   = currentStepIndex + 1

        // tv_step_indicator: e.g. "Step 1 of 5"
        binding.tvStepIndicator.text =
            getString(R.string.firstaid_step, stepNum, total)

        // progress_steps: LinearProgressIndicator
        binding.progressSteps.max      = total
        binding.progressSteps.progress = stepNum

        // tv_step_title + tv_step_description
        binding.tvStepTitle.text       = step.title
        binding.tvStepDescription.text = step.content

        // card_warning — show only when sectionTitle contains "warning" or "important"
        val hasWarning = step.sectionTitle
            .lowercase()
            .let { it.contains("warning") || it.contains("important") }

        binding.cardWarning.isVisible = hasWarning
        if (hasWarning) {
            binding.tvWarning.text = step.sectionTitle
        }

        // Button states
        binding.btnPrevious.isEnabled = currentStepIndex > 0
        binding.btnNext.isEnabled     = currentStepIndex < steps.lastIndex
    }

    private fun readCurrentStepAloud() {
        if (steps.isEmpty()) return
        val step = steps[currentStepIndex]
        val text = "Step ${currentStepIndex + 1} of ${steps.size}. " +
                "${step.title}. ${step.content}"
        ttsManager.speak(text)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.selectedCategory.collect { category ->
                        category?.let {
                            binding.toolbar.title = it.name
                        }
                    }
                }

                launch {
                    viewModel.categorySteps.collect { contentList ->
                        steps             = contentList
                        currentStepIndex  = 0
                        displayCurrentStep()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        ttsManager.stop()
        super.onDestroyView()
        _binding = null
    }
}