package com.example.cardproject.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.example.cardproject.R
import com.example.cardproject.databinding.FragmentSessionStatsBinding
import com.example.cardproject.model.LearningMode
import com.example.cardproject.model.NavigationSource
import com.example.cardproject.model.SessionStats
import com.example.cardproject.model.SessionType
import com.example.cardproject.ui.card.CardListFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SessionStatsFragment : Fragment() {

    private var _binding: FragmentSessionStatsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SessionStatsViewModel by viewModels()

    private var sessionStats: SessionStats? = null
    private var openFromStats: Boolean = false
    private var navigationSource: NavigationSource = NavigationSource.FROM_DECKS

    private fun debugBackStack() {
        val fragmentManager = requireActivity().supportFragmentManager
        println("🔍 BackStack entries в SessionStatsFragment:")
        for (i in 0 until fragmentManager.backStackEntryCount) {
            val entry = fragmentManager.getBackStackEntryAt(i)
            println("   [$i] name: '${entry.name ?: "null"}'")
        }
        println("🔍 openFromStats = $openFromStats")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionStats = arguments?.getParcelable("sessionStats")
        openFromStats = arguments?.getBoolean("openFromStats", false) ?: false
        navigationSource = arguments?.getSerializable("navigationSource") as? NavigationSource
            ?: NavigationSource.FROM_DECKS
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSessionStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        debugBackStack()
        updateButtonText()
        setupToolbar()
        displayStats()
        setupClickListeners()
    }

    private fun updateButtonText() {
        // Меняем текст кнопки в зависимости от источника навигации
        when (navigationSource) {
            NavigationSource.FROM_STATS -> {
                binding.closeButton.text = "Вернуться к статистике"
            }
            NavigationSource.FROM_LEARNING -> {
                binding.closeButton.text = "Вернуться к карточкам"
            }
            NavigationSource.FROM_DECKS -> {
                binding.closeButton.text = "Вернуться к колодам"
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            navigateBack()
        }
    }

    private fun navigateBack() {
        if (openFromStats) {
            // Открыто из статистики - просто возвращаемся назад к статистике
            parentFragmentManager.popBackStack()
        } else {
            // Открыто из обучения - возвращаемся к карточкам конкретной колоды
            navigateBackToCardList()
        }
    }

    private fun navigateBackToCardList() {
        val fragmentManager = requireActivity().supportFragmentManager

        // Простая логика: если открыто из обучения, просто делаем два pop back stack
        // чтобы вернуться из: session_stats -> learning -> card_list
        fragmentManager.popBackStack() // Выходим из session_stats
        fragmentManager.popBackStack() // Выходим из learning (возвращаемся к card_list)
    }


    private fun displayStats() {
        val stats = sessionStats ?: return

        binding.sessionTypeText.text = when (stats.sessionType) {
            SessionType.SPACED_REPETITION -> "Интервальное повторение"
            SessionType.FULL_REVIEW -> "Просмотр всех карточек"
        }

        binding.totalCardsText.text = stats.totalCards.toString()
        binding.correctAnswersText.text = stats.correctAnswers.toString()
        binding.wrongAnswersText.text = stats.wrongAnswers.toString()
        binding.accuracyText.text = "%.1f%%".format(stats.accuracy)
        binding.durationText.text = stats.formattedDuration
        binding.dateText.text = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(stats.date))

        // Визуализация точности
        updateAccuracyVisual(stats.accuracy)

        // Дополнительная информация в зависимости от типа сессии
        if (stats.sessionType == SessionType.SPACED_REPETITION) {
            binding.learningModeText.text = when (stats.learningMode) {
                LearningMode.LONG_TERM -> "Режим: Фундаментальный"
                LearningMode.SHORT_TERM -> "Режим: Подготовка к событию"
                null -> ""
            }
            binding.learningModeText.visibility = View.VISIBLE
        } else {
            binding.learningModeText.visibility = View.GONE
        }
    }

    private fun updateAccuracyVisual(accuracy: Double) {
        when {
            accuracy >= 90 -> {
                binding.accuracyText.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
                binding.accuracyComment.text = "Отличный результат!"
            }
            accuracy >= 70 -> {
                binding.accuracyText.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
                binding.accuracyComment.text = "Хороший результат!"
            }
            accuracy >= 50 -> {
                binding.accuracyText.setTextColor(requireContext().getColor(android.R.color.holo_orange_light))
                binding.accuracyComment.text = "Неплохо, можно лучше"
            }
            else -> {
                binding.accuracyText.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
                binding.accuracyComment.text = "Нужно повторить материал"
            }
        }
    }

    private fun setupClickListeners() {
        // ИСПРАВЛЕНО: используем navigateBack() вместо returnToCardList()
        binding.closeButton.setOnClickListener {
            navigateBack()
        }

        // ИСПРАВЛЕНО: навигационная иконка уже настроена в setupToolbar()
        // Убираем дублирующий вызов
        // binding.toolbar.setNavigationOnClickListener {
        //     returnToCardList()
        // }
    }

    private fun returnToCardList() {
        val stats = sessionStats ?: return

        // Создаем CardListFragment с параметрами конкретной колоды
        val cardListFragment = CardListFragment().apply {
            arguments = Bundle().apply {
                putLong("deckId", stats.deckId)
                putString("deckName", stats.deckName)
            }
        }

        // Очищаем backstack до корня и создаем новую цепочку:
        // DeckListFragment -> CardListFragment
        requireActivity().supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, cardListFragment)
            .addToBackStack("card_list") // Добавляем в backstack
            .commit()
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}