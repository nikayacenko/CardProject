package com.example.cardproject.ui.learning

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.cardproject.R
import com.example.cardproject.databinding.FragmentLearningBinding
import com.example.cardproject.model.Card
import com.example.cardproject.model.LearningMode
import com.example.cardproject.model.NavigationSource
import com.example.cardproject.ui.stats.SessionStatsFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LearningFragment : Fragment() {

    private var _binding: FragmentLearningBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LearningViewModel by viewModels()

    private var cardShowTime: Long = 0
    private var isFrontVisible = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLearningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val deckId = arguments?.getLong("deckId") ?: -1
        val mode = arguments?.getSerializable("learningMode") as? LearningMode ?: LearningMode.LONG_TERM

        viewModel.setDeckId(deckId, mode)

        // Перехват кнопки Назад
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            handleExitAttempt()
        }

        binding.toolbar.setNavigationOnClickListener { handleExitAttempt() }

        setupObservers()
        setupClickListeners()
        // Проверка статуса ML
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isMLReady.collect { isReady ->
                if (isReady) {
                    binding.mlStatusBadge.visibility = View.VISIBLE
                    binding.mlStatusBadge.text = "🤖 AI"
                } else {
                    binding.mlStatusBadge.visibility = View.GONE
                }
            }
        }
    }

    private fun handleExitAttempt() {
        if (viewModel.getAnsweredCount() > 0) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Завершить сессию?")
                .setMessage("Прогресс пройденных карточек сохранен, но статистика всей сессии не будет записана.")
                .setPositiveButton("Выйти") { _, _ ->
                    viewModel.abandonSession()
                    parentFragmentManager.popBackStack()
                }
                .setNegativeButton("Отмена", null)
                .show()
        } else {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentCard.collect { card ->
                card?.let {
                    showCard(it)
                    cardShowTime = SystemClock.elapsedRealtime()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isFinished.collect { finished ->
                if (finished) navigateToStats()
            }
        }

        // Дополнительно: индикатор AI и прогресс (согласно твоему дизайну)
    }

    private fun showCard(card: Card) {
        binding.cardFrontText.text = card.front
        binding.cardBackText.text = card.back
        binding.cardBackLayout.visibility = View.GONE
        binding.cardFrontLayout.visibility = View.VISIBLE
        binding.answerButtons.visibility = View.GONE
        isFrontVisible = true

        binding.cardCounter.text = "Карта: ${viewModel.getAnsweredCount() + 1}/${viewModel.getTotalCount()}"
    }

    private fun setupClickListeners() {
        binding.cardContainer.setOnClickListener {
            if (isFrontVisible) {
                isFrontVisible = false
                binding.cardFrontLayout.visibility = View.GONE
                binding.cardBackLayout.visibility = View.VISIBLE
                binding.answerButtons.visibility = View.VISIBLE
            }
        }

        binding.knowButton.setOnClickListener { submitAnswer(1) }
        binding.dontKnowButton.setOnClickListener { submitAnswer(0) }
    }

    private fun submitAnswer(quality: Int) {
        val card = viewModel.currentCard.value ?: return
        val time = SystemClock.elapsedRealtime() - cardShowTime
        viewModel.answerCard(card, quality, time)
    }

    private fun navigateToStats() {
        val stats = viewModel.getCurrentSessionStats()
        val fragment = SessionStatsFragment().apply {
            arguments = Bundle().apply {
                putParcelable("sessionStats", stats)
                putSerializable("navigationSource", NavigationSource.FROM_LEARNING)
            }
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}