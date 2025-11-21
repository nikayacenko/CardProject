// NoteDetailFragment.kt
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NoteDetailFragment : Fragment() {

    private var _binding: FragmentNoteDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NoteDetailViewModel by viewModels()

    private var noteId: Long = -1

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
            requireActivity().supportFragmentManager.popBackStack()
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
                    binding.titleInput.setText(it.note.title)
                    binding.contentInput.setText(it.note.content)
                    binding.tagsInput.setText(it.tags.joinToString(", "))
                }
            }
        }
    }

    private fun setupClickListeners() {
        // Автосохранение при изменении текста
        binding.titleInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                autoSave()
            }
        }

        binding.contentInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                autoSave()
            }
        }
    }

    private fun autoSave() {
        if (isValidInput()) {
            saveNote(false) // Тихий режим без Toast
        }
    }

    private fun saveNote(showToast: Boolean = true) {
        if (!isValidInput()) return

        val title = binding.titleInput.text.toString().trim()
        val content = binding.contentInput.text.toString().trim()
        val tags = binding.tagsInput.text.toString().trim()

        val tagList = if (tags.isNotEmpty()) {
            tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        viewModel.saveNote(title, content, tagList)

        if (showToast) {
            Toast.makeText(requireContext(), "Конспект сохранен!", Toast.LENGTH_SHORT).show()
        }
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

    private fun isValidInput(): Boolean {
        return binding.titleInput.text.toString().trim().isNotBlank() &&
                binding.contentInput.text.toString().trim().isNotBlank()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}