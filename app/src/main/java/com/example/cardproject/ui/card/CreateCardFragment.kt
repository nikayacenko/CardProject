package com.example.cardproject.ui.card

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.cardproject.databinding.FragmentCreateCardBinding
import com.example.cardproject.model.QuestionType
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreateCardFragment : Fragment() {

    private lateinit var binding: FragmentCreateCardBinding
    private val viewModel: CardListViewModel by viewModels()
    private var selectedQuestionType: QuestionType = QuestionType.FACT

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCreateCardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val deckId = arguments?.getLong("deckId") ?: run {
            Toast.makeText(requireContext(), "Ошибка: колода не найдена", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.setDeckId(deckId)

        setupQuestionTypeSpinner()
        setupButtons()
    }

    private fun setupQuestionTypeSpinner() {
        // Адаптер уже установлен через android:entries в XML
        binding.questionTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedQuestionType = when (position) {
                    0 -> QuestionType.FACT
                    1 -> QuestionType.DEFINITION
                    2 -> QuestionType.PROOF
                    else -> QuestionType.FACT
                }
                println("📝 Выбран тип вопроса: ${selectedQuestionType.name}")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Ничего не делаем
            }
        }
    }

    private fun setupButtons() {
        binding.saveButton.setOnClickListener {
            val question = binding.questionEditText.text.toString().trim()
            val answer = binding.answerEditText.text.toString().trim()

            if (question.isNotBlank() && answer.isNotBlank()) {
                println("💾 Создание карточки: тип=${selectedQuestionType.name}")
                viewModel.createCard(question, answer, selectedQuestionType.name)
                Toast.makeText(requireContext(), "Карточка создана!", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } else {
                Toast.makeText(requireContext(), "Заполните вопрос и ответ", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cancelButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }
}