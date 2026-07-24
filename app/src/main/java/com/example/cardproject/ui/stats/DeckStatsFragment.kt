package com.example.cardproject.ui.stats

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.cardproject.R
import com.example.cardproject.databinding.FragmentDeckStatsBinding
import com.example.cardproject.model.DeckStats
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import com.example.cardproject.model.DeckPrediction
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
                stats?.let {
                    displayStats(it)
                    Log.d("DeckStats", "📊 Stats: $it")
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.prediction.collectLatest { prediction ->
                Log.d("DeckStats", "🔮 Prediction: $prediction")
                prediction?.let {
                    displayPrediction(it)
                    displayMilestones(it.milestones)
                }
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

    // DeckStatsFragment.kt - добавить метод
    private fun displayPrediction(prediction: DeckPrediction) {
        with(binding) {
            predictionCard.visibility = View.VISIBLE

            dailyRateText.text = if (prediction.dailyLearningRate > 0) {
                String.format("%.1f карточек/день", prediction.dailyLearningRate)
            } else {
                "🌱 Ещё нет данных"
            }

            remainingCardsText.text = prediction.remainingCards.toString()

            completionDateText.text = when {
                prediction.predictedDaysToComplete > 0 -> prediction.predictedCompletionDate
                prediction.learnedCards >= prediction.totalCards -> "🎉 Завершено!"
                prediction.dailyLearningRate <= 0 && prediction.totalCards > 0 -> "📚 Начните учить карточки"
                else -> "Нет данных"
            }

            daysToCompleteText.text = when {
                prediction.predictedDaysToComplete > 0 -> "${prediction.predictedDaysToComplete} дней"
                prediction.learnedCards >= prediction.totalCards -> "0 дней"
                prediction.dailyLearningRate <= 0 && prediction.totalCards > 0 -> "Начните обучение"
                else -> "Нет данных"
            }

            recommendationText.text = prediction.improvementTip

            // 🔥 ИСПОЛЬЗУЕМ prediction.inProgressCards
            val currentStats = viewModel.deckStats.value
            val inProgress = currentStats?.inProgressCards ?: 0

            val progressToGoal = if (prediction.totalCards > 0) {
                ((prediction.learnedCards + inProgress * 0.5f) / prediction.totalCards * 100).toInt()
            } else {
                0
            }
            completionProgress.progress = progressToGoal.coerceIn(0, 100)
            completionProgressText.text = "${progressToGoal.coerceIn(0, 100)}%"

            progressEmoji.text = prediction.getStatusEmoji()
            progressStatus.text = prediction.getStatusText()

            estimatedCompletion.text = when {
                prediction.predictedDaysToComplete > 0 -> "⏳ Осталось ~${prediction.predictedDaysToComplete} дней"
                prediction.learnedCards >= prediction.totalCards -> "✅ Колода завершена!"
                prediction.dailyLearningRate <= 0 && prediction.totalCards > 0 -> "📊 Начните изучение карточек для прогноза"
                else -> "📊 Начните обучение для прогноза"
            }
        }
    }

    private fun displayMilestones(milestones: List<DeckPrediction.Milestone>) {
        if (milestones.isEmpty()) {
            binding.milestonesCard.visibility = View.GONE
            return
        }

        binding.milestonesCard.visibility = View.VISIBLE
        binding.milestonesContainer.removeAllViews()

        for (milestone in milestones) {
            val itemView = LayoutInflater.from(context).inflate(
                R.layout.item_milestone,
                binding.milestonesContainer,
                false
            )

            val percentageText = itemView.findViewById<TextView>(R.id.percentageText)
            val dateText = itemView.findViewById<TextView>(R.id.dateText)
            val statusIcon = itemView.findViewById<TextView>(R.id.statusIcon)

            percentageText.text = "${milestone.percentage}%"
            dateText.text = milestone.predictedDate
            statusIcon.text = if (milestone.isAchieved) "✅" else "⏳"

            binding.milestonesContainer.addView(itemView)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}