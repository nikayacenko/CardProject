"""
Работа с базой данных SQLite
"""

import sqlite3
from typing import List, Optional, Dict, Any
from datetime import datetime
from pathlib import Path

from core.config import settings


class Database:
    """Класс для работы с базой данных"""

    def __init__(self):
        self.db_path = settings.DB_PATH
        self._init_db()

    def _init_db(self):
        """Создание таблиц при первом запуске"""
        conn = self._get_connection()
        cursor = conn.cursor()

        # Таблица логов
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                card_id INTEGER NOT NULL,
                question_type TEXT NOT NULL,
                card_text_length INTEGER DEFAULT 0,
                has_formulas INTEGER DEFAULT 0,
                difficulty_score REAL DEFAULT 0.5,
                response_time_ms INTEGER DEFAULT 0,
                hour_of_day INTEGER DEFAULT 0,
                day_of_week INTEGER DEFAULT 1,
                fatigue_level REAL DEFAULT 0.0,
                cards_reviewed_in_session INTEGER DEFAULT 0,
                total_reviews_before INTEGER DEFAULT 0,
                correct_rate_before REAL DEFAULT 0.5,
                streak_correct_before INTEGER DEFAULT 0,
                days_since_last_review INTEGER DEFAULT 0,
                linked_cards_count INTEGER DEFAULT 0,
                linked_cards_mastery REAL DEFAULT 0.5,
                was_correct INTEGER DEFAULT 1,
                timestamp INTEGER NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)

        # Индексы для ускорения запросов
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_logs_user_id ON logs(user_id)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_logs_timestamp ON logs(timestamp)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_logs_card_id ON logs(card_id)")
        cursor.execute("CREATE INDEX IF NOT EXISTS idx_logs_user_id_timestamp ON logs(user_id, timestamp)")

        # Таблица версий моделей
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS model_versions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                version INTEGER NOT NULL UNIQUE,
                file_path TEXT NOT NULL,
                size_mb REAL DEFAULT 0,
                accuracy REAL DEFAULT 0,
                mae REAL DEFAULT 0,
                samples INTEGER DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                is_active INTEGER DEFAULT 1
            )
        """)

        # Таблица пользователей (для будущего расширения)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE,
                email TEXT UNIQUE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)

        conn.commit()
        conn.close()

    def _get_connection(self) -> sqlite3.Connection:
        """Получить соединение с БД"""
        # Создаём папку, если её нет
        Path(self.db_path).parent.mkdir(parents=True, exist_ok=True)
        return sqlite3.connect(self.db_path)

    # ============================================================
    # ЛОГИ
    # ============================================================

    def insert_logs(self, logs: List[Dict[str, Any]]) -> int:
        """Вставить несколько логов"""
        conn = self._get_connection()
        cursor = conn.cursor()

        saved = 0
        for log in logs:
            try:
                cursor.execute("""
                    INSERT INTO logs (
                        card_id, question_type, card_text_length, has_formulas,
                        difficulty_score, response_time_ms, hour_of_day, day_of_week,
                        fatigue_level, cards_reviewed_in_session, total_reviews_before,
                        correct_rate_before, streak_correct_before, days_since_last_review,
                        linked_cards_count, linked_cards_mastery, was_correct, timestamp
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    log.get('card_id', 0),
                    log.get('question_type', 'FACT'),
                    log.get('card_text_length', 0),
                    1 if log.get('has_formulas', False) else 0,
                    log.get('difficulty_score', 0.5),
                    log.get('response_time_ms', 0),
                    log.get('hour_of_day', 0),
                    log.get('day_of_week', 1),
                    log.get('fatigue_level', 0.0),
                    log.get('cards_reviewed_in_session', 0),
                    log.get('total_reviews_before', 0),
                    log.get('correct_rate_before', 0.5),
                    log.get('streak_correct_before', 0),
                    log.get('days_since_last_review', 0),
                    log.get('linked_cards_count', 0),
                    log.get('linked_cards_mastery', 0.5),
                    1 if log.get('was_correct', True) else 0,
                    log.get('timestamp', int(datetime.now().timestamp() * 1000))
                ))
                saved += 1
            except Exception as e:
                print(f"❌ Error inserting log: {e}")
                continue

        conn.commit()
        conn.close()
        return saved

    def get_logs_count(self) -> int:
        """Получить общее количество логов"""
        conn = self._get_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM logs")
        count = cursor.fetchone()[0]
        conn.close()
        return count

    def get_logs_for_training(self, limit: int = 10000) -> List[Dict[str, Any]]:
        """Получить логи для обучения модели"""
        conn = self._get_connection()
        cursor = conn.cursor()

        cursor.execute("""
            SELECT 
                card_text_length,
                has_formulas,
                question_type,
                response_time_ms,
                hour_of_day,
                day_of_week,
                fatigue_level,
                cards_reviewed_in_session,
                total_reviews_before,
                correct_rate_before,
                streak_correct_before,
                days_since_last_review,
                linked_cards_count,
                linked_cards_mastery,
                was_correct
            FROM logs
            ORDER BY timestamp DESC
            LIMIT ?
        """, (limit,))

        rows = cursor.fetchall()
        conn.close()

        # Преобразуем в список словарей
        columns = [
            'card_text_length', 'has_formulas', 'question_type',
            'response_time_ms', 'hour_of_day', 'day_of_week', 'fatigue_level',
            'cards_reviewed_in_session', 'total_reviews_before', 'correct_rate_before',
            'streak_correct_before', 'days_since_last_review', 'linked_cards_count',
            'linked_cards_mastery', 'was_correct'
        ]

        result = []
        for row in rows:
            result.append(dict(zip(columns, row)))

        return result

    def get_recent_logs(self, days: int = 7) -> int:
        """Получить количество логов за последние N дней"""
        conn = self._get_connection()
        cursor = conn.cursor()
        cursor.execute("""
            SELECT COUNT(*) FROM logs 
            WHERE created_at > datetime('now', ?)
        """, (f'-{days} days',))
        count = cursor.fetchone()[0]
        conn.close()
        return count

    # ============================================================
    # ВЕРСИИ МОДЕЛЕЙ
    # ============================================================

    def get_latest_model_version(self) -> Optional[Dict[str, Any]]:
        """Получить последнюю версию модели"""
        conn = self._get_connection()
        cursor = conn.cursor()
        cursor.execute("""
            SELECT * FROM model_versions 
            WHERE is_active = 1 
            ORDER BY version DESC 
            LIMIT 1
        """)
        row = cursor.fetchone()
        conn.close()

        if row:
            return {
                'id': row[0],
                'version': row[1],
                'file_path': row[2],
                'size_mb': row[3],
                'accuracy': row[4],
                'mae': row[5],
                'samples': row[6],
                'created_at': row[7],
                'is_active': row[8]
            }
        return None

    def save_model_version(self, version: int, file_path: str, metadata: Dict[str, Any]) -> int:
        """Сохранить информацию о новой версии модели"""
        conn = self._get_connection()
        cursor = conn.cursor()

        # Деактивируем старые версии
        cursor.execute("UPDATE model_versions SET is_active = 0")

        # Вставляем новую версию
        cursor.execute("""
            INSERT INTO model_versions (
                version, file_path, size_mb, accuracy, mae, samples, is_active
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (
            version,
            file_path,
            metadata.get('size_mb', 0),
            metadata.get('accuracy', 0),
            metadata.get('mae', 0),
            metadata.get('samples', 0),
            1
        ))

        conn.commit()
        conn.close()
        return cursor.lastrowid

    def get_count(self, user_id: int) -> int:
        """Получить количество логов пользователя"""
        conn = self._get_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM logs WHERE user_id = ?", (user_id,))
        count = cursor.fetchone()[0]
        conn.close()
        return count