"""
Сервис для работы с пользователями
"""

import bcrypt
import jwt
from datetime import datetime, timedelta
from typing import Optional

from core.database import Database
from passlib.context import CryptContext

SECRET_KEY = "your-super-secret-key-change-this-in-production"
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60 * 24 * 7  # 7 дней


class UserService:
    def __init__(self):
        self.db = Database()  # ← УБЕДИТЕСЬ, ЧТО ЗДЕСЬ self.db
        self._init_db()

    def _init_db(self):
        """Создание таблицы пользователей"""
        conn = self.db._get_connection()
        cursor = conn.cursor()

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                email TEXT,
                password_hash TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)

        conn.commit()
        conn.close()
        print("✅ Таблица users создана/проверена")

    def create_user(self, username: str, password: str, email: str = None) -> Optional[dict]:
        """Создание нового пользователя"""
        print(f"🔴 create_user: username={username}, email={email}")

        try:
            conn = self.db._get_connection()
            cursor = conn.cursor()

            cursor.execute("SELECT id FROM users WHERE username = ?", (username,))
            if cursor.fetchone():
                conn.close()
                return None

            password_hash = bcrypt.hashpw(
                password.encode('utf-8'),
                bcrypt.gensalt()
            ).decode('utf-8')
            print(f"🔑 Хэш пароля для {username}: {password_hash[:30]}...")

            cursor.execute("""
                INSERT INTO users (username, email, password_hash)
                VALUES (?, ?, ?)
            """, (username, email, password_hash))

            user_id = cursor.lastrowid
            conn.commit()

            cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
            row = cursor.fetchone()
            conn.close()

            if not row:
                return None

            return {
                "id": row[0],
                "username": row[1],
                "email": row[2],
                "password_hash": row[3],
                "created_at": row[4]
            }

        except Exception as e:
            print(f"❌ Ошибка в create_user: {e}")
            import traceback
            traceback.print_exc()
            return None

    def authenticate_user(self, username: str, password: str) -> Optional[dict]:
        """Проверка логина и пароля"""
        print(f"🔴 authenticate_user: username={username}")

        try:
            conn = self.db._get_connection()
            cursor = conn.cursor()

            cursor.execute("SELECT * FROM users WHERE username = ?", (username,))
            row = cursor.fetchone()
            conn.close()

            if not row:
                print(f"❌ Пользователь {username} не найден")
                return None

            user_id = row[0]
            user_username = row[1]
            user_email = row[2]
            created_at = row[3]
            stored_hash = row[4]

            print(f"🔑 Хэш из БД: {stored_hash[:30]}...")

            if not stored_hash:
                print(f"❌ Хэш пароля для {username} пуст")
                return None

            if not stored_hash.startswith('$2b$'):
                print(f"❌ Неверный формат хэша: {stored_hash[:20]}...")
                return None

            try:
                is_valid = bcrypt.checkpw(
                    password.encode('utf-8'),
                    stored_hash.encode('utf-8')
                )
            except ValueError as e:
                print(f"❌ Ошибка при проверке пароля: {e}")
                return None

            if not is_valid:
                print(f"❌ Неверный пароль для {username}")
                return None

            print(f"✅ Пароль верный для {username}")

            return {
                "id": user_id,
                "username": user_username,
                "email": user_email,
                "created_at": created_at
            }

        except Exception as e:
            print(f"❌ Ошибка в authenticate_user: {e}")
            import traceback
            traceback.print_exc()
            return None

    def get_user_by_id(self, user_id: int) -> Optional[dict]:
        """Получение пользователя по ID"""
        conn = self.db._get_connection()
        cursor = conn.cursor()

        cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
        row = cursor.fetchone()
        conn.close()

        if not row:
            return None

        return {
            "id": row[0],
            "username": row[1],
            "email": row[2],
            "created_at": row[4]
        }

    def get_users_count(self) -> int:
        """Количество пользователей"""
        conn = self.db._get_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM users")
        count = cursor.fetchone()[0]
        conn.close()
        return count

    def create_access_token(self, user_id: int, username: str) -> str:
        """Создание JWT токена"""
        to_encode = {
            "sub": str(user_id),
            "username": username,
            "exp": datetime.utcnow() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
        }
        return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)

    def verify_token(self, token: str) -> Optional[dict]:
        """Проверка JWT токена"""
        try:
            payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
            return payload
        except jwt.ExpiredSignatureError:
            print("❌ Токен истёк")
            return None
        except jwt.InvalidTokenError as e:
            print(f"❌ Неверный токен: {e}")
            return None