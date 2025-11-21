package com.example.cardproject.ui.deck

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cardproject.R
import com.example.cardproject.databinding.FragmentDeckListBinding
import com.example.cardproject.model.Deck
import com.example.cardproject.ui.card.CardListFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import android.text.TextWatcher
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.replace
import com.example.cardproject.model.LearningMode
import com.example.cardproject.model.NavigationSource
import com.example.cardproject.ui.info.LearningModesInfoFragment
import com.example.cardproject.ui.notes.NoteListFragment
import com.example.cardproject.ui.stats.StatsFragment
import com.example.cardproject.utils.TagColorHelper
import com.google.android.material.chip.Chip

class DeckListFragment : Fragment() {

    private var _binding: FragmentDeckListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeckListViewModel by viewModels()
    private lateinit var adapter: DeckAdapter

    private var isSelectionMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true) // Включаем обработку меню
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeckListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        setupSearchView()
        setupTagFiltering()
    }

    private fun setupToolbar() {
        // Начальная настройка - обычный режим
        showNormalMode()
    }


    private fun showNormalMode() {
        // Очищаем меню и устанавливаем обычный режим
        binding.mainToolbar.menu.clear()
        binding.mainToolbar.title = "Мои колоды"
        binding.mainToolbar.navigationIcon = null

        // Устанавливаем основное меню
        binding.mainToolbar.inflateMenu(R.menu.menu_main)

        binding.mainToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_more -> {
                    showOverflowMenu()
                    true
                }
                else -> false
            }
        }
    }

    private fun showSelectionMode(selectedCount: Int) {
        // Очищаем меню и устанавливаем режим выделения
        binding.mainToolbar.menu.clear()
        binding.mainToolbar.title = "Выбрано: $selectedCount"

        // Устанавливаем навигационную иконку для отмены выделения
        binding.mainToolbar.setNavigationIcon(R.drawable.ic_close_24dp)
        binding.mainToolbar.setNavigationOnClickListener {
            cancelSelection()
        }

        // Устанавливаем меню для действий с выделенными колодами
        binding.mainToolbar.inflateMenu(R.menu.menu_deck_selection)

        // Настраиваем видимость пунктов меню
        val editMenuItem = binding.mainToolbar.menu.findItem(R.id.menu_edit)
        editMenuItem?.isVisible = selectedCount == 1

        val deleteMenuItem = binding.mainToolbar.menu.findItem(R.id.menu_delete)
        deleteMenuItem?.isVisible = selectedCount > 0

        binding.mainToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_edit -> {
                    editSelectedDeck()
                    true
                }
                R.id.menu_delete -> {
                    showDeleteConfirmationDialog()
                    true
                }
//                R.id.menu_cancel -> {
//                    cancelSelection()
//                    true
//                }
                else -> false
            }
        }
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(requireContext(), binding.mainToolbar.findViewById(R.id.menu_more))
        popup.menuInflater.inflate(R.menu.menu_overflow, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_stats -> {
                    openStats()
                    true
                }
                R.id.menu_learning_info -> { // ДОБАВЬТЕ этот case
                    openLearningModesInfo()
                    true
                }
                R.id.menu_notes -> {
                    openNotes()
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun openNotes(){
        val noteListFragment = NoteListFragment()

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, noteListFragment)
            .addToBackStack("deck_list")
            .commit()
    }

    private fun openLearningModesInfo() {
        val learningModesInfoFragment = LearningModesInfoFragment()

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, learningModesInfoFragment)
            .addToBackStack("learning_modes_info")
            .commit()
    }

    private fun editSelectedDeck() {
        val selectedDecks = adapter.getSelectedDecks()
        if (selectedDecks.size == 1) {
            val deckToEdit = selectedDecks.first()
            showEditDeckDialog(deckToEdit.deck, deckToEdit.tags)
        }
    }

    private fun showEditDeckDialog(deck: Deck, currentTags: List<String>) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Редактировать колоду")

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }

        val nameInput = EditText(requireContext()).apply {
            setText(deck.name)
            hint = "Название колоды"
            setSingleLine(true)
        }

        val descriptionInput = EditText(requireContext()).apply {
            setText(deck.description)
            hint = "Описание (необязательно)"
            setSingleLine(true)
        }

        val tagsInput = EditText(requireContext()).apply {
            setText(currentTags.joinToString(", "))
            hint = "Теги (через запятую)"
            setSingleLine(true)
        }

        // Добавляем выбор цвета обложки
        val colorSelectionLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 10)
        }

        val colorTitle = androidx.appcompat.widget.AppCompatTextView(requireContext()).apply {
            text = "Цвет обложки:"
            setTextAppearance(requireContext(), androidx.appcompat.R.style.TextAppearance_AppCompat_Body1)
            setPadding(0, 0, 0, 10)
        }

        val colorsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ПРЕДПРОСМОТР ЦВЕТА - ПРОСТОЙ VIEW БЕЗ BACKGROUND
        val colorPreview = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(60, 60).apply {
                setMargins(20, 0, 0, 0)
            }
            // Устанавливаем текущий цвет колоды - ТОЛЬКО setBackgroundColor
            val currentColor = deck.coverColor ?: "#FFFFFF"
            println("🎯 Устанавливаем цвет предпросмотра: $currentColor для колоды '${deck.name}'")
            setBackgroundColor(Color.parseColor(currentColor))
