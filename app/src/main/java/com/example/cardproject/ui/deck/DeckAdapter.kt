package com.example.cardproject.ui.deck

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
import com.example.cardproject.databinding.ItemDeckBinding
import com.example.cardproject.model.DeckWithTags
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DeckAdapter(
    private val onDeckClick: (DeckWithTags) -> Unit,
    private val onSelectionChange: (Int) -> Unit = {} // Колбэк для изменения количества выбранных элементов
) : ListAdapter<DeckWithTags, DeckAdapter.DeckViewHolder>(DeckDiffCallback) {

    private val selectedItems = mutableSetOf<Long>()
    private var isSelectionMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeckViewHolder {
        val binding = ItemDeckBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
//        binding.root.post {
//            val width = parent.measuredWidth / 2 - parent.context.resources.getDimensionPixelSize(R.dimen.grid_spacing) * 2
//            binding.root.layoutParams.height = width
//        }
        return DeckViewHolder(binding, onDeckClick, ::toggleSelection)
    }

    override fun onBindViewHolder(holder: DeckViewHolder, position: Int) {
        val deckWithTags = getItem(position)
        val isSelected = selectedItems.contains(deckWithTags.deck.id)
        holder.bind(deckWithTags, isSelected, isSelectionMode)
    }

    fun toggleSelection(deckId: Long) {
        if (selectedItems.contains(deckId)) {
            selectedItems.remove(deckId)
        } else {
            selectedItems.add(deckId)
        }

        isSelectionMode = selectedItems.isNotEmpty()
        onSelectionChange(selectedItems.size)
        notifyDataSetChanged()
    }

    fun getSelectedDecks(): List<DeckWithTags> {
        return currentList.filter { selectedItems.contains(it.deck.id) }
    }

    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        onSelectionChange(0)
        notifyDataSetChanged()
    }

    fun isInSelectionMode(): Boolean = isSelectionMode

    class DeckViewHolder(
        private val binding: ItemDeckBinding,
        private val onDeckClick: (DeckWithTags) -> Unit,
        private val onSelectionToggle: (Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(deckWithTags: DeckWithTags, isSelected: Boolean, isSelectionMode: Boolean) {
            val deck = deckWithTags.deck
            binding.deckName.text = deck.name
            binding.deckDescription.text = deck.description

            // Отображение количества карточек
            binding.cardCount.text = when (deck.cardCount) {
                0 -> "Нет карточек"
                1 -> "1 карточка"
                2, 3, 4 -> "${deck.cardCount} карточки"
                else -> "${deck.cardCount} карточек"
            }

            // Отображение цвета обложки
            if (deck.coverColor != null) {
                try {
                    binding.root.setCardBackgroundColor(Color.parseColor(deck.coverColor))
                    val backgroundColor = Color.parseColor(deck.coverColor)
                    val textColor = if (isColorDark(backgroundColor)) Color.WHITE else Color.BLACK
                    binding.deckName.setTextColor(textColor)
                    binding.deckDescription.setTextColor(textColor)
                    binding.cardCount.setTextColor(textColor)
                    binding.tagsTextView.setTextColor(textColor)
                } catch (e: Exception) {
                    setDefaultColors()
                }
            } else {
                setDefaultColors()
            }

            // Отображение тегов
            if (deckWithTags.tags.isNotEmpty()) {
                binding.tagsTextView.visibility = View.VISIBLE
                binding.tagsTextView.text = deckWithTags.tags.joinToString(", ")
            } else {
                binding.tagsTextView.visibility = View.GONE
            }

            // Отображение состояния выбора
            if (isSelected) {
                // Если колода выбрана - показываем overlay и чекбокс
                binding.selectionOverlay.visibility = View.VISIBLE
                binding.selectionCheckbox.visibility = View.VISIBLE
                binding.selectionCheckbox.isChecked = true
            } else if (isSelectionMode) {
                // Если режим выбора активен, но колода не выбрана - показываем только чекбокс (неактивный)
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
                    onSelectionToggle(deck.id)
                } else {
                    onDeckClick(deckWithTags)
                }
            }

            binding.root.setOnLongClickListener {
                onSelectionToggle(deck.id)
                true
            }

            // Обработка клика по чекбоксу
            binding.selectionCheckbox.setOnClickListener {
                onSelectionToggle(deck.id)
            }
        }

        private fun isColorDark(color: Int): Boolean {
            val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            return darkness >= 0.5
        }

        private fun setDefaultColors() {
            val context = binding.root.context
            binding.root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.cardview_light_background))
            binding.deckName.setTextColor(ContextCompat.getColor(context, R.color.cardview_dark_background))
            binding.deckDescription.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            binding.cardCount.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            binding.tagsTextView.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        }
    }

    object DeckDiffCallback : DiffUtil.ItemCallback<DeckWithTags>() {
        override fun areItemsTheSame(oldItem: DeckWithTags, newItem: DeckWithTags): Boolean {
            return oldItem.deck.id == newItem.deck.id
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: DeckWithTags, newItem: DeckWithTags): Boolean {
            return oldItem.deck == newItem.deck &&
                    oldItem.tags.toSet() == newItem.tags.toSet()
        }
        override fun getChangePayload(oldItem: DeckWithTags, newItem: DeckWithTags): Any? {
            return if (oldItem.deck != newItem.deck || oldItem.tags != newItem.tags) {
                true // Любой объект, указывающий на изменение
            } else {
                null
            }
        }
    }

}