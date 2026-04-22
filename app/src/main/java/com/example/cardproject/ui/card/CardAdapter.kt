package com.example.cardproject.ui.card

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cardproject.R
import com.example.cardproject.databinding.ItemCardBinding
import com.example.cardproject.model.Card
import com.example.cardproject.ui.components.LearningStatusTooltip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CardAdapter(
    private val onCardClick: (Card) -> Unit = {},
    private val onSelectionChange: (Int) -> Unit = {}
) : ListAdapter<Card, CardAdapter.CardViewHolder>(CardDiffCallback) {

    private val selectedItems = mutableSetOf<Long>()
    private var isSelectionMode = false
    private var currentTooltip: LearningStatusTooltip? = null

    fun dismissTooltip() {
        currentTooltip?.dismissWithAnimation()
        currentTooltip = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CardViewHolder(binding, onCardClick, ::toggleSelection)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = getItem(position)
        val isSelected = selectedItems.contains(card.id)
        holder.bind(card, isSelected, isSelectionMode)
    }

    override fun onViewRecycled(holder: CardViewHolder) {
        super.onViewRecycled(holder)
        holder.stopTimer()
    }

    fun toggleSelection(cardId: Long) {
        if (selectedItems.contains(cardId)) {
            selectedItems.remove(cardId)
        } else {
            selectedItems.add(cardId)
        }

        isSelectionMode = selectedItems.isNotEmpty()
        onSelectionChange(selectedItems.size)
        notifyDataSetChanged()
    }

    fun getSelectedCards(): List<Card> {
        return currentList.filter { selectedItems.contains(it.id) }
    }

    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
        onSelectionChange(0)
        notifyDataSetChanged()
    }

    fun isInSelectionMode(): Boolean = isSelectionMode

    // Форматирование времени
    private fun formatTime(seconds: Long): String {
        return when {
            seconds >= 3600 -> {
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val secs = seconds % 60
                String.format("%dч %02dм %02dс", hours, minutes, secs)
            }
            seconds >= 60 -> {
                val minutes = seconds / 60
                val secs = seconds % 60
                String.format("%dм %02dс", minutes, secs)
            }
            else -> "${seconds}с"
        }
    }

    inner class CardViewHolder(
        private val binding: ItemCardBinding,
        private val onCardClick: (Card) -> Unit,
        private val onSelectionToggle: (Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var isExpanded = false
        private var timerJob: Job? = null
        private var currentCard: Card? = null

        fun bind(card: Card, isSelected: Boolean, isSelectionMode: Boolean) {
            currentCard = card
            binding.cardFront.text = card.front
            binding.cardBack.text = card.back
            binding.cardBack.visibility = View.VISIBLE

            setupLearningStatus(card)
            setupIconTooltip(card)

            val isLongText = card.front.length > 200 || card.back.length > 200
            setupExpandButton(isLongText)

            binding.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE

            // Обработка кликов
            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    onSelectionToggle(card.id)
                } else {
                    onCardClick(card)
                }
            }

            binding.root.setOnLongClickListener {
                onSelectionToggle(card.id)
                true
            }

            // Запускаем таймер для этой карточки
            startTimerIfNeeded(card)
        }

        private fun startTimerIfNeeded(card: Card) {
            val now = System.currentTimeMillis()
            val isBlocked = card.nextReview != null && card.nextReview!! > now

            if (isBlocked) {
                startTimer(card)
            } else {
                stopTimer()
                binding.timerLayout.visibility = View.GONE
            }
        }

        private fun startTimer(card: Card) {
            stopTimer()

            timerJob = CoroutineScope(Dispatchers.Main).launch {
                while (true) {
                    val now = System.currentTimeMillis()
                    val remainingMs = card.nextReview?.let { it - now } ?: 0L

                    if (remainingMs <= 0) {
                        // Таймер истёк
                        binding.timerLayout.visibility = View.GONE
                        stopTimer()
                        break
                    }

                    // Показываем таймер
                    binding.timerLayout.visibility = View.VISIBLE
                    val seconds = remainingMs / 1000
                    binding.timerText.text = formatTime(seconds)

                    delay(1000)
                }
            }
        }

        fun stopTimer() {
            timerJob?.cancel()
            timerJob = null
            // Не скрываем таймер здесь, только останавливаем обновление
        }

        private fun setupLearningStatus(card: Card) {
            val learnedIcon = binding.learnedIcon

            when {
                isCardLearned(card) -> {
                    learnedIcon.setImageResource(R.drawable.ic_learned)
                    learnedIcon.visibility = View.VISIBLE
                }
                isCardInProgress(card) -> {
                    learnedIcon.setImageResource(R.drawable.ic_learning)
                    learnedIcon.visibility = View.VISIBLE
                }
                isCardNew(card) -> {
                    learnedIcon.setImageResource(R.drawable.ic_new)
                    learnedIcon.visibility = View.VISIBLE
                }
                else -> {
                    learnedIcon.visibility = View.GONE
                }
            }
        }

        private fun setupIconTooltip(card: Card) {
            binding.learnedIcon.setOnClickListener {
                showTooltip(card, binding.learnedIcon)
            }

            binding.learnedIcon.setOnLongClickListener {
                showTooltip(card, binding.learnedIcon)
                true
            }
        }

        private fun showTooltip(card: Card, targetView: View) {
            dismissTooltip()

            val tooltipText = when {
                isCardLearned(card) -> "✅ Выученная карточка"
                isCardInProgress(card) -> "🔄 В процессе изучения"
                isCardNew(card) -> "🆕 Новая карточка"
                else -> "❓ Неизвестный статус"
            }

            currentTooltip = LearningStatusTooltip(targetView.context).apply {
                show(targetView, tooltipText)
                setOnDismissListener {
                    currentTooltip = null
                }
            }
        }

        private fun isCardLearned(card: Card): Boolean {
            return card.reviewStage >= 3 && card.consecutiveCorrect >= 2
        }

        private fun isCardInProgress(card: Card): Boolean {
            return card.lastReviewed != null && !isCardLearned(card)
        }

        private fun isCardNew(card: Card): Boolean {
            return card.reviewStage == 0 && card.lastReviewed == null
        }

        private fun setupExpandButton(isLongText: Boolean) {
            val expandButton = binding.expandButton

            if (expandButton != null && isLongText) {
                expandButton.visibility = View.VISIBLE

                if (!isExpanded) {
                    binding.cardFront.maxLines = 4
                    binding.cardBack.maxLines = 4
                    expandButton.text = "Развернуть"
                } else {
                    binding.cardFront.maxLines = Int.MAX_VALUE
                    binding.cardBack.maxLines = Int.MAX_VALUE
                    expandButton.text = "Свернуть"
                }

                expandButton.setOnClickListener {
                    dismissTooltip()
                    isExpanded = !isExpanded
                    if (isExpanded) {
                        binding.cardFront.maxLines = Int.MAX_VALUE
                        binding.cardBack.maxLines = Int.MAX_VALUE
                        expandButton.text = "Свернуть"
                    } else {
                        binding.cardFront.maxLines = 4
                        binding.cardBack.maxLines = 4
                        expandButton.text = "Развернуть"
                    }
                }
            } else if (expandButton != null) {
                expandButton.visibility = View.GONE
                binding.cardFront.maxLines = Int.MAX_VALUE
                binding.cardBack.maxLines = Int.MAX_VALUE
            }
        }
    }

    override fun onViewDetachedFromWindow(holder: CardViewHolder) {
        super.onViewDetachedFromWindow(holder)
        dismissTooltip()
        holder.stopTimer()
    }

    object CardDiffCallback : DiffUtil.ItemCallback<Card>() {
        override fun areItemsTheSame(oldItem: Card, newItem: Card): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Card, newItem: Card): Boolean {
            // Сравниваем всё, кроме nextReview (он меняется каждую секунду)
            return oldItem.id == newItem.id &&
                    oldItem.deckId == newItem.deckId &&
                    oldItem.front == newItem.front &&
                    oldItem.back == newItem.back &&
                    oldItem.reviewStage == newItem.reviewStage &&
                    oldItem.consecutiveCorrect == newItem.consecutiveCorrect &&
                    oldItem.interval == newItem.interval
        }
    }
}