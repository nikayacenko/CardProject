// NoteListFragment.kt
package com.example.cardproject.ui.notes

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cardproject.R
import com.example.cardproject.databinding.FragmentNoteListBinding
import com.example.cardproject.model.NoteWithTags
import com.example.cardproject.ui.deck.GridSpacingItemDecoration
import com.example.cardproject.utils.TagColorHelper
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NoteListFragment : Fragment() {

    private var _binding: FragmentNoteListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NoteListViewModel by viewModels()
    private lateinit var adapter: NoteAdapter

    private var isSelectionMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoteListBinding.inflate(inflater, container, false)
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
        showNormalMode()
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun showNormalMode() {
        binding.toolbar.menu.clear()
        binding.toolbar.title = "Мои конспекты"
        binding.toolbar.navigationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_arrow_back)
    }

    private fun showSelectionMode(selectedCount: Int) {
        binding.toolbar.menu.clear()
        binding.toolbar.title = "Выбрано: $selectedCount"

        binding.toolbar.setNavigationIcon(R.drawable.ic_close_24dp)
        binding.toolbar.setNavigationOnClickListener {
            cancelSelection()
        }

        binding.toolbar.inflateMenu(R.menu.menu_note_selection)

        val deleteMenuItem = binding.toolbar.menu.findItem(R.id.menu_delete)
        deleteMenuItem?.isVisible = selectedCount > 0

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_delete -> {
                    showDeleteConfirmationDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun editSelectedNote() {
        val selectedNotes = adapter.getSelectedNotes()
        if (selectedNotes.size == 1) {
            val noteToEdit = selectedNotes.first()
            openNoteDetail(noteToEdit.note.id)
        }
    }

    private fun cancelSelection() {
        adapter.clearSelection()
        showNormalMode()
        isSelectionMode = false
    }

    private fun showDeleteConfirmationDialog() {
        val selectedNotes = adapter.getSelectedNotes()
        if (selectedNotes.isEmpty()) return

        val noteTitles = selectedNotes.joinToString("\n") { "• ${it.note.title}" }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить выбранные конспекты?")
            .setMessage("Будут удалены следующие конспекты:\n\n$noteTitles\n\nЭто действие нельзя отменить.")
            .setPositiveButton("Удалить") { dialog, _ ->
                deleteSelectedNotes()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteSelectedNotes() {
        val selectedNotes = adapter.getSelectedNotes()
        selectedNotes.forEach { noteWithTags ->
            viewModel.deleteNote(noteWithTags)
        }

        Toast.makeText(
            requireContext(),
            "Удалено конспектов: ${selectedNotes.size}",
            Toast.LENGTH_SHORT
        ).show()

        cancelSelection()
    }

    private fun setupRecyclerView() {
        adapter = NoteAdapter(
            onNoteClick = { noteWithTags ->
                if (!adapter.isInSelectionMode()) {
                    openNoteDetail(noteWithTags.note.id)
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

        // Grid layout с 2 колонками как у колод
        val gridLayoutManager = GridLayoutManager(requireContext(), 2)
        binding.notesRecyclerView.layoutManager = gridLayoutManager
        binding.notesRecyclerView.adapter = adapter

        // Добавляем отступы между элементами
        val spacingInPixels = resources.getDimensionPixelSize(R.dimen.grid_spacing)
        binding.notesRecyclerView.addItemDecoration(GridSpacingItemDecoration(2, spacingInPixels, true))
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.notes.collectLatest { notes ->
                adapter.submitList(notes)
                if (notes.isEmpty()) {
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.notesRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyStateText.visibility = View.GONE
                    binding.notesRecyclerView.visibility = View.VISIBLE
                }
            }
        }

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

    private fun setupClickListeners() {
        binding.fabCreateNote.setOnClickListener {
            // Снимаем выделение перед созданием конспекта
            if (adapter.isInSelectionMode()) {
                adapter.clearSelection()
            }
            showCreateNoteDialog()
        }
    }

    private fun showCreateNoteDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Создать конспект")

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }

        val titleInput = EditText(requireContext()).apply {
            hint = "Название конспекта"
            setSingleLine(true)
        }

        val contentInput = EditText(requireContext()).apply {
            hint = "Содержание конспекта"
            setSingleLine(false)
            minLines = 3
            maxLines = 6
        }

        val tagsInput = EditText(requireContext()).apply {
            hint = "Теги (через запятую)"
            setSingleLine(true)
        }

        layout.addView(titleInput)
        layout.addView(contentInput)
        layout.addView(tagsInput)
        builder.setView(layout)

        builder.setPositiveButton("Создать") { _, _ ->
            val title = titleInput.text.toString().trim()
            val content = contentInput.text.toString().trim()
            val tags = tagsInput.text.toString().trim()

            val tagList = if (tags.isNotEmpty()) {
                tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            if (title.isNotBlank() && content.isNotBlank()) {
                viewModel.createNote(title, content, tagList)
                Toast.makeText(requireContext(), "Конспект создан!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Заполните название и содержание", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    private fun openNoteDetail(noteId: Long) {
        val noteDetailFragment = NoteDetailFragment().apply {
            arguments = Bundle().apply {
                putLong("noteId", noteId)
            }
        }

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, noteDetailFragment)
            .addToBackStack("note_list")
            .commit()
    }

    private fun setupSearchView() {
        binding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                viewModel.setSearchQuery(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.searchInputLayout.setEndIconOnClickListener {
            binding.searchInput.setText("")
            viewModel.clearSearch()
        }
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
                setChipStrokeColor(android.content.res.ColorStateList.valueOf(strokeColor))
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
                chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(strokeColor))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}