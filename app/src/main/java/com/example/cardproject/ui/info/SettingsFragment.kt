package com.example.cardproject.ui.info


import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.cardproject.R
import com.example.cardproject.databinding.FragmentSettingsBinding
import com.example.cardproject.model.LearningMode
import com.example.cardproject.utils.DataExporter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    @Inject
    lateinit var dataExporter: DataExporter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupObservers()
        setupClickListeners()
        setupColorPicker()
//        loadPreferences()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isMLReady.collect { isReady ->
                updateAIStatus(isReady)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.logsCount.collect { count ->
                binding.dataCountText.text = count.toString()
            }
        }

//        viewLifecycleOwner.lifecycleScope.launch {
//            viewModel.defaultLearningMode.collect { mode ->
//                when (mode) {
//                    LearningMode.LONG_TERM -> binding.longTermMode.isChecked = true
//                    LearningMode.SHORT_TERM -> binding.shortTermMode.isChecked = true
//                }
//            }
//        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.minCardsForSession.collect { value ->
                binding.minCardsSeekBar.progress = value
                binding.minCardsValue.text = value.toString()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isFineTuning.collect { isRunning ->
                binding.fineTuneButton.isEnabled = !isRunning
                binding.fineTuneButton.text = if (isRunning) {
                    "Идёт дообучение..."
                } else {
                    "Запустить дообучение"
                }
                updateStatusDot(isRunning)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fineTuneStatus.collect { status ->
                binding.fineTuneStatusText.text = status
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fineTuneProgress.collect { progress ->
                val progressPercent = (progress * 100).toInt()
                binding.fineTuneProgressBar.progress = progressPercent
                binding.fineTuneProgressText.text = "Дообучение: $progressPercent%"

                if (progress > 0f && progress < 1f) {
                    binding.fineTuneProgressLayout.visibility = View.VISIBLE
                } else if (progress >= 1f) {
                    // Завершено — скрываем через 2 секунды
                    // (можно оставить видимым для информации)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.serverLogsCount.collect { count ->
                binding.serverLogsCountText.text = count.toString()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.hasNewModel.collect { hasNew ->
                if (hasNew) {
                    binding.fineTuneStatusText.text = "✅ Доступна новая модель!"
                }
            }
        }
    }

    private fun updateAIStatus(isReady: Boolean) {
        if (isReady) {
            binding.aiStatusText.text = "Активна"
            binding.aiStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_500))
//            binding.modelInfoText.text = "Модель загружена: forgetting_model.tflite"
        } else {
            binding.aiStatusText.text = "Не активна (SM-2)"
            binding.aiStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_500))
//            binding.modelInfoText.text = "Используется базовый алгоритм SM-2"
        }
    }
    /**
     * Обновление статусного кружка
     */
    private fun updateStatusDot(isRunning: Boolean) {
        val dot = binding.fineTuneStatusDot
        if (isRunning) {
            dot.setBackgroundResource(R.drawable.circle_orange)
        } else {
            dot.setBackgroundResource(R.drawable.circle_green)
        }
    }

    private fun setupClickListeners() {
        binding.exportDataButton.setOnClickListener {
            exportData()
        }

//        binding.longTermMode.setOnClickListener {
//            viewModel.setDefaultLearningMode(LearningMode.LONG_TERM)
//            showToast("Режим по умолчанию: Долговременный")
//        }
//
//        binding.shortTermMode.setOnClickListener {
//            viewModel.setDefaultLearningMode(LearningMode.SHORT_TERM)
//            showToast("Режим по умолчанию: Краткосрочный")
//        }
//
//        // Обработчик для поля ввода новых карточек
//        binding.newCardsPerDayInput.setOnEditorActionListener { _, _, _ ->
//            saveNewCardsPerDay()
//            true
//        }
//
//        binding.newCardsPerDayInput.setOnFocusChangeListener { _, hasFocus ->
//            if (!hasFocus) {
//                saveNewCardsPerDay()
//            }
//        }

        binding.minCardsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.minCardsValue.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = seekBar?.progress ?: 10
                viewModel.setMinCardsForSession(value)
                println("🔍 Сохранено в настройках: minCards=$value")
                Toast.makeText(requireContext(), "Минимум: $value карточек", Toast.LENGTH_SHORT).show()
            }
        })

        // Кнопка "Запустить дообучение"
        binding.fineTuneButton.setOnClickListener {
            if (viewModel.fineTuneStatus.value.contains("Сервер недоступен")) {
                showServerUnavailableDialog()
                return@setOnClickListener
            }

            // Показываем диалог подтверждения
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Дообучение AI модели")
                .setMessage("""
                    Будет выполнено:
                    
                    1. Отправка логов на сервер
                    2. Дообучение модели (1-3 минуты)
                    3. Скачивание и установка новой модели
                    
                    Требуется интернет-соединение.
                """.trimIndent())
                .setPositiveButton("Начать") { _, _ ->
                    viewModel.startFineTune()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        // Кнопка "Проверить обновления"
        binding.checkUpdatesButton.setOnClickListener {
            viewModel.refreshServerData()
        }
    }
    private fun showServerUnavailableDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Сервер недоступен")
            .setMessage("""
                Не удаётся подключиться к серверу дообучения.
                
                Проверьте:
                1. Интернет-соединение на устройстве
                2. Запущен ли сервер (локально или на Render)
                3. Правильно ли указан URL в RetrofitClient
            """.trimIndent())
            .setPositiveButton("Повторить") { _, _ ->
                viewModel.refreshServerData()
            }
            .setNegativeButton("OK", null)
            .show()
    }
//    private fun saveNewCardsPerDay() {
//        val text = binding.newCardsPerDayInput.text.toString()
//        val value = text.toIntOrNull()
//        if (value != null && value in 1..50) {
//            viewModel.setNewCardsPerDay(value)
//        } else {
//            binding.newCardsPerDayInput.setText(viewModel.newCardsPerDay.value.toString())
//            showToast("Введите число от 1 до 50")
//        }
//    }

//    private fun loadPreferences() {
//        binding.newCardsPerDayInput.setText(viewModel.newCardsPerDay.value.toString())
//    }

    private fun exportData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Проверяем, есть ли данные для экспорта
                if (viewModel.logsCount.value == 0) {
                    showNoDataDialog()
                    return@launch
                }

                // Показываем диалог подтверждения
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Экспорт данных")
                    .setMessage("Будет экспортировано ${viewModel.logsCount.value} записей. Продолжить?")
                    .setPositiveButton("Экспортировать") { _, _ ->
                        performExport()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()

            } catch (e: Exception) {
                showToast("Ошибка: ${e.message}")
            }
        }
    }

    private fun performExport() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val file = dataExporter.exportLogsToCsv()
                val intent = dataExporter.shareFile(file)

                // Показываем выбор приложения для отправки
                startActivity(Intent.createChooser(intent, "Экспорт данных для обучения AI"))

                showToast("Данные экспортированы успешно!")

            } catch (e: Exception) {
                showToast("Ошибка экспорта: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun showNoDataDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Нет данных")
            .setMessage("Сначала поучитесь, чтобы накопить данные для экспорта. Минимум 50 записей для обучения AI модели.")
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // В SettingsFragment
    private fun setupColorPicker() {
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val currentColor = prefs.getString("theme_color", "purple")

        // Подсвечиваем выбранный цвет
        highlightSelectedColor(currentColor)

        // Обработчики кликов
        binding.colorPurple.setOnClickListener { setThemeColor("purple") }
        binding.colorBlue.setOnClickListener { setThemeColor("blue") }
        binding.colorGreen.setOnClickListener { setThemeColor("green") }
        binding.colorOrange.setOnClickListener { setThemeColor("orange") }
        binding.colorPink.setOnClickListener { setThemeColor("pink") }
    }

    private fun setThemeColor(color: String) {
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putString("theme_color", color).apply()
        highlightSelectedColor(color)

        // Применяем тему
        applyTheme()
    }

    private fun highlightSelectedColor(color: String?) {
        val views = listOf(
            binding.colorPurple to "purple",
            binding.colorBlue to "blue",
            binding.colorGreen to "green",
            binding.colorOrange to "orange",
            binding.colorPink to "pink"
        )

        views.forEach { (view, id) ->
            view.setBackgroundResource(
                if (id == color) R.drawable.circle_selected else R.drawable.circle_green
            )
        }
    }

    private fun applyTheme() {
        requireActivity().recreate()
    }
}