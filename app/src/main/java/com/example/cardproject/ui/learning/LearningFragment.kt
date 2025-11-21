package com.example.cardproject.ui.learning

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.cardproject.R
import com.example.cardproject.algorithm.LearningProgress
import com.example.cardproject.model.LearningMode
import dagger.hilt.android.AndroidEntryPoint
import com.example.cardproject.databinding.FragmentLearningBinding
import com.example.cardproject.model.Card
import com.example.cardproject.model.NavigationSource
import com.example.cardproject.ui.stats.SessionStatsFragment
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LearningFragment : Fragment() {

    private var _binding: FragmentLearningBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LearningViewModel by viewModels()

    private var deckId: Long = -1
    private var learningMode: LearningMode = LearningMode.LONG_TERM
    private var isFrontVisible = true
    private var isAnimating = false
    private var isCurrentAnswerLong = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deckId = arguments?.getLong("deckId") ?: -1
        learningMode = arguments?.getSerializable("learningMode") as? LearningMode ?: LearningMode.LONG_TERM
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLearningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupCardPerspective()
        viewModel.setDeckId(deckId, learningMode)
        setupObservers()
        setupClickListeners()
        updateLearningModeUI()
    }



    private fun setupToolbar() {
        // Показываем диалог подтверждения при выходе
        binding.toolbar.setNavigationOnClickListener {
            showExitConfirmationDialog()
        }
    }

    private fun showExitConfirmationDialog() {
        val answeredCards = viewModel.getAnsweredCardsCount()

        if (answeredCards > 0) {
            AlertDialog.Builder(requireContext())
                .setTitle("Прервать сессию?")
                .setMessage("Вы ответили на $answeredCards карточек. При выходе прогресс этой сессии не сохранится, и карточки будут показаны снова.\n\nПродолжить обучение?")
                .setPositiveButton("Остаться") { dialog, _ ->
                    dialog.dismiss()
                }
                .setNegativeButton("Выйти") { dialog, _ ->
                    // Отменяем сессию (очищаем временные ответы)
                    viewModel.cancelSession()
                    requireActivity().supportFragmentManager.popBackStack()
                    dialog.dismiss()
                }
                .setCancelable(true)
                .show()
        } else {
            // Если нет ответов - просто выходим
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    // Остальные методы БЕЗ ИЗМЕНЕНИЙ
    private fun setupCardPerspective() {
        binding.cardContainer.cameraDistance = 10000 * resources.displayMetrics.density
        binding.cardFrontLayout.cameraDistance = 10000 * resources.displayMetrics.density
        binding.cardBackLayout.cameraDistance = 10000 * resources.displayMetrics.density
    }

    private fun updateLearningModeUI() {
        val modeText = when (learningMode) {
            LearningMode.LONG_TERM -> "Долговременное обучение"
            LearningMode.SHORT_TERM -> "Краткосрочное обучение"
        }
        binding.learningModeText.text = modeText
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.isFinished.collect { finished ->
                if (finished) {
                    showSessionStats()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.currentCard.collect { card ->
                card?.let {
                    showCard(it)
                } ?: showNoCards()
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.learningProgress.collect { progress ->
                updateProgress(progress)
            }
        }
    }

    private fun showSessionStats() {
        val stats = viewModel.getCurrentSessionStats()

        val statsFragment = SessionStatsFragment().apply {
            arguments = Bundle().apply {
                putParcelable("sessionStats", stats)
                putBoolean("openFromStats", false)
                putSerializable("navigationSource", NavigationSource.FROM_LEARNING)
            }
        }

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, statsFragment)
            .addToBackStack("session_stats_from_learning")
            .commit()
    }

    private fun showCard(card: Card) {
        binding.cardFrontText.text = card.front
        binding.cardBackText.text = card.back

        resetCardState()
        isCurrentAnswerLong = card.back.length > 400

        binding.cardCounter.text = "Осталось: ${viewModel.getRemainingCount()}"
        binding.cardProgress.progress = calculateProgress()
    }

    private fun resetCardState() {
        binding.cardFrontLayout.clearAnimation()
        binding.cardBackLayout.clearAnimation()

        binding.cardFrontLayout.visibility = View.VISIBLE
        binding.cardFrontLayout.rotationY = 0f
        binding.cardFrontLayout.alpha = 1f

        binding.cardBackLayout.visibility = View.GONE
        binding.cardBackLayout.rotationY = 180f
        binding.cardBackLayout.alpha = 1f

        isFrontVisible = true
        isAnimating = false

        binding.cardBackText.movementMethod = null
        binding.btnShowFull.visibility = View.GONE
        binding.btnShowFull.setOnClickListener(null)
    }

    private fun calculateProgress(): Int {
        val total = viewModel.getTotalCount()
        val remaining = viewModel.getRemainingCount()
        return if (total > 0) {
            ((total - remaining) * 100) / total
        } else {
            0
        }
    }

    private fun showNoCards() {
        binding.cardFrontText.text = "Нет карточек для повторения"
        binding.cardBackText.text = "Все карточки изучены!"
        binding.btnShowFull.visibility = View.GONE
        binding.answerButtons.visibility = View.GONE
        binding.cardProgress.progress = 100
    }

    private fun updateProgress(progress: LearningProgress) {
        binding.progressText.text =
            "Всего: ${progress.totalCards} | " +
                    "К изучению: ${progress.dueCards} | " +
                    "Изучено: ${progress.learnedCards}"
    }

    private fun setupClickListeners() {
        binding.cardContainer.setOnClickListener {
            if (!isAnimating) {
                flipCard()
            }
        }

        binding.knowButton.setOnClickListener {
            viewModel.answerCard(1)
        }

        binding.dontKnowButton.setOnClickListener {
            viewModel.answerCard(0)
        }
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
        isAnimating = true
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
                        if (isCurrentAnswerLong) {
                            setupLongAnswerText()
                        }
                        binding.answerButtons.visibility = View.VISIBLE
                    }
                    .start()
            }
            .start()
    }

    private fun flipToFront() {
        isAnimating = true
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
                    }
                    .start()
            }
            .start()
    }

    private fun setupLongAnswerText() {
        val currentCard = viewModel.currentCard.value ?: return

        val charThreshold = 300
        if (currentCard.back.length > charThreshold) {
            val truncatedText = if (currentCard.back.length > 300) {
                currentCard.back.substring(0, 300) + "..."
            } else {
                currentCard.back
            }

            binding.cardBackText.text = truncatedText
            binding.btnShowFull.visibility = View.VISIBLE
            binding.btnShowFull.setOnClickListener {
                showFullAnswerDialog()
            }
        } else {
            binding.cardBackText.text = currentCard.back
            binding.btnShowFull.visibility = View.GONE
        }

        binding.cardBackText.movementMethod = null
    }

    private fun showFullAnswerDialog() {
        val currentCard = viewModel.currentCard.value ?: return

        val dialog = AlertDialog.Builder(requireContext(), R.style.RoundedDialogTheme)
            .setTitle("Полный ответ")
            .setMessage(currentCard.back)
            .setPositiveButton("Закрыть", null)
            .create()

        dialog.show()

        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton?.setOnClickListener {
            dialog.dismiss()
        }

        val messageTextView = dialog.findViewById<android.widget.TextView>(android.R.id.message)
        messageTextView?.let { textView ->
            textView.movementMethod = android.text.method.ScrollingMovementMethod()
            textView.setPadding(50, 30, 50, 30)

            textView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    textView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    val lineCount = textView.lineCount
                    val lineHeight = textView.lineHeight
                    val padding = textView.paddingTop + textView.paddingBottom
                    val textHeight = lineCount * lineHeight + padding

                    val displayMetrics = resources.displayMetrics
                    val screenHeight = displayMetrics.heightPixels
                    val maxDialogHeight = (screenHeight * 0.8).toInt()
                    val minDialogHeight = (screenHeight * 0.3).toInt()

                    val optimalHeight = textHeight.coerceIn(minDialogHeight, maxDialogHeight)

                    dialog.window?.setLayout(
                        (displayMetrics.widthPixels * 0.9).toInt(),
                        optimalHeight
                    )
                    setupButtonPosition(dialog, positiveButton)
                }
            })
        }

        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background)
    }

    private fun setupButtonPosition(dialog: AlertDialog, button: android.widget.Button?) {
        button?.let {
            val layoutParams = it.layoutParams as? android.widget.LinearLayout.LayoutParams
            layoutParams?.let { params ->
                params.gravity = android.view.Gravity.END
                params.setMargins(0, 0, 16, 16)
                it.layoutParams = params
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}