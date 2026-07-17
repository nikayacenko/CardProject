"""
Сервис для работы с логами
"""

import sqlite3
from typing import List
from datetime import datetime
from pathlib import Path

from api.models import LogEntry
from core.config import settings


class LogService:
    """Управление хранением логов в SQLite"""

    def __init__(self):
        self.db_path = settings.DB_PATH
        self._init_db()

    def _init_db(self):
        """Создать таблицу, если её нет"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                card_id INTEGER,
                question_type TEXT,
                card_text_length INTEGER,
                has_formulas INTEGER,
                difficulty_score REAL,
                response_time_ms INTEGER,
                hour_of_day INTEGER,
                day_of_week INTEGER,
                fatigue_level REAL,
                cards_reviewed_in_session INTEGER,
                total_reviews_before INTEGER,
                correct_rate_before REAL,
                streak_correct_before INTEGER,
                days_since_last_review INTEGER,
                linked_cards_count INTEGER,
                linked_cards_mastery REAL,
                was_correct INTEGER,
                timestamp INTEGER,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)

        # Индексы для быстрого поиска
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_timestamp ON logs(timestamp)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_card_id ON logs(card_id)")

        conn.commit()
        conn.close()

    def save_logs(self, logs: List[LogEntry], user_id: int) -> int:
        """Сохранить логи пользователя"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()

        saved = 0
        for log in logs:
            try:
                cursor.execute("""
                    INSERT INTO logs (
                        user_id, card_id, question_type, card_text_length, has_formulas,
                        difficulty_score, response_time_ms, hour_of_day, day_of_week,
                        fatigue_level, cards_reviewed_in_session, total_reviews_before,
                        correct_rate_before, streak_correct_before, days_since_last_review,
                        linked_cards_count, linked_cards_mastery, was_correct, timestamp
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    user_id, log.card_id, log.question_type, log.card_text_length,
                    1 if log.has_formulas else 0, log.difficulty_score,
                    log.response_time_ms, log.hour_of_day, log.day_of_week,
                    log.fatigue_level, log.cards_reviewed_in_session,
                    log.total_reviews_before, log.correct_rate_before,
                    log.streak_correct_before, log.days_since_last_review,
                    log.linked_cards_count, log.linked_cards_mastery,
                    1 if log.was_correct else 0, log.timestamp or int(datetime.now().timestamp() * 1000)
                ))
                saved += 1
            except Exception as e:
                print(f"Error saving log: {e}")
                continue

        conn.commit()
        conn.close()
        return saved

    def get_count(self, user_id: int) -> int:
        """Получить количество логов пользователя"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM logs WHERE user_id = ?", (user_id,))
        count = cursor.fetchone()[0]
        conn.close()
        return count

    def get_all_logs(self, limit: int = 10000):
        """Получить последние логи"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        cursor.execute("""
            SELECT * FROM logs 
            ORDER BY timestamp DESC 
            LIMIT ?
        """, (limit,))
        rows = cursor.fetchall()
        conn.close()
        return rows

    def get_logs_for_training(self, limit: int = 5000):
        """Получить логи для обучения модели (как CSV)"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        cursor.execute("""
            SELECT 
                card_text_length, has_formulas, question_type,
                response_time_ms, hour_of_day, day_of_week, fatigue_level,
                cards_reviewed_in_session, total_reviews_before, correct_rate_before,
                streak_correct_before, days_since_last_review, linked_cards_count,
                linked_cards_mastery, was_correct
            FROM logs
            ORDER BY timestamp DESC
            LIMIT ?
        """, (limit,))
        rows = cursor.fetchall()
        conn.close()
        return rows

    def get_total_count(self) -> int:
        """Получить общее количество логов (для health check)"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM logs")
        count = cursor.fetchone()[0]
        conn.close()
        return count