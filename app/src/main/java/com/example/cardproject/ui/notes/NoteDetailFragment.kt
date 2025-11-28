package com.example.cardproject.ui.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.cardproject.R
import com.example.cardproject.databinding.FragmentNoteDetailBinding
import com.example.cardproject.model.NoteWithTags
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NoteDetailFragment : Fragment() {

    private var _binding: FragmentNoteDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NoteDetailViewModel by viewModels()

    private var noteId: Long = -1
    private var hasUnsavedChanges = false
    private var originalNote: NoteWithTags? = null
    private var isDataLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        noteId = arguments?.getLong("noteId") ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoteDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupObservers()
        setupClickListeners()

        if (noteId != -1L) {
            viewModel.loadNote(noteId)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            if (hasUnsavedChanges) {
                showUnsavedChangesDialog()
            } else {
                requireActivity().supportFragmentManager.popBackStack()
            }
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_save -> {
                    saveNote()
                    true
                }
                R.id.menu_delete -> {
                    deleteNote()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.note.collect { noteWithTags ->
                noteWithTags?.let {
                    if (!isDataLoaded) {
                        // Первоначальная загрузка данных
                        originalNote = noteWithTags
                        binding.titleInput.setText(it.note.title)
                        binding.contentInput.setText(it.note.content)
                        binding.tagsInput.setText(it.tags.joinToString(", "))
                        isDataLoaded = true
                        hasUnsavedChanges = false
                    } else {
                        // Обновление данных после сохранения
                        originalNote = noteWithTags
                        hasUnsavedChanges = false
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.titleInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                checkForChanges()
            }
        }

        binding.contentInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                checkForChanges()
            }
        }

        binding.tagsInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                checkForChanges()
            }
        }

        binding.titleInput.addTextChangedListener(createTextWatcher())
        binding.contentInput.addTextChangedListener(createTextWatcher())
        binding.tagsInput.addTextChangedListener(createTextWatcher())
    }

    private fun createTextWatcher(): android.text.TextWatcher {
        return object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkForChanges()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }
    }

    private fun checkForChanges() {
        if (!isDataLoaded) return

        val currentTitle = binding.titleInput.text?.toString()?.trim() ?: ""
        val currentContent = binding.contentInput.text?.toString()?.trim() ?: ""
        val currentTags = binding.tagsInput.text?.toString()?.trim() ?: ""
        val currentTagsList = if (currentTags.isNotEmpty()) {
            currentTags.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        val original = originalNote
        hasUnsavedChanges = if (original != null) {
            currentTitle != original.note.title ||
                    currentContent != original.note.content ||
                    currentTagsList.toSet() != original.tags.toSet()
        } else {
            currentTitle.isNotBlank() || currentContent.isNotBlank() || currentTagsList.isNotEmpty()
        }
    }

    private fun saveNote() {
        if (!isValidInput()) {
            Toast.makeText(requireContext(), "Заполните название и содержание", Toast.LENGTH_SHORT).show()
            return
        }

        val currentData = getCurrentInputData()
        viewModel.saveNote(currentData.title, currentData.content, currentData.tags)

        viewLifecycleOwner.lifecycleScope.launch {
            // Ждем обновления данных
            kotlinx.coroutines.delay(300)
            val updatedNote = viewModel.note.value
            if (updatedNote != null) {
                originalNote = updatedNote
                hasUnsavedChanges = false
                Toast.makeText(requireContext(), "Конспект сохранен!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performSaveAndExit() {
        if (!isValidInput()) {
            Toast.makeText(requireContext(), "Заполните название и содержание", Toast.LENGTH_SHORT).show()
            return
        }

        val currentData = getCurrentInputData()
        viewModel.saveNote(currentData.title, currentData.content, currentData.tags)

        viewLifecycleOwner.lifecycleScope.launch {
            // Даем время на сохранение перед выходом
            kotlinx.coroutines.delay(300)
            Toast.makeText(requireContext(), "Конспект сохранен!", Toast.LENGTH_SHORT).show()
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun getCurrentInputData(): CurrentInputData {
        val title = binding.titleInput.text.toString().trim()
        val content = binding.contentInput.text.toString().trim()
        val tags = binding.tagsInput.text.toString().trim()

        val tagList = if (tags.isNotEmpty()) {
            tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        return CurrentInputData(title, content, tagList)
    }

    private fun deleteNote() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Удалить конспект?")
            .setMessage("Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteNote()
                Toast.makeText(requireContext(), "Конспект удален", Toast.LENGTH_SHORT).show()
                requireActivity().supportFragmentManager.popBackStack()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showUnsavedChangesDialog() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Несохраненные изменения")
            .setMessage("У вас есть несохраненные изменения. Сохранить перед выходом?")
            .setPositiveButton("Сохранить") { _, _ ->
                performSaveAndExit()
            }
            .setNegativeButton("Не сохранять") { _, _ ->
                requireActivity().supportFragmentManager.popBackStack()
            }
            .setNeutralButton("Отмена", null)
            .show()
    }

    private fun isValidInput(): Boolean {
        return binding.titleInput.text.toString().trim().isNotBlank()

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class CurrentInputData(
        val title: String,
        val content: String,
        val tags: List<String>
    )
}