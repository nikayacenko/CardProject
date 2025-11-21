package com.example.cardproject.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.cardproject.databinding.FragmentDeckStatsBinding
import com.example.cardproject.model.DeckStats
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class DeckStatsFragment : Fragment() {

    private var _binding: FragmentDeckStatsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeckStatsViewModel by viewModels()

    private var deckId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deckId = arguments?.getLong("deckId") ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeckStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupObservers()
        viewModel.loadDeckStats(deckId)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.deckStats.collectLatest { stats ->
                stats?.let { displayStats(it) }
            }
        }
    }

    private fun displayStats(stats: DeckStats) {
        binding.toolbar.title = "Статистика: ${stats.deckName}"

        // Основная статистика
        binding.totalCardsText.text = stats.totalCards.toString()
        binding.learnedCardsText.text = "${stats.learnedCards} (${stats.learnedPercentage.toInt()}%)"
        binding.inProgressCardsText.text = "${stats.inProgressCards} (${stats.inProgressPercentage.toInt()}%)"
        binding.newCardsText.text = "${stats.newCards} (${stats.newCardsPercentage.toInt()}%)"

        // Сессии
        binding.totalSessionsText.text = stats.totalSessions.toString()
        binding.totalStudyTimeText.text = stats.formattedTotalTime
        binding.averageAccuracyText.text = "%.1f%%".format(stats.averageAccuracy)

        // Прогресс-бары
        binding.learnedProgress.progress = stats.learnedPercentage.toInt()
        binding.inProgressProgress.progress = stats.inProgressPercentage.toInt()
        binding.newCardsProgress.progress = stats.newCardsPercentage.toInt()

        // Визуализация прогресса
        updateProgressVisual(stats)
    }

    private fun updateProgressVisual(stats: DeckStats) {
        // Цвета в зависимости от прогресса
        when {
            stats.learnedPercentage >= 80 -> {
                binding.learnedCardsText.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
                binding.progressComment.text = "Отличный прогресс!"
            }
            stats.learnedPercentage >= 50 -> {
                binding.learnedCardsText.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
                binding.progressComment.text = "Хорошие результаты!"
            }
            else -> {
                binding.learnedCardsText.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
                binding.progressComment.text = "Продолжайте обучение!"
            }
        }

        // Общий прогресс изучения
        val overallProgress = (stats.learnedCards + stats.inProgressCards * 0.5) / stats.totalCards * 100
        binding.overallProgress.progress = overallProgress.toInt()
        binding.overallProgressText.text = "Общий прогресс: ${overallProgress.toInt()}%"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}