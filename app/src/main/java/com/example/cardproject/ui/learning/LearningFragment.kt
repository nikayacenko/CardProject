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
    private var isCurrentAnswerLong = false
    private var isAnimating = false
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
        setupCardPerspective()
    }
    private fun setupCardPerspective() {
        binding.cardContainer.cameraDistance = 10000 * resources.displayMetrics.density
        binding.cardFrontLayout.cameraDistance = 10000 * resources.displayMetrics.density
        binding.cardBackLayout.cameraDistance = 10000 * resources.displayMetrics.density
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

// Наблюдаем за изменением контекста (усталости)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.sessionContext.collect { context ->
                updateFatigueUI(context.userFatigueLevel)
            }
        }

        // Наблюдаем за статусом AI (текстовое сообщение под шкалой)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.lastCalcStatus.collect { status ->
                binding.mlStatusText.text = status
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.learningProgress.collect { progress ->
                binding.progressText.text = "Всего: ${progress.totalCards} | К изучению: ${progress.newCards + progress.learnedCards} | Изучено: ${progress.learnedCards}"

                // Обновляем прогресс-бар
                val studied = progress.totalCards - (progress.newCards + progress.learnedCards)
                binding.cardProgress.progress = if (progress.totalCards > 0) {
                    (studied * 100 / progress.totalCards).toInt()
                } else 0
            }
        }
    }
    private fun updateFatigueUI(fatigue: Float) {
        // Преобразуем 0.0-1.0 в 0-100 для ProgressBar
        val progress = (fatigue * 100).toInt()
        binding.fatigueIndicator.setProgress(progress, true)

        // Меняем текст и цвет в зависимости от уровня усталости
        when {
            fatigue < 0.3f -> {
                binding.fatigueText.text = "Низкая усталость"
                binding.fatigueText.setTextColor(requireContext().getColor(R.color.green_500))
                binding.fatigueIndicator.progressTintList =
                    android.content.res.ColorStateList.valueOf(
                        requireContext().getColor(R.color.green_500)
                    )
            }

            fatigue < 0.7f -> {
                binding.fatigueText.text = "Средняя усталость"
                binding.fatigueText.setTextColor(requireContext().getColor(R.color.orange_500))
                binding.fatigueIndicator.progressTintList =
                    android.content.res.ColorStateList.valueOf(
                        requireContext().getColor(R.color.orange_500)
                    )
            }

            else -> {
                binding.fatigueText.text = "Высокая усталость"
                binding.fatigueText.setTextColor(requireContext().getColor(R.color.red_500))
                binding.fatigueIndicator.progressTintList =
                    android.content.res.ColorStateList.valueOf(
                        requireContext().getColor(R.color.red_500)
                    )
            }
        }
    }

    private fun showCard(card: Card) {
        binding.cardFrontText.text = card.front
        binding.cardBackText.text = card.back
        resetCardState()

        isCurrentAnswerLong = card.back.length > 500

        // Сбрасываем состояние
        binding.cardBackText.movementMethod = null
        binding.btnShowFull.visibility = View.GONE
        binding.btnShowFull.setOnClickListener(null)

        if (isCurrentAnswerLong) {
            // Обрезаем текст
            val truncatedText = if (card.back.length > 500) {
                card.back.substring(0, 500) + "..."
            } else {
                card.back
            }
            binding.cardBackText.text = truncatedText

            // Показываем кнопку
            binding.btnShowFull.visibility = View.VISIBLE
            binding.btnShowFull.setOnClickListener {
                showFullAnswerDialog()
            }
        } else {
            binding.cardBackText.text = card.back
            binding.btnShowFull.visibility = View.GONE
        }
        binding.cardBackLayout.visibility = View.GONE
        binding.cardFrontLayout.visibility = View.VISIBLE
        binding.answerButtons.visibility = View.GONE
        isFrontVisible = true

        binding.cardCounter.text = "Карта: ${viewModel.getAnsweredCount() + 1}/${viewModel.getTotalCount()}"
    }

    private fun resetCardState() {
        binding.cardFrontLayout.clearAnimation()
        binding.cardBackLayout.clearAnimation()

        binding.cardFrontLayout.visibility = View.VISIBLE
        binding.cardFrontLayout.rotationY = 0f

        binding.cardBackLayout.visibility = View.GONE
        binding.cardBackLayout.rotationY = 180f

        binding.answerButtons.visibility = View.GONE
        binding.btnShowFull.visibility = View.GONE
        binding.btnShowFull.setOnClickListener(null)

        isFrontVisible = true
        isAnimating = false
    }
    private fun showFullAnswerDialog() {
        val card = viewModel.currentCard.value ?: return

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Полный ответ")
            .setMessage(card.back)
            .setPositiveButton("Закрыть", null)
            .create()

        dialog.show()

        // Делаем текст прокручиваемым (как в FullScreenCardFragment)
        val messageTextView = dialog.findViewById<android.widget.TextView>(android.R.id.message)
        messageTextView?.let { textView ->
            textView.movementMethod = android.text.method.ScrollingMovementMethod()
            textView.setPadding(50, 30, 50, 30)
        }

        // Настраиваем размер (как в FullScreenCardFragment)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.7).toInt()
        )
    }
    private fun setupClickListeners() {
        binding.cardContainer.setOnClickListener {
            if (!isAnimating) {
                flipCard()
            }
        }

        // 2. Кнопка "показать полностью"
        binding.btnShowFull.setOnClickListener {
            showFullAnswerDialog()
        }

        // 3. Кнопки ответа
        binding.knowButton.setOnClickListener { submitAnswer(1) }
        binding.dontKnowButton.setOnClickListener { submitAnswer(0) }
    }

    private fun flipCard() {
        if (isAnimating) return
        isAnimating = true

        if (isFrontVisible) {
            flipToBack()
        } else {
            flipToFront()
        }
    }
    private fun flipToBack() {
        binding.cardFrontLayout.animate()
            .rotationY(90f)
            .setDuration(200)
            .withEndAction {
                binding.cardFrontLayout.visibility = View.GONE
                binding.cardBackLayout.visibility = View.VISIBLE
                binding.cardBackLayout.rotationY = -90f

                binding.cardBackLayout.animate()
                    .rotationY(0f)
                    .setDuration(200)
                    .withEndAction {
                        isFrontVisible = false
                        isAnimating = false
                        binding.answerButtons.visibility = View.VISIBLE

                        // Показываем кнопку "показать полностью" если ответ длинный
                        val card = viewModel.currentCard.value
                        if (card != null && card.back.length > 500) {
                            binding.btnShowFull.visibility = View.VISIBLE
                        }
                    }
                    .start()
            }
            .start()
    }

    /**
     * Переворот на переднюю сторону (вопрос)
     */
    private fun flipToFront() {
        binding.cardBackLayout.animate()
            .rotationY(90f)
            .setDuration(200)
            .withEndAction {
                binding.cardBackLayout.visibility = View.GONE
                binding.cardFrontLayout.visibility = View.VISIBLE
                binding.cardFrontLayout.rotationY = -90f

                binding.cardFrontLayout.animate()
                    .rotationY(0f)
                    .setDuration(200)
                    .withEndAction {
                        isFrontVisible = true
                        isAnimating = false
                        binding.answerButtons.visibility = View.GONE
                        binding.btnShowFull.visibility = View.GONE
                    }
                    .start()
            }
            .start()
    }

    private fun submitAnswer(quality: Int) {
        val card = viewModel.currentCard.value ?: return
        val time = SystemClock.elapsedRealtime() - cardShowTime
        if (quality == 0) {
            viewModel.blockCard(card, 30)  // Блокируем на 30 секунд
        }
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