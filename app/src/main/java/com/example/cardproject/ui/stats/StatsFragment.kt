package com.example.cardproject.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.cardproject.R
import com.example.cardproject.databinding.FragmentStatsBinding
import com.example.cardproject.model.DeckStats
import com.example.cardproject.model.NavigationSource
import com.example.cardproject.model.SessionStats
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StatsViewModel by viewModels()

    private var scrollListener: ViewTreeObserver.OnScrollChangedListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupObservers()
        setupClickListeners()
    }

    private fun setupScrolling() {
        binding.decksStatsScrollView.viewTreeObserver.addOnScrollChangedListener {
            updateScrollIndicator()
        }
    }
    private fun updateScrollIndicator() {
        // ПРОВЕРЬТЕ что binding не null
        if (_binding == null) return

        val scrollView = binding.decksStatsScrollView
        val canScrollRight = scrollView.canScrollHorizontally(1)
        val canScrollLeft = scrollView.canScrollHorizontally(-1)

        val indicatorText = when {
            canScrollLeft && canScrollRight -> "← Листайте в обе стороны →"
            canScrollRight -> "Листайте вправо →"
            canScrollLeft -> "← Листайте влево"
            else -> "Все колоды видны"
        }

        // ИСПРАВЬТЕ: используйте существующий TextView или создайте новый
        val indicatorTextView = binding.scrollIndicator.getChildAt(0) as? android.widget.TextView
        indicatorTextView?.text = indicatorText
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.allSessions.collectLatest { sessions ->
                displayRecentSessions(sessions)
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.totalStats.collectLatest { stats ->
                displayTotalStats(stats)
            }
        }
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.decksStatsSync.collect { decksStats ->
                println("Статистика колод: ${decksStats.size} колод")
                displayDecksStats(decksStats)
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.debugInfo.collect { info ->
                if (info.isNotEmpty()) {
                    println("🐛 Debug: $info")
                    // Можно показать debug информацию в UI если нужно
                    binding.debugText.text = info
                    binding.debugText.visibility =
                        if (info.isNotEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun displayDecksStats(decksStats: List<com.example.cardproject.model.DeckStats>) {
        if (decksStats.isEmpty()) {
            showEmptyDecksStats()
        } else {
            showDecksStatsList(decksStats)
            // ДОБАВЬТЕ: настройка скроллинга только когда есть данные
            setupHorizontalScrolling()
        }
    }

    private fun setupHorizontalScrolling() {
        // УДАЛИТЕ старый слушатель если он есть
        scrollListener?.let { oldListener ->
            binding.decksStatsScrollView.viewTreeObserver.removeOnScrollChangedListener(oldListener)
        }

        // СОЗДАЙТЕ новый слушатель
        val newScrollListener = ViewTreeObserver.OnScrollChangedListener {
            updateScrollIndicator()
        }

        scrollListener = newScrollListener
        binding.decksStatsScrollView.viewTreeObserver.addOnScrollChangedListener(newScrollListener)
    }

    private fun showEmptyDecksStats() {
        binding.decksStatsSection.visibility = View.VISIBLE
        binding.decksStatsList.visibility = View.GONE
        binding.emptyDecksStats.visibility = View.VISIBLE
        binding.emptyDecksStats.text = "Нет колод для отображения статистики"
    }

    private fun showDecksStatsList(decksStats: List<DeckStats>) {
        binding.decksStatsSection.visibility = View.VISIBLE
        binding.decksStatsList.visibility = View.VISIBLE
        binding.emptyDecksStats.visibility = View.GONE

        setupDecksStatsList(decksStats)
    }

    private fun setupDecksStatsList(decksStats: List<DeckStats>) {
        binding.decksStatsList.removeAllViews()

        // Сортируем колоды по прогрессу изучения
        val sortedDecks = decksStats.sortedByDescending { it.learnedPercentage }

        sortedDecks.forEach { deckStats ->
            val deckView = createDeckStatsView(deckStats)
            binding.decksStatsList.addView(deckView)
        }

        // Показываем заголовок с количеством
        binding.decksStatsTitle.text = "Статистика колод (${decksStats.size})"

        // Показываем индикатор прокрутки если колод больше 1
        if (decksStats.size > 1) {
            binding.scrollIndicator.visibility = View.VISIBLE
        } else {
            binding.scrollIndicator.visibility = View.GONE
        }
    }


    private fun displayTotalStats(stats: TotalStats) {
        binding.totalSessionsText.text = stats.totalSessions.toString()
        binding.totalCardsStudiedText.text = stats.totalCardsStudied.toString()
        binding.averageAccuracyText.text = "%.1f%%".format(stats.averageAccuracy)
        binding.totalStudyTimeText.text = stats.formattedTotalTime

        // Обновляем прогресс-бары
        binding.accuracyProgress.progress = stats.averageAccuracy.toInt()
        binding.sessionsProgress.progress = (stats.totalSessions * 100 / 50).coerceAtMost(100) // 50 сессий = 100%
    }

    private fun displayRecentSessions(sessions: List<SessionStats>) {
        if (sessions.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recentSessionsList.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recentSessionsList.visibility = View.VISIBLE

            // Простой адаптер для списка сессий
            setupSessionsList(sessions)
        }
    }

    private fun setupSessionsList(sessions: List<SessionStats>) {
        // Очищаем предыдущие элементы
        binding.recentSessionsList.removeAllViews()

        sessions.forEach { session ->
            val sessionView = createSessionView(session)
            binding.recentSessionsList.addView(sessionView)
        }
    }

    private fun createSessionView(session: SessionStats): View {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.item_session_stats, binding.recentSessionsList, false)

        val deckName = view.findViewById<android.widget.TextView>(R.id.sessionDeckName)
        val sessionType = view.findViewById<android.widget.TextView>(R.id.sessionType)
        val accuracy = view.findViewById<android.widget.TextView>(R.id.sessionAccuracy)
        val date = view.findViewById<android.widget.TextView>(R.id.sessionDate)
        val cardCount = view.findViewById<android.widget.TextView>(R.id.sessionCardCount)

        deckName.text = session.deckName
        sessionType.text = when (session.sessionType) {
            com.example.cardproject.model.SessionType.SPACED_REPETITION -> "Интервальное"
            com.example.cardproject.model.SessionType.FULL_REVIEW -> "Все карточки"
        }
        accuracy.text = "%.1f%%".format(session.accuracy)
        date.text = java.text.SimpleDateFormat("dd.MM.yy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(session.date))
        cardCount.text = "${session.totalCards} карточек"

        // Цвет точности в зависимости от результата
        when {
            session.accuracy >= 90 -> accuracy.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
            session.accuracy >= 70 -> accuracy.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
            else -> accuracy.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
        }

        view.setOnClickListener {
            showSessionDetails(session)
        }

        return view
    }

    private fun showSessionDetails(session: SessionStats) {
        val sessionStatsFragment = SessionStatsFragment().apply {
            arguments = Bundle().apply {
                putParcelable("sessionStats", session)
                putBoolean("openFromStats", true)
                putSerializable("navigationSource", NavigationSource.FROM_STATS)
            }
        }

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, sessionStatsFragment)
            .addToBackStack("stats_details")
            .commit()
    }

    private fun setupClickListeners() {
        binding.clearStatsButton.setOnClickListener {
            showClearStatsDialog()
        }
    }

    private fun showClearStatsDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Очистить статистику")
            .setMessage("Вы уверены, что хотите удалить всю статистику? Это действие нельзя отменить.")
            .setPositiveButton("Очистить") { dialog, _ ->
                viewModel.clearAllStats()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun displayDecksStats() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            // Здесь нужно получить список всех колод с их статистикой
            // Пока добавим заглушку
            displayDecksList(emptyList())
        }
    }

    private fun displayDecksList(decks: List<DeckStats>) {
        if (decks.isEmpty()) {
            binding.decksStatsSection.visibility = View.GONE
        } else {
            binding.decksStatsSection.visibility = View.VISIBLE
            setupDecksList(decks)
        }
    }

    private fun setupDecksList(decks: List<DeckStats>) {
        binding.decksStatsList.removeAllViews()

        decks.forEach { deckStats ->
            val deckView = createDeckStatsView(deckStats)
            binding.decksStatsList.addView(deckView)
        }
    }

    private fun createDeckStatsView(deckStats: DeckStats): View {
        val inflater = LayoutInflater.from(requireContext())

        // Убедитесь, что используете правильное имя макета
        val view = inflater.inflate(R.layout.item_deck_stats_horizontal, binding.decksStatsList, false)

        // Находим все View по ID
        val deckName = view.findViewById<android.widget.TextView>(R.id.deckName)
        val totalCards = view.findViewById<android.widget.TextView>(R.id.totalCards)
        val learnedCards = view.findViewById<android.widget.TextView>(R.id.learnedCards)
        val inProgressCards = view.findViewById<android.widget.TextView>(R.id.inProgressCards)
        val newCards = view.findViewById<android.widget.TextView>(R.id.newCards)
        val accuracy = view.findViewById<android.widget.TextView>(R.id.accuracy)
        val progressBar = view.findViewById<android.widget.ProgressBar>(R.id.progressBar)
        val progressText = view.findViewById<android.widget.TextView>(R.id.progressText)
        val sessionsCount = view.findViewById<android.widget.TextView>(R.id.sessionsCount)
        val detailButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.detailButton)

        // Заполняем данные
        deckName.text = deckStats.deckName
        totalCards.text = "${deckStats.totalCards} карточек"
        learnedCards.text = deckStats.learnedCards.toString()
        inProgressCards.text = deckStats.inProgressCards.toString()
        newCards.text = deckStats.newCards.toString()

        if (deckStats.totalSessions > 0) {
            accuracy.text = "Средняя точность: ${"%.1f".format(deckStats.averageAccuracy)}%"
            sessionsCount.text = "${deckStats.totalSessions} сессий"
        } else {
            accuracy.text = "Нет данных о точности"
            sessionsCount.text = "нет сессий"
        }

        // Прогресс-бар
        val progress = deckStats.learnedPercentage.toInt()
        progressBar.progress = progress
        progressText.text = "$progress%"

        // Цвет прогресса
        updateProgressColors(progressText, progressBar, progress)

        // Обработчик клика на всю карточку
        view.setOnClickListener {
            openDeckStats(deckStats.deckId)
        }

        // Обработчик клика на кнопку
        detailButton.setOnClickListener {
            openDeckStats(deckStats.deckId)
        }

        return view
    }

    private fun updateProgressColors(progressText: android.widget.TextView, progressBar: android.widget.ProgressBar, progress: Int) {
        val color = when {
            progress >= 80 -> android.R.color.holo_green_dark
            progress >= 50 -> android.R.color.holo_orange_dark
            else -> android.R.color.holo_red_dark
        }

        progressText.setTextColor(requireContext().getColor(color))
        progressBar.progressTintList = android.content.res.ColorStateList.valueOf(
            requireContext().getColor(color)
        )
    }

    private fun openDeckStats(deckId: Long) {
        val deckStatsFragment = DeckStatsFragment().apply {
            arguments = Bundle().apply {
                putLong("deckId", deckId)
            }
        }

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, deckStatsFragment)
            .addToBackStack("stats")
            .commit()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        scrollListener?.let { listener ->
            binding.decksStatsScrollView.viewTreeObserver.removeOnScrollChangedListener(listener)
        }
        scrollListener = null
        _binding = null
    }
}