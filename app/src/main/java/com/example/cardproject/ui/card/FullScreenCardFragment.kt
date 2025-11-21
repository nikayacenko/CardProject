package com.example.cardproject.ui.card

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.cardproject.R
import com.example.cardproject.databinding.FragmentFullScreenCardBinding
import com.example.cardproject.model.Card
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FullScreenCardFragment : Fragment() {

    private var _binding: FragmentFullScreenCardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CardListViewModel by viewModels()

    private var deckId: Long = -1
    private var deckName: String = ""
    private var currentCardIndex = 0
    private var cards: List<Card> = emptyList()
    private var isFrontVisible = true
    private var isCurrentAnswerLong = false
    private var isAnimating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deckId = arguments?.getLong("deckId", -1) ?: -1
        deckName = arguments?.getString("deckName", "Карточки") ?: "Карточки"

        // Включаем обработку меню
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFullScreenCardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.title = deckName
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        // Устанавливаем свой обработчик меню
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_shuffle -> {
                    shuffleCards()
                    true
                }
                else -> false
            }
        }

        viewModel.setDeckId(deckId)
        setupObservers()
        setupClickListeners()
        setupCardPerspective()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_card_actions, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        // Обновляем состояние меню (активно/неактивно)
        val shuffleMenuItem = menu.findItem(R.id.menu_shuffle)
        shuffleMenuItem?.isEnabled = cards.size > 1

        // Можно также изменить иконку если нужно показать неактивное состояние
        if (cards.size <= 1) {
            shuffleMenuItem?.icon?.alpha = 128 // Полупрозрачный когда неактивно
        } else {
            shuffleMenuItem?.icon?.alpha = 255 // Полностью непрозрачный когда активно
        }
    }

    private fun setupCardPerspective() {
        binding.cardContainer.cameraDistance = 10000 * resources.displayMetrics.density
        binding.cardFrontLayout.cameraDistance = 10000 * resources.displayMetrics.density
        binding.cardBackLayout.cameraDistance = 10000 * resources.displayMetrics.density
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.cards.collectLatest { cardList ->
                cards = cardList
                if (cards.isNotEmpty()) {
                    showCard(currentCardIndex)
                } else {
                    showEmptyState()
                }
                // Обновляем меню когда меняются карточки
                activity?.invalidateOptionsMenu()
            }
        }
    }

    private fun setupClickListeners() {
        binding.cardContainer.setOnClickListener {
            if (!isAnimating) {
                flipCard()
            }
        }

        binding.btnNext.setOnClickListener { nextCard() }
        binding.btnPrev.setOnClickListener { previousCard() }
        // Убрали обработчик shuffleCards из кнопки
    }

    private fun showCard(index: Int) {
        if (cards.isEmpty()) return

        val card = cards[index]
        binding.cardFrontText.text = card.front
        binding.cardBackText.text = card.back // Сначала устанавливаем полный текст

        resetCardState()
        isCurrentAnswerLong = card.back.length > 500 // Обновите порог

        binding.cardCounter.text = "${index + 1}/${cards.size}"
        binding.cardProgress.progress = ((index + 1) * 100) / cards.size

        // Обновляем состояние кнопок (только Назад и Вперед)
        binding.btnPrev.isEnabled = index > 0
        binding.btnNext.isEnabled = index < cards.size - 1

        // Обновляем меню
        activity?.invalidateOptionsMenu()
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

        // Сбрасываем состояние текста и кнопки
        binding.cardBackText.movementMethod = null
        binding.btnShowFull.visibility = View.GONE
        binding.btnShowFull.setOnClickListener(null)
    }

    private fun flipCard() {
        if (cards.isEmpty() || isAnimating) return
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
                    .setDuration(299)
                    .withEndAction {
                        isFrontVisible = true
                        isAnimating = false
                    }
                    .start()
            }
            .start()
    }

    private fun setupLongAnswerText() {
        val currentCard = cards[currentCardIndex]

        // Оптимальные значения для большинства экранов
        val charThreshold = 400

        if (currentCard.back.length > charThreshold) {
            // Обрезаем текст
            val truncatedText = if (currentCard.back.length > 400) {
                currentCard.back.substring(0, 400) + "..."
            } else {
                currentCard.back
            }

            // Показываем обрезанный текст
            binding.cardBackText.text = truncatedText

            // Показываем кнопку "показать полностью"
            binding.btnShowFull.visibility = View.VISIBLE
            binding.btnShowFull.setOnClickListener {
                showFullAnswerDialog()
            }

        } else {
            // Текст короткий - показываем полностью и скрываем кнопку
            binding.cardBackText.text = currentCard.back
            binding.btnShowFull.visibility = View.GONE
        }

        // Убираем возможность прокрутки
        binding.cardBackText.movementMethod = null
    }

    private fun showFullAnswerDialog() {
        val currentCard = cards.getOrNull(currentCardIndex) ?: return

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

    private fun nextCard() {
        if (currentCardIndex < cards.size - 1) {
            currentCardIndex++
            showCard(currentCardIndex)
        }
    }

    private fun previousCard() {
        if (currentCardIndex > 0) {
            currentCardIndex--
            showCard(currentCardIndex)
        }
    }

    private fun shuffleCards() {
        if (cards.size > 1) {
            cards = cards.shuffled()
            currentCardIndex = 0
            showCard(currentCardIndex)
            Toast.makeText(requireContext(), "Карточки перемешаны!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Для перемешивания нужно больше карточек", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEmptyState() {
        binding.cardFrontText.text = "Нет карточек"
        binding.cardBackText.text = "Создайте первую карточку"
        binding.cardCounter.text = "0/0"
        binding.cardProgress.progress = 0
        binding.btnPrev.isEnabled = false
        binding.btnNext.isEnabled = false

        // Обновляем меню
        activity?.invalidateOptionsMenu()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


//class FullScreenCardFragment : Fragment() {
//
//    private var _binding: FragmentFullScreenCardBinding? = null
//    private val binding get() = _binding!!
//    private val viewModel: CardListViewModel by viewModels()
//
//    private var deckId: Long = -1
//    private var deckName: String = ""
//    private var currentCardIndex = 0
//    private var cards: List<Card> = emptyList()
//    private var isFrontVisible = true
//    private var isCurrentAnswerLong = false
//
//
//    // Анимации
//    private lateinit var flipRightIn: android.animation.Animator
//    private lateinit var flipRightOut: android.animation.Animator
//    private lateinit var flipLeftIn: android.animation.Animator
//    private lateinit var flipLeftOut: android.animation.Animator
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        deckId = arguments?.getLong("deckId", -1) ?: -1
//        deckName = arguments?.getString("deckName", "Карточки") ?: "Карточки"
//
//        // Загружаем анимации переворота
//        flipRightIn = android.animation.AnimatorInflater.loadAnimator(requireContext(), R.animator.card_flip_right_in)
//        flipRightOut = android.animation.AnimatorInflater.loadAnimator(requireContext(), R.animator.card_flip_right_out)
//        flipLeftIn = android.animation.AnimatorInflater.loadAnimator(requireContext(), R.animator.card_flip_left_in)
//        flipLeftOut = android.animation.AnimatorInflater.loadAnimator(requireContext(), R.animator.card_flip_left_out)
//    }
//
//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        _binding = FragmentFullScreenCardBinding.inflate(inflater, container, false)
//        return binding.root
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        binding.toolbar.title = deckName
//        binding.toolbar.setNavigationOnClickListener {
//            requireActivity().supportFragmentManager.popBackStack()
//        }
//
//        viewModel.setDeckId(deckId)
//        setupObservers()
//        setupClickListeners()
//
//        // Настраиваем кликабельность текста
//        binding.cardBackText.setOnClickListener {
//            if (isCurrentAnswerLong && !isFrontVisible) {
//                showFullAnswerDialog()
//            }
//        }
//    }
//
//    private fun setupObservers() {
//        viewLifecycleOwner.lifecycleScope.launch {
//            viewModel.cards.collectLatest { cardList ->
//                cards = cardList
//                if (cards.isNotEmpty()) {
//                    showCard(currentCardIndex)
//                } else {
//                    showEmptyState()
//                }
//            }
//        }
//    }
//
//    private fun setupClickListeners() {
//        // Переворот карточки
//        binding.cardContainer.setOnClickListener {
//            flipCard()
//        }
//
//        // Следующая карточка
//        binding.btnNext.setOnClickListener {
//            nextCard()
//        }
//
//        // Предыдущая карточка
//        binding.btnPrev.setOnClickListener {
//            previousCard()
//        }
//
//
//        // Случайная карточка
//        binding.btnShuffle.setOnClickListener {
//            shuffleCards()
//        }
//    }
//
//    private fun showCard(index: Int) {
//        if (cards.isEmpty()) return
//
//        val card = cards[index]
//        binding.cardFrontText.text = card.front
//        binding.cardBackText.text = card.back
//
//        // Сбрасываем движение текста
//        binding.cardBackText.movementMethod = null
//
//        // Проверяем длину ответа
//        isCurrentAnswerLong = card.back.length > 200
//
//        // Показываем переднюю сторону
//        binding.cardFrontText.visibility = View.VISIBLE
//        binding.cardBackText.visibility = View.GONE
//        isFrontVisible = true
//
//        // Обновляем счетчик
//        binding.cardCounter.text = "${index + 1}/${cards.size}"
//        binding.cardProgress.progress = ((index + 1) * 100) / cards.size
//
//        // Обновляем состояние кнопок
//        binding.btnPrev.isEnabled = index > 0
//        binding.btnNext.isEnabled = index < cards.size - 1
//    }
//
//    private fun flipCard() {
//        if (cards.isEmpty()) return
//
//        if (isFrontVisible) {
//            // Переворачиваем на заднюю сторону
//            flipCardToBack()
//        } else {
//            // Переворачиваем на переднюю сторону
//            flipCardToFront()
//        }
//    }
//    private fun flipCardToBack() {
//        // Настраиваем анимации для передней стороны (исчезает)
//        flipRightOut.setTarget(binding.cardFrontText)
//        flipRightOut.addListener(object : android.animation.Animator.AnimatorListener {
//            override fun onAnimationStart(animation: android.animation.Animator) {}
//            override fun onAnimationCancel(animation: android.animation.Animator) {}
//            override fun onAnimationRepeat(animation: android.animation.Animator) {}
//
//            override fun onAnimationEnd(animation: android.animation.Animator) {
//                binding.cardFrontText.visibility = View.GONE
//
//                // Показываем заднюю сторону с анимацией появления
//                binding.cardBackText.visibility = View.VISIBLE
//                flipLeftIn.setTarget(binding.cardBackText)
//                flipLeftIn.start()
//
//                // Если ответ длинный, обрезаем текст и добавляем ссылку
//                if (isCurrentAnswerLong) {
//                    setupLongAnswerText()
//                }
//            }
//        })
//
//        flipRightOut.start()
//        isFrontVisible = false
//    }
//    private fun flipCardToFront() {
//        // Настраиваем анимации для задней стороны (исчезает)
//        flipLeftOut.setTarget(binding.cardBackText)
//        flipLeftOut.addListener(object : android.animation.Animator.AnimatorListener {
//            override fun onAnimationStart(animation: android.animation.Animator) {}
//            override fun onAnimationCancel(animation: android.animation.Animator) {}
//            override fun onAnimationRepeat(animation: android.animation.Animator) {}
//
//            override fun onAnimationEnd(animation: android.animation.Animator) {
//                binding.cardBackText.visibility = View.GONE
//
//                // Показываем переднюю сторону с анимацией появления
//                binding.cardFrontText.visibility = View.VISIBLE
//                flipRightIn.setTarget(binding.cardFrontText)
//                flipRightIn.start()
//            }
//        })
//
//        flipLeftOut.start()
//        isFrontVisible = true
//    }
//    private fun setupLongAnswerText() {
//        val currentCard = cards[currentCardIndex]
//        val truncatedText = if (currentCard.back.length > 150) {
//            currentCard.back.substring(0, 150) + "... "
//        } else {
//            currentCard.back + " "
//        }
//
//        // Создаем SpannableString с кликабельной ссылкой
//        val spannable = android.text.SpannableString(truncatedText + "показать полностью")
//        val showMoreStart = truncatedText.length
//        val showMoreEnd = showMoreStart + "показать полностью".length
//
//        // Делаем "показать полностью" кликабельной ссылкой
//        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_500)
//        spannable.setSpan(
//            android.text.style.ForegroundColorSpan(blueColor),
//            showMoreStart,
//            showMoreEnd,
//            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
//        )
//
//        spannable.setSpan(
//            android.text.style.UnderlineSpan(),
//            showMoreStart,
//            showMoreEnd,
//            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
//        )
//
//        binding.cardBackText.text = spannable
//        binding.cardBackText.movementMethod = android.text.method.LinkMovementMethod.getInstance()
//    }
//
//    private fun showFullAnswerDialog() {
//        val currentCard = cards.getOrNull(currentCardIndex) ?: return
//
//        // Создаем кастомный диалог
//        val dialog = AlertDialog.Builder(requireContext(), R.style.RoundedDialogTheme)
//            .setTitle("Полный ответ")
//            .setMessage(currentCard.back)
//            .setPositiveButton("Закрыть", null) // Обработчик будет настроен позже
//            .create()
//
//        // Показываем диалог до настройки размеров
//        dialog.show()
//
//        // Настраиваем кнопку после показа диалога
//        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
//        positiveButton?.setOnClickListener {
//            dialog.dismiss()
//        }
//
//        // Делаем текст прокручиваемым и настраиваем отступы
//        val messageTextView = dialog.findViewById<android.widget.TextView>(android.R.id.message)
//        messageTextView?.let { textView ->
//            textView.movementMethod = android.text.method.ScrollingMovementMethod()
//            textView.setPadding(50, 30, 50, 30)
//
//            // Добавляем слушатель для измерения текста и подстройки размера
//            textView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
//                override fun onGlobalLayout() {
//                    textView.viewTreeObserver.removeOnGlobalLayoutListener(this)
//
//                    // Получаем высоту текста
//                    val lineCount = textView.lineCount
//                    val lineHeight = textView.lineHeight
//                    val padding = textView.paddingTop + textView.paddingBottom
//                    val textHeight = lineCount * lineHeight + padding
//
//                    // Получаем доступную высоту экрана
//                    val displayMetrics = resources.displayMetrics
//                    val screenHeight = displayMetrics.heightPixels
//                    val maxDialogHeight = (screenHeight * 0.8).toInt() // Максимум 80% экрана
//                    val minDialogHeight = (screenHeight * 0.3).toInt() // Минимум 30% экрана
//
//                    // Вычисляем оптимальную высоту
//                    val optimalHeight = textHeight.coerceIn(minDialogHeight, maxDialogHeight)
//
//                    // Настраиваем размер диалога
//                    dialog.window?.setLayout(
//                        (displayMetrics.widthPixels * 0.9).toInt(),
//                        optimalHeight
//                    )
//
//                    // Позиционируем кнопку "Закрыть" в правом нижнем углу
//                    setupButtonPosition(dialog, positiveButton)
//                }
//            })
//        }
//
//        // Настраиваем скругленные углы
//        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_background)
//    }
//    private fun setupButtonPosition(dialog: AlertDialog, button: android.widget.Button?) {
//        button?.let {
//            val layoutParams = it.layoutParams as? android.widget.LinearLayout.LayoutParams
//            layoutParams?.let { params ->
//                params.gravity = android.view.Gravity.END
//                params.setMargins(0, 0, 16, 16) // Отступы от правого и нижнего края
//                it.layoutParams = params
//            }
//        }
//    }
//
//    private fun nextCard() {
//        if (currentCardIndex < cards.size - 1) {
//            currentCardIndex++
//            showCard(currentCardIndex)
//        }
//    }
//
//    private fun previousCard() {
//        if (currentCardIndex > 0) {
//            currentCardIndex--
//            showCard(currentCardIndex)
//        }
//    }
//
//    private fun shuffleCards() {
//        if (cards.size > 1) {
//            // Создаем перемешанную копию списка карточек
//            val shuffledCards = cards.shuffled()
//
//            // Обновляем основной список карточек
//            cards = shuffledCards
//
//            // Сбрасываем индекс на первую карточку
//            currentCardIndex = 0
//
//            // Показываем первую карточку новой перемешанной колоды
//            showCard(currentCardIndex)
//
//            Toast.makeText(requireContext(), "Карточки перемешаны!", Toast.LENGTH_SHORT).show()
//        } else if (cards.size == 1) {
//            Toast.makeText(requireContext(), "Для перемешивания нужно больше карточек", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun openCardList() {
//        requireActivity().supportFragmentManager.popBackStack()
//    }
//
//    private fun showEmptyState() {
//        binding.cardFrontText.text = "Нет карточек"
//        binding.cardBackText.text = "Создайте первую карточку"
//        binding.cardCounter.text = "0/0"
//        binding.cardProgress.progress = 0
//        binding.btnPrev.isEnabled = false
//        binding.btnNext.isEnabled = false
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//    }
//}