//            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_color_preview)

        }

        // Создаем контейнер для обводки
//        val previewContainer = FrameLayout(requireContext()).apply {
//            layoutParams = LinearLayout.LayoutParams(64, 64).apply {
//                setMargins(20, 0, 0, 0)
//            }
//            setBackgroundColor(Color.GRAY) // Цвет обводки
//            addView(colorPreview)
//        }

        val previewLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 0)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val previewText = androidx.appcompat.widget.AppCompatTextView(requireContext()).apply {
            text = "Выбранный цвет:"
            setTextAppearance(requireContext(), androidx.appcompat.R.style.TextAppearance_AppCompat_Body2)
        }

        previewLayout.addView(previewText)
        previewLayout.addView(colorPreview)

        // Доступные цвета
        val availableColors = listOf(
            "#FFFFFF", "#CCAEA9", "#47021E", "#054564",
            "#FDD835", "#1C707C", "#E9241F", "#EF096D",
            "#E77805", "#5003EB", "#1D90DB", "#00FF37"
        )

        // ТЕПЕРЬ ПРАВИЛЬНО УСТАНАВЛИВАЕМ ВЫБРАННЫЙ ЦВЕТ ИЗ КОЛОДЫ
        var selectedColor = deck.coverColor ?: availableColors[0]
        println("🎨 Текущий цвет колоды '${deck.name}': $selectedColor")

        // Храним ссылки на все цветовые контейнеры для управления выделением
        val colorContainers = mutableListOf<FrameLayout>()

        // Создаем 2 ряда по 6 цветов
        for (row in 0 until 2) {
            val rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            for (col in 0 until 6) {
                val colorIndex = row * 6 + col
                if (colorIndex < availableColors.size) {
                    val colorHex = availableColors[colorIndex]

                    val colorContainer = FrameLayout(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 0).apply {
                            weight = 1f
                            setMargins(4, 4, 4, 4)
                        }
                        setBackgroundColor(Color.parseColor(colorHex))
                        background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_color_square)
                        setOnClickListener {
                            // Сбрасываем выделение у всех кнопок
                            colorContainers.forEach { container ->
                                container.alpha = 0.7f
                            }

                            // Выделяем выбранную кнопку
                            alpha = 1.0f
                            selectedColor = colorHex

                            // Обновляем предпросмотр
                            colorPreview.setBackgroundColor(Color.parseColor(colorHex))
                            println("🎨 Выбран новый цвет: $colorHex")
                        }

                        // Устанавливаем начальную прозрачность - выделяем текущий цвет колоды
                        alpha = if (colorHex == selectedColor) 1.0f else 0.7f

                        // Если это текущий цвет колоды, выводим в лог
                        if (colorHex == selectedColor) {
                            println("✅ Выделен цвет колоды: $colorHex")
                        }
                    }

                    // Добавляем в список для управления
                    colorContainers.add(colorContainer)

                    val colorView = View(requireContext()).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        ).apply {
                            setMargins(2, 2, 2, 2) // ОТСТУПЫ КАК В СОЗДАНИИ
                        }
                        setBackgroundColor(Color.parseColor(colorHex))
                    }

                    val backgroundDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.bg_color_square)
                    colorContainer.background = backgroundDrawable
                    colorContainer.addView(colorView)


                    colorContainer.post {
                        val width = colorContainer.measuredWidth
                        colorContainer.layoutParams.height = width
                        colorContainer.requestLayout()
                    }
                    rowLayout.addView(colorContainer)
                }
            }
            colorsContainer.addView(rowLayout)
        }

        // Кнопка случайного цвета
        val randomColorButton = androidx.appcompat.widget.AppCompatButton(requireContext()).apply {
            text = "Случайный цвет"
            setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_500))
            setBackgroundColor(Color.TRANSPARENT)

            setOnClickListener {
                val randomColor = availableColors.random()
                selectedColor = randomColor

                // Находим и "нажимаем" соответствующую кнопку
                colorContainers.forEachIndexed { index, container ->
                    val colorHex = availableColors[index]
                    if (colorHex == randomColor) {
                        container.performClick()
                        return@setOnClickListener
                    }
                }
            }
        }

        colorSelectionLayout.addView(colorTitle)
        colorSelectionLayout.addView(colorsContainer)
        colorSelectionLayout.addView(randomColorButton)
        colorSelectionLayout.addView(previewLayout)

        layout.addView(nameInput)
        layout.addView(descriptionInput)
        layout.addView(tagsInput)
        layout.addView(colorSelectionLayout)

        builder.setView(layout)

        builder.setPositiveButton("Сохранить") { _, _ ->
            val name = nameInput.text.toString().trim()
            val description = descriptionInput.text.toString().trim()
            val tags = tagsInput.text.toString().trim()

            val tagList = if (tags.isNotEmpty()) {
                tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            if (name.isNotBlank()) {
                val updatedDeck = deck.copy(
                    name = name,
                    description = description,
                    coverColor = selectedColor
                )
                viewModel.updateDeck(updatedDeck, tagList)

                Toast.makeText(requireContext(), "Колода обновлена!", Toast.LENGTH_SHORT).show()
                cancelSelection()
            } else {
                Toast.makeText(requireContext(), "Введите название колоды", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Отмена") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()

        // После показа диалога убедимся, что правильный цвет выделен
        dialog.setOnShowListener {
            // Находим контейнер с текущим выбранным цветом и выделяем его
            colorContainers.forEachIndexed { index, container ->
                val colorHex = availableColors[index]
                container.alpha = if (colorHex == selectedColor) 1.0f else 0.7f
            }
            println("🎨 Диалог показан, выбранный цвет: $selectedColor")
            println("🎨 Цвет предпросмотра должен быть: ${colorPreview.background}")
        }
    }
    private fun createColoredSquareWithBorder(color: Int): android.graphics.drawable.Drawable {
        val shape = android.graphics.drawable.GradientDrawable()
        shape.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        shape.setColor(color)
        shape.setStroke(4, Color.GRAY) // Толщина и цвет обводки
        shape.cornerRadius = 8f // Закругленные углы
        return shape
    }

    private fun cancelSelection() {
        adapter.clearSelection()
        showNormalMode()
        isSelectionMode = false
    }

    private fun openStats() {
        val statsFragment = StatsFragment().apply {
            arguments = Bundle().apply {
                putSerializable("navigationSource", NavigationSource.FROM_DECKS)
            }
        }

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, statsFragment)
            .addToBackStack("stats")
            .commit()
    }

    private fun showDeleteConfirmationDialog() {
        val selectedDecks = adapter.getSelectedDecks()
        if (selectedDecks.isEmpty()) return

        val deckNames = selectedDecks.joinToString("\n") { "• ${it.deck.name}" }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить выбранные колоды?")
            .setMessage("Будут удалены следующие колоды и все их карточки:\n\n$deckNames\n\nЭто действие нельзя отменить.")
            .setPositiveButton("Удалить") { dialog, _ ->
                deleteSelectedDecks()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteSelectedDecks() {
        val selectedDecks = adapter.getSelectedDecks()
        selectedDecks.forEach { deckWithTags ->
            viewModel.deleteDeck(deckWithTags.deck)
        }

        Toast.makeText(
            requireContext(),
            "Удалено колод: ${selectedDecks.size}",
            Toast.LENGTH_SHORT
        ).show()

        cancelSelection()
    }

    private fun setupTagFiltering() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.availableTags.collect { availableTags ->
                updateTagsChips(availableTags)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedTags.collect { selectedTags ->
                updateChipsAppearance(selectedTags)
            }
        }
    }

    private fun updateTagsChips(availableTags: List<String>) {
        binding.tagsChipGroup.removeAllViews()

        availableTags.forEachIndexed { index, tagName ->
            val chip = Chip(requireContext()).apply {
                text = tagName
                setTag(tagName)

                val strokeColor = TagColorHelper.getTagStrokeColor(requireContext(), index)
                setChipStrokeColor(ColorStateList.valueOf(strokeColor))
                chipStrokeWidth = resources.getDimension(R.dimen.chip_stroke_width)

                setChipBackgroundColorResource(R.color.tag_default_background)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.tag_default_text))

                isCloseIconVisible = false

                setOnClickListener {
                    viewModel.toggleTag(tagName)
                }
            }
            binding.tagsChipGroup.addView(chip)
        }
        updateChipsAppearance(viewModel.selectedTags.value)
    }

    private fun updateChipsAppearance(selectedTags: Set<String>) {
        for (i in 0 until binding.tagsChipGroup.childCount) {
            val chip = binding.tagsChipGroup.getChildAt(i) as Chip
            val chipTag = chip.tag as? String ?: continue

            val isSelected = selectedTags.contains(chipTag)

            if (isSelected) {
                chip.setChipBackgroundColorResource(R.color.tag_selected_background)
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.tag_selected_text))
                chip.isCloseIconVisible = true
                chip.setChipStrokeColorResource(R.color.tag_selected_stroke)
            } else {
                chip.setChipBackgroundColorResource(R.color.tag_default_background)
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.tag_default_text))
                chip.isCloseIconVisible = false

                val strokeColor = TagColorHelper.getTagStrokeColor(requireContext(), i)
                chip.setChipStrokeColor(ColorStateList.valueOf(strokeColor))
            }
        }
    }

    private fun setupSearchView() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                println("🔍 Поисковый запрос: '$query'")
                viewModel.setSearchQuery(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        binding.searchInputLayout.setEndIconOnClickListener {
            binding.searchInput.setText("")
            viewModel.clearSearch()
            hideKeyboard()
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }

    private fun setupRecyclerView() {
        adapter = DeckAdapter(
            onDeckClick = { deckWithTags ->
                if (!adapter.isInSelectionMode()) {
                    navigateToCardList(deckWithTags.deck)
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

        val gridLayoutManager = GridLayoutManager(requireContext(), 2)
        binding.decksRecyclerView.layoutManager = gridLayoutManager
        binding.decksRecyclerView.adapter = adapter

        val spacingInPixels = resources.getDimensionPixelSize(R.dimen.grid_spacing)
        binding.decksRecyclerView.addItemDecoration(GridSpacingItemDecoration(2, spacingInPixels, true))
    }

    private fun navigateToCardList(deck: Deck) {
        println("🔄 navigateToCardList: открываем ${deck.name} (ID: ${deck.id})")

        val cardListFragment = CardListFragment().apply {
            arguments = Bundle().apply {
                putLong("deckId", deck.id)
                putString("deckName", deck.name)
            }
        }

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, cardListFragment)
            .addToBackStack("deck_list")
            .commit()

        println("✅ Навигация выполнена успешно")
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.decks.collectLatest { decks ->
                println("🔄 Fragment получил ${decks.size} колод для отображения")
                decks.forEach {
                    println("   - '${it.deck.name}' (ID: ${it.deck.id}), теги: ${it.tags}")
                }
                adapter.submitList(decks)
            }
        }
    }

    private fun setupClickListeners() {
        binding.fabCreateDeck.setOnClickListener {
            // СНИМАЕМ ВЫДЕЛЕНИЕ ПЕРЕД СОЗДАНИЕМ КОЛОДЫ
            if (adapter.isInSelectionMode()) {
                adapter.clearSelection()
            }
            showCreateDeckDialog()
        }
    }

    private fun showCreateDeckDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Создать новую колоду")

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }

        val nameInput = EditText(requireContext()).apply {
            hint = "Название колоды"
            setSingleLine(true)
        }

        val descriptionInput = EditText(requireContext()).apply {
            hint = "Описание (необязательно)"
            setSingleLine(true)
        }

        val tagsInput = EditText(requireContext()).apply {
            hint = "Теги (через запятую)"
            setSingleLine(true)
        }

        // ОБНОВЛЕНО: Выбор режима обучения с динамическими пояснениями
        val learningModeLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 10)
        }

        val learningModeTitle = androidx.appcompat.widget.AppCompatTextView(requireContext()).apply {
            text = "Режим обучения:"
            setTextAppearance(requireContext(), androidx.appcompat.R.style.TextAppearance_AppCompat_Body1)
            setPadding(0, 0, 0, 10)
        }

        val learningModeGroup = RadioGroup(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ОБНОВЛЕНО: Научно обоснованные названия
        val fundamentalRadio = RadioButton(requireContext()).apply {
            text = "📚 Фундаментальное обучение"
            id = View.generateViewId()
            isChecked = true // По умолчанию выбран
        }

        val eventPrepRadio = RadioButton(requireContext()).apply {
            text = "🎯 Подготовка к событию"
            id = View.generateViewId()
        }

        learningModeGroup.addView(fundamentalRadio)
        learningModeGroup.addView(eventPrepRadio)

        // ОБНОВЛЕНО: Динамическое пояснение
        val modeExplanation = androidx.appcompat.widget.AppCompatTextView(requireContext()).apply {
            text = "📚 Оптимально для экзаменов и долгосрочного запоминания (80-90% эффективности)"
            setTextAppearance(requireContext(), androidx.appcompat.R.style.TextAppearance_AppCompat_Caption)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_500))
            setPadding(0, 8, 0, 0)
        }

        // ОБНОВЛЕНО: Обработчики выбора радиокнопок
        fundamentalRadio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                modeExplanation.text = "📚 Оптимально для экзаменов и долгосрочного запоминания (80-90% эффективности)"
            }
        }

        eventPrepRadio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                modeExplanation.text = "🎯 Для срочной подготовки к зачетам и мероприятиям (40-60% эффективности)"
            }
        }

        learningModeLayout.addView(learningModeTitle)
        learningModeLayout.addView(learningModeGroup)
        learningModeLayout.addView(modeExplanation)

        val colorSelectionLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 10)
        }

        val colorTitle = androidx.appcompat.widget.AppCompatTextView(requireContext()).apply {
            text = "Цвет обложки:"
            setTextAppearance(requireContext(), androidx.appcompat.R.style.TextAppearance_AppCompat_Body1)
            setPadding(0, 0, 0, 10)
        }

        val colorsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        val colorPreview = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(60, 60).apply {
                setMargins(20, 0, 0, 0)
            }
            setBackgroundColor(Color.parseColor("#FFFFFF"))
        }

        val previewLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 0)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val previewText = androidx.appcompat.widget.AppCompatTextView(requireContext()).apply {
            text = "Выбранный цвет:"
            setTextAppearance(requireContext(), androidx.appcompat.R.style.TextAppearance_AppCompat_Body2)
        }

        previewLayout.addView(previewText)
        previewLayout.addView(colorPreview)

        val availableColors = listOf(
            "#FFFFFF", "#CCAEA9", "#47021E", "#054564",
            "#FDD835", "#1C707C", "#E9241F", "#EF096D",
            "#E77805", "#5003EB", "#1D90DB", "#00FF37"
        )
        var selectedColor = availableColors[0]

        for (row in 0 until 2) {
            val rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            for (col in 0 until 6) {
                val colorIndex = row * 6 + col
                if (colorIndex < availableColors.size) {
                    val colorHex = availableColors[colorIndex]

                    val colorContainer = FrameLayout(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 0).apply {
                            weight = 1f
                            setMargins(4, 4, 4, 4)
                        }
                        setBackgroundColor(Color.parseColor(colorHex))
                        background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_color_square)

                        setOnClickListener {
                            for (i in 0 until colorsContainer.childCount) {
                                val rowLayout = colorsContainer.getChildAt(i) as LinearLayout
                                for (j in 0 until rowLayout.childCount) {
                                    val child = rowLayout.getChildAt(j)
                                    child.alpha = 0.7f
                                }
                            }

                            alpha = 1.0f
                            selectedColor = colorHex
                            colorPreview.setBackgroundColor(Color.parseColor(colorHex))
                        }
                        alpha = if (colorIndex == 0) 1.0f else 0.7f
                    }

                    val colorView = View(requireContext()).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        ).apply {
                            setMargins(2, 2, 2, 2)
                        }
                        setBackgroundColor(Color.parseColor(colorHex))
                    }

                    val backgroundDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.bg_color_square)
                    colorContainer.background = backgroundDrawable
                    colorContainer.addView(colorView)

                    colorContainer.post {
                        val width = colorContainer.measuredWidth
                        colorContainer.layoutParams.height = width
                        colorContainer.requestLayout()
                    }
                    rowLayout.addView(colorContainer)
                }
            }
            colorsContainer.addView(rowLayout)
        }

        val randomColorButton = androidx.appcompat.widget.AppCompatButton(requireContext()).apply {
            text = "Случайный цвет"
            setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_500))
            setBackgroundColor(Color.TRANSPARENT)

            setOnClickListener {
                val randomColor = availableColors.random()
                selectedColor = randomColor

                for (i in 0 until colorsContainer.childCount) {
                    val rowLayout = colorsContainer.getChildAt(i) as LinearLayout
                    for (j in 0 until rowLayout.childCount) {
                        val child = rowLayout.getChildAt(j)
                        val buttonColorIndex = i * 6 + j
                        if (buttonColorIndex < availableColors.size) {
                            val buttonColor = availableColors[buttonColorIndex]
                            if (buttonColor == randomColor) {
                                child.performClick()
                                break
                            }
                        }
                    }
                }
            }
        }

        colorSelectionLayout.addView(colorTitle)
        colorSelectionLayout.addView(colorsContainer)
        colorSelectionLayout.addView(randomColorButton)
        colorSelectionLayout.addView(previewLayout)

        // ДОБАВЛЯЕМ ВСЕ ЭЛЕМЕНТЫ В ОСНОВНОЙ LAYOUT
        layout.addView(nameInput)
        layout.addView(descriptionInput)
        layout.addView(tagsInput)
        layout.addView(learningModeLayout) // ОБНОВЛЕНО: режим обучения с динамическими пояснениями
        layout.addView(colorSelectionLayout)

        builder.setView(layout)

        builder.setPositiveButton("Создать") { _, _ ->
            val name = nameInput.text.toString().trim()
            val description = descriptionInput.text.toString().trim()
            val tags = tagsInput.text.toString().trim()

            // ОБНОВЛЕНО: Определяем выбранный режим обучения
            val learningMode = when (learningModeGroup.checkedRadioButtonId) {
                fundamentalRadio.id -> LearningMode.LONG_TERM  // Сохраняем обратную совместимость
                eventPrepRadio.id -> LearningMode.SHORT_TERM   // Сохраняем обратную совместимость
                else -> LearningMode.LONG_TERM
            }

            val tagList = if (tags.isNotEmpty()) {
                tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            if (name.isNotBlank()) {
                println("🎨 Создаем колоду '$name' с цветом: $selectedColor, режим: $learningMode")
                viewModel.createDeck(name, description, tagList, selectedColor, learningMode)

                binding.root.postDelayed({
                    viewLifecycleOwner.lifecycleScope.launch {
                        println("🔄 Принудительное обновление списка после создания колоды")
                    }
                }, 1000)

                Toast.makeText(requireContext(), "Колода создана!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Введите название колоды", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    override fun onResume() {
        super.onResume()
        println("🔍 Fragment onResume - обновляем список колод")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}