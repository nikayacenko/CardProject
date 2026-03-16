// File: app/src/main/java/com/example/cardproject/model/QuestionType.kt
package com.example.cardproject.model

/**
 * Типы вопросов для классификации карточек
 */
enum class QuestionType {
    FACT,           // Простой факт (кто, что, когда)
    DEFINITION,     // Определение (что такое)
    PROOF;          // Доказательство (почему, докажите)

    companion object {
        fun fromString(type: String): QuestionType {
            return when (type.uppercase()) {
                "FACT" -> FACT
                "DEFINITION" -> DEFINITION
                "PROOF" -> PROOF
                else -> FACT
            }
        }

        fun encode(type: QuestionType): Float {
            return when (type) {
                FACT -> 0.1f
                DEFINITION -> 0.3f
                PROOF -> 0.9f
            }
        }
    }
}