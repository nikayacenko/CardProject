package com.example.cardproject.ui.info


import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
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
        loadPreferences()
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.defaultLearningMode.collect { mode ->
                when (mode) {
                    LearningMode.LONG_TERM -> binding.longTermMode.isChecked = true
                    LearningMode.SHORT_TERM -> binding.shortTermMode.isChecked = true
                }
            }
        }
    }

    private fun updateAIStatus(isReady: Boolean) {
        if (isReady) {
            binding.aiStatusText.text = "✅ Активна"
            binding.aiStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_500))
            binding.modelInfoText.text = "Модель загружена: forgetting_model.tflite"
        } else {
            binding.aiStatusText.text = "❌ Не активна (SM-2)"
            binding.aiStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_500))
            binding.modelInfoText.text = "Используется базовый алгоритм SM-2"
        }
    }

    private fun setupClickListeners() {
        binding.exportDataButton.setOnClickListener {
            exportData()
        }

        binding.longTermMode.setOnClickListener {
            viewModel.setDefaultLearningMode(LearningMode.LONG_TERM)
            showToast("Режим по умолчанию: Долговременный")
        }

        binding.shortTermMode.setOnClickListener {
            viewModel.setDefaultLearningMode(LearningMode.SHORT_TERM)
            showToast("Режим по умолчанию: Краткосрочный")
        }

        // Обработчик для поля ввода новых карточек
        binding.newCardsPerDayInput.setOnEditorActionListener { _, _, _ ->
            saveNewCardsPerDay()
            true
        }

        binding.newCardsPerDayInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveNewCardsPerDay()
            }
        }
    }

    private fun saveNewCardsPerDay() {
        val text = binding.newCardsPerDayInput.text.toString()
        val value = text.toIntOrNull()
        if (value != null && value in 1..50) {
            viewModel.setNewCardsPerDay(value)
        } else {
            binding.newCardsPerDayInput.setText(viewModel.newCardsPerDay.value.toString())
            showToast("Введите число от 1 до 50")
        }
    }

    private fun loadPreferences() {
        binding.newCardsPerDayInput.setText(viewModel.newCardsPerDay.value.toString())
    }

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
}