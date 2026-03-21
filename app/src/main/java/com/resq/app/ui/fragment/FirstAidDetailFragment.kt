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
import androidx.recyclerview.widget.LinearLayoutManager
import com.resq.app.databinding.FragmentFirstAidDetailBinding
import com.resq.app.ui.adapter.FirstAidStepAdapter
import com.resq.app.ui.viewmodel.FirstAidViewModel
import com.resq.app.util.TextToSpeechManager
import com.resq.app.util.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * First Aid detail fragment — scrollable list of all steps for a category.
 *
 * Layout: fragment_first_aid_detail.xml
 *   CoordinatorLayout
 *     AppBarLayout → CollapsingToolbarLayout (iv_header, scrim, toolbar)
 *     NestedScrollView
 *       tv_description   — category description
 *       rv_steps         — all steps via FirstAidStepAdapter
 *     fab_read_aloud     — reads all steps aloud via TTS
 *
 * No step-pager logic. No prev/next buttons. Mirrors DisasterDetailFragment.
 */
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
    }

    private fun setupToolbar() {
        binding.toolbar.setupWithNavController(this)
    }

    private fun setupRecyclerView() {
        stepAdapter = FirstAidStepAdapter()
        binding.rvSteps.adapter = stepAdapter
        // Defensive: XML already sets LinearLayoutManager via app:layoutManager,
        // but this guard ensures it is never null if that attribute is ever
        // removed or overridden during a layout refactor.
        if (binding.rvSteps.layoutManager == null) {
            binding.rvSteps.layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Update collapsing toolbar title, description, and header image
                // once the category entity arrives from the DB.
                launch {
                    viewModel.selectedCategory.collect { category ->
                        category ?: return@collect

                        binding.collapsingToolbar.title = category.name
                        binding.tvDescription.text      = category.description

                        // Resolve imageResName to a drawable resource ID.
                        // Falls back gracefully — iv_header just shows the scrim
                        // colour if no matching drawable exists yet.
                        val resId = requireContext().resources.getIdentifier(
                            category.imageResName,
                            "drawable",
                            requireContext().packageName
                        )
                        if (resId != 0) {
                            binding.ivHeader.setImageResource(resId)
                        }
                    }
                }

                // Submit the full step list to the adapter whenever it changes.
                launch {
                    viewModel.categorySteps.collect { steps ->
                        stepAdapter.submitList(steps)
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