package com.example.cardproject.ui.info

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.cardproject.R
import com.example.cardproject.databinding.FragmentLearningModesInfoBinding

class LearningModesInfoFragment : Fragment() {

    private var _binding: FragmentLearningModesInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearningModesInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupContent()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun setupContent() {
        // Контент уже установлен в XML, но можно добавить динамические элементы если нужно

        // Пример динамического обновления (если нужно)
        binding.fundamentalEffectiveness.text = "80-90% сохранения знаний через 6 месяцев"
        binding.eventPrepEffectiveness.text = "40-60% сохранения знаний через 1 месяц"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}