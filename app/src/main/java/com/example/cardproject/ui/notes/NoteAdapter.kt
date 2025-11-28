package com.example.cardproject.ui.notes

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cardproject.R
import com.example.cardproject.databinding.ItemNoteBinding
import com.example.cardproject.model.NoteWithTags
import java.text.SimpleDateFormat
import java.util.Locale

class NoteAdapter(
    private val onNoteClick: (NoteWithTags) -> Unit,
    private val onSelectionChange: (Int) -> Unit = {}
) : ListAdapter<NoteWithTags, NoteAdapter.NoteViewHolder>(NoteDiffCallback) {

    private val selectedItems = mutableSetOf<Long>()
    private var isSelectionMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NoteViewHolder(binding, onNoteClick, ::toggleSelection)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val noteWithTags = getItem(position)
        val isSelected = selectedItems.contains(noteWithTags.note.id)
        holder.bind(noteWithTags, isSelected, isSelectionMode)
    }

    fun toggleSelection(noteId: Long) {
        if (selectedItems.contains(noteId)) {
            selectedItems.remove(noteId)
        } else {
            selectedItems.add(noteId)
        }

        isSelectionMode = selectedItems.isNotEmpty()
        onSelectionChange(selectedItems.size)
        notifyDataSetChanged()
    }

    fun getSelectedNotes(): List<NoteWithTags> {
        return currentList.filter { selectedItems.contains(it.note.id) }
    }

    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        onSelectionChange(0)
        notifyDataSetChanged()
    }

    fun isInSelectionMode(): Boolean = isSelectionMode

    class NoteViewHolder(
        private val binding: ItemNoteBinding,
        private val onNoteClick: (NoteWithTags) -> Unit,
        private val onSelectionToggle: (Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        fun bind(noteWithTags: NoteWithTags, isSelected: Boolean, isSelectionMode: Boolean) {
            val note = noteWithTags.note

            binding.noteTitle.text = note.title
            binding.noteDate.text = dateFormat.format(note.updatedAt)
//            binding.noteContent.text = note.content

            // Обрезаем содержание для отображения в карточке
            val shortContent = if (note.content.length > 100) {
                note.content.substring(0, 100) + "..."
            } else {
                note.content
            }
            binding.noteContent.text = shortContent

            // Отображение тегов
            if (noteWithTags.tags.isNotEmpty()) {
                binding.tagsTextView.visibility = View.VISIBLE
                binding.tagsTextView.text = noteWithTags.tags.joinToString(", ")
            } else {
                binding.tagsTextView.visibility = View.GONE
            }

            // Устанавливаем цвет карточки (можно сделать как у колод или оставить стандартный)
            setCardAppearance(noteWithTags)

            // Отображение состояния выбора
            if (isSelected) {
                // Если конспект выбран - показываем overlay и чекбокс
                binding.selectionOverlay.visibility = View.VISIBLE
                binding.selectionCheckbox.visibility = View.VISIBLE
                binding.selectionCheckbox.isChecked = true
            } else if (isSelectionMode) {
                // Если режим выбора активен, но конспект не выбран - показываем только чекбокс (неактивный)
                binding.selectionOverlay.visibility = View.GONE
                binding.selectionCheckbox.visibility = View.VISIBLE
                binding.selectionCheckbox.isChecked = false
            } else {
                // Обычный режим - скрываем все элементы выбора
                binding.selectionOverlay.visibility = View.GONE
                binding.selectionCheckbox.visibility = View.GONE
            }

            // Обработка кликов
            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    onSelectionToggle(note.id)
                } else {
                    onNoteClick(noteWithTags)
                }
            }

            binding.root.setOnLongClickListener {
                onSelectionToggle(note.id)
                true
            }

            // Обработка клика по чекбоксу
            binding.selectionCheckbox.setOnClickListener {
                onSelectionToggle(note.id)
            }
        }

        private fun setCardAppearance(noteWithTags: NoteWithTags) {
            // Можно добавить цветовую схему для конспектов как у колод
            // Пока используем стандартные цвета
            setDefaultColors()
        }

        private fun setDefaultColors() {
            val context = binding.root.context
            binding.root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.cardview_light_background))
            binding.noteTitle.setTextColor(ContextCompat.getColor(context, R.color.cardview_dark_background))
            binding.noteDate.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            binding.noteContent.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            binding.tagsTextView.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        }
    }

    object NoteDiffCallback : DiffUtil.ItemCallback<NoteWithTags>() {
        override fun areItemsTheSame(oldItem: NoteWithTags, newItem: NoteWithTags): Boolean {
            return oldItem.note.id == newItem.note.id
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: NoteWithTags, newItem: NoteWithTags): Boolean {
            return oldItem.note == newItem.note &&
                    oldItem.tags.toSet() == newItem.tags.toSet()
        }

        override fun getChangePayload(oldItem: NoteWithTags, newItem: NoteWithTags): Any? {
            return if (oldItem.note != newItem.note || oldItem.tags != newItem.tags) {
                true // Любой объект, указывающий на изменение
            } else {
                null
            }
        }
    }
}