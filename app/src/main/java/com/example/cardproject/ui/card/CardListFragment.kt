package com.example.cardproject.ui.card

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cardproject.R
import com.example.cardproject.databinding.FragmentCardListBinding
import com.example.cardproject.model.Card
import com.example.cardproject.ui.info.SettingsFragment
import com.example.cardproject.ui.learning.LearningFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CardListFragment : Fragment() {

    private var _binding: FragmentCardListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CardListViewModel by viewModels()
    private lateinit var adapter: CardAdapter

    private var currentCards: List<Card> = emptyList()
    private var deckId: Long = -1
    private var deckName: String = ""
    private var cardCount: Int = 0

    private var isSelectionMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deckId = arguments?.getLong("deckId", -1) ?: -1
        deckName = arguments?.getString("deckName", "Карточки") ?: "Карточки"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCardListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        println("🎯 CardListFragment создан: $deckName")

        setupToolbar()
        viewModel.setDeckId(deckId)
        setupRecyclerView()
        setupObservers()
        setupClickListeners()


    }

    private fun setupToolbar() {
        showNormalMode()

        // Кнопка назад
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun showNormalMode() {
        binding.toolbar.menu.clear()
        binding.toolbar.title = deckName

        // Восстанавливаем стандартную иконку навигации
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
        // Устанавливаем основное меню
        binding.toolbar.inflateMenu(R.menu.menu_card_list)

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_fullscreen -> {
                    openFullScreenMode()
                    true
                }
                R.id.menu_learn -> {
                    val minCards = getMinCardsForSession()
                    val readyCards = getReadyCardsCount()
                    println("🔍 menu_learn: minCards=$minCards, readyCards=$readyCards")
                    if (readyCards >= minCards && readyCards > 0) {
                        startLearning()
                    } else if (readyCards == 0) {
                        Toast.makeText(requireContext(), "Нет карточек для повторения", Toast.LENGTH_SHORT).show()
                    } else {
                        showNotEnoughCardsMessage(readyCards, minCards)
                    }
                    true
                }
                else -> false
            }
        }
    }

    // Метод для подсчёта готовых карточек
    private fun getReadyCardsCount(): Int {
        val now = System.currentTimeMillis()
        return currentCards.count { card ->  // ← используем currentCards
            card.nextReview == null || card.nextReview!! <= now
        }
    }
    private fun getMinCardsForSession(): Int {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        val value = prefs.getInt("min_cards_for_session", 10)
        println("🔍 getMinCardsForSession = $value")
        return value
    }
    private fun showSelectionMode(selectedCount: Int) {
        binding.toolbar.menu.clear()
        binding.toolbar.title = "Выбрано: $selectedCount"

        // Устанавливаем навигационную иконку для отмены выделения
        binding.toolbar.setNavigationIcon(R.drawable.ic_close_24dp)
        binding.toolbar.setNavigationOnClickListener {
            cancelSelection()
        }

        // Устанавливаем меню для действий с выделенными карточками
        binding.toolbar.inflateMenu(R.menu.menu_card_selection)

        // Настраиваем видимость пунктов меню
        val editMenuItem = binding.toolbar.menu.findItem(R.id.menu_edit)
        editMenuItem?.isVisible = selectedCount == 1

        val deleteMenuItem = binding.toolbar.menu.findItem(R.id.menu_delete)
        deleteMenuItem?.isVisible = selectedCount > 0

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_edit -> {
                    editSelectedCard()
                    true
                }
                R.id.menu_delete -> {
                    showDeleteConfirmationDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun editSelectedCard() {
        val selectedCards = adapter.getSelectedCards()
        if (selectedCards.size == 1) {
            val cardToEdit = selectedCards.first()
            showEditCardDialog(cardToEdit)
        }
    }

    private fun showEditCardDialog(card: Card) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Редактировать карточку")

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }

        val frontInput = EditText(requireContext()).apply {
            setText(card.front)
            hint = "Передняя сторона (вопрос)"
            setSingleLine(false)
            minLines = 2
            maxLines = 4
        }

        val backInput = EditText(requireContext()).apply {
            setText(card.back)
            hint = "Задняя сторона (ответ)"
            setSingleLine(false)
            minLines = 2
            maxLines = 4
        }

        layout.addView(frontInput)
        layout.addView(backInput)
        builder.setView(layout)

        builder.setPositiveButton("Сохранить") { _, _ ->
            val front = frontInput.text.toString().trim()
            val back = backInput.text.toString().trim()

            if (front.isNotBlank() && back.isNotBlank()) {
                val updatedCard = card.copy(front = front, back = back)
                viewModel.updateCard(updatedCard)
                Toast.makeText(requireContext(), "Карточка обновлена!", Toast.LENGTH_SHORT).show()
                cancelSelection()
            } else {
                Toast.makeText(requireContext(), "Заполните обе стороны карточки", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Отмена") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    private fun cancelSelection() {
        adapter.clearSelection()
        showNormalMode()
        isSelectionMode = false
    }

    private fun showDeleteConfirmationDialog() {
        val selectedCards = adapter.getSelectedCards()
        if (selectedCards.isEmpty()) return

        val cardPreviews = selectedCards.take(3).joinToString("\n") { "• ${it.front.take(50)}..." }
        val moreText = if (selectedCards.size > 3) "\n... и ещё ${selectedCards.size - 3}" else ""

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить выбранные карточки?")
            .setMessage("Будут удалены следующие карточки:\n\n$cardPreviews$moreText\n\nЭто действие нельзя отменить.")
            .setPositiveButton("Удалить") { dialog, _ ->
                deleteSelectedCards()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteSelectedCards() {
        val selectedCards = adapter.getSelectedCards()
        selectedCards.forEach { card ->
            viewModel.deleteCard(card)
        }

        Toast.makeText(
            requireContext(),
            "Удалено карточек: ${selectedCards.size}",
            Toast.LENGTH_SHORT
        ).show()

        cancelSelection()
    }

    private fun showNotEnoughCardsMessage() {
        AlertDialog.Builder(requireContext())
            .setTitle("Недостаточно карточек")
            .setMessage("Для режима интервального повторения необходимо минимум 10 карточек.\n\nСейчас в колоде: $cardCount карточек.\n\nИспользуйте полноэкранный режим для просмотра всех карточек.")
            .setPositiveButton("Полноэкранный режим") { _, _ ->
                openFullScreenMode()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startLearning() {
        val minCards = getMinCardsForSession()
        val readyCards = getReadyCardsCount()

        if (readyCards == 0) {
            Toast.makeText(requireContext(), "Нет карточек для повторения", Toast.LENGTH_SHORT).show()
            return
        }

        if (readyCards < minCards) {
            showNotEnoughCardsMessage(readyCards, minCards)
            return
        }
        val learningFragment = LearningFragment().apply {
            arguments = Bundle().apply {
                putLong("deckId", deckId)
                putSerializable("learningMode", viewModel.getLearningMode())
            }
        }

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, learningFragment)
            .addToBackStack("card_list")
            .commit()
    }
    private fun showNotEnoughCardsMessage(ready: Int, required: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Недостаточно готовых карточек")
            .setMessage("""
            Готово к повторению: $ready
            Требуется в настройках: $required
            
            Вы можете:
            • Создать новые карточки
            • Уменьшить порог в настройках
            • Использовать полноэкранный режим для просмотра всех карточек
        """.trimIndent())
            .setPositiveButton("Полноэкранный режим") { _, _ ->
                openFullScreenMode()
            }
            .setNegativeButton("Настройки") { _, _ ->
                val settingsFragment = SettingsFragment()
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, settingsFragment)
                    .addToBackStack("settings")
                    .commit()
            }
            .setNeutralButton("Отмена", null)
            .show()
    }
    private fun setupRecyclerView() {
        adapter = CardAdapter(
            onCardClick = { card ->
                if (!adapter.isInSelectionMode()) {
                    // Обычный клик - можно добавить функциональность просмотра
                }
            },
            onSelectionChange = { selectedCount ->
                isSelectionMode = selectedCount > 0
                if (isSelectionMode) {
                    showSelectionMode(selectedCount)
                } else {
                    showNormalMode()
                }
            }
        )

        binding.cardsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.cardsRecyclerView.adapter = adapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.cards.collectLatest { cards ->
                println("🎴 Получены карточки: ${cards.size} шт.")
                currentCards = cards  // ← 🔥 ВАЖНО: сохраняем карточки
                cardCount = cards.size
                adapter.submitList(cards)
                updateMenuVisibility()
                if (cards.isEmpty()) {
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.cardsRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyStateText.visibility = View.GONE
                    binding.cardsRecyclerView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateMenuVisibility() {
        val learnMenuItem = binding.toolbar.menu.findItem(R.id.menu_learn)
        val minCards = getMinCardsForSession()

        // 🔥 Считаем готовые карточки прямо из currentCards, а не из adapter
        val now = System.currentTimeMillis()
        val readyCards = currentCards.count { card ->
            card.nextReview == null || card.nextReview!! <= now
        }

        // Показываем пункт меню, если есть хотя бы 1 готовая карточка
        learnMenuItem?.isVisible = readyCards > 0

        if (readyCards == 0) {
            learnMenuItem?.title = "Нет готовых карточек"
        } else if (readyCards < minCards) {
            learnMenuItem?.title = "Готово $readyCards/$minCards карточек"
        } else {
            learnMenuItem?.title = "Интервальное повторение"
        }
    }

    private fun setupClickListeners() {
        binding.fabCreateCard.setOnClickListener {
            // Снимаем выделение перед созданием карточки
            if (adapter.isInSelectionMode()) {
                adapter.clearSelection()
            }
            showCreateCardDialog()
        }
    }

    private fun showCreateCardDialog() {
        println("🔄 Открываем диалог создания карточки")

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Создать карточку")

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }

        val frontInput = EditText(requireContext()).apply {
            hint = "Передняя сторона (вопрос)"
            setSingleLine(false)
            minLines = 2
            maxLines = 4
        }

        val backInput = EditText(requireContext()).apply {
            hint = "Задняя сторона (ответ)"
            setSingleLine(false)
            minLines = 2
            maxLines = 4
        }

        val typeSpinner = Spinner(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }

            val adapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.question_types,
                android.R.layout.simple_spinner_dropdown_item
            )
            this.adapter = adapter
        }

        var selectedQuestionType = "FACT"
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedQuestionType = when (position) {
                    0 -> "FACT"
                    1 -> "DEFINITION"
                    2 -> "PROOF"
                    else -> "FACT"
                }
                Toast.makeText(requireContext(), "Выбран тип: $selectedQuestionType", Toast.LENGTH_SHORT).show()
                println("📝 В диалоге выбран тип: $selectedQuestionType")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        layout.addView(frontInput)
        layout.addView(backInput)
        layout.addView(typeSpinner)
        builder.setView(layout)

        builder.setPositiveButton("Создать") { _, _ ->
            val front = frontInput.text.toString().trim()
            val back = backInput.text.toString().trim()

            println("📝 КНОПКА СОЗДАТЬ: front='$front', back='$back', type='$selectedQuestionType'")

            if (front.isNotBlank() && back.isNotBlank()) {
                if (deckId != -1L) {
                    viewModel.createCard(front, back, selectedQuestionType)
                    Toast.makeText(requireContext(), "Карточка создана!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Ошибка: колода не найдена", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Заполните обе стороны карточки", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Отмена", null)

        val dialog = builder.create()
        dialog.show()
    }

    private fun openFullScreenMode() {
        println("🔄 Открываем полноэкранный режим для колоды: $deckName")

        val fullScreenFragment = FullScreenCardFragment().apply {
            arguments = Bundle().apply {
                putLong("deckId", deckId)
                putString("deckName", deckName)
            }
        }

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fullScreenFragment)
            .addToBackStack("card_list")
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}