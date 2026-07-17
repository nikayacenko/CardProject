import jwt
import bcrypt
from datetime import datetime, timedelta
from typing import Optional
from passlib.context import CryptContext

from core.database import Database
from core.config import settings

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
SECRET_KEY = "your-secret-key-here"  # В продакшене брать из env
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60 * 24 * 7  # 7 дней


class AuthService:
    def __init__(self):
        self.db = Database()
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

    def create_user(self, username: str, password: str, email: str = None) -> Optional[dict]:
        """Создание нового пользователя"""
        # Проверяем, существует ли пользователь
        conn = self.db._get_connection()
        cursor = conn.cursor()

        cursor.execute("SELECT id FROM users WHERE username = ?", (username,))
        if cursor.fetchone():
            conn.close()
            return None

        # Хэшируем пароль
        password_hash = pwd_context.hash(password)

        cursor.execute("""
            INSERT INTO users (username, email, password_hash)
            VALUES (?, ?, ?)
        """, (username, email, password_hash))

        user_id = cursor.lastrowid
        conn.commit()

        cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))
        row = cursor.fetchone()
        conn.close()

        return {
            "id": row[0],
            "username": row[1],
            "email": row[2],
            "created_at": row[4]
        }

    def authenticate_user(self, username: str, password: str) -> Optional[dict]:
        """Проверка логина и пароля"""
        conn = self.db._get_connection()
        cursor = conn.cursor()

        cursor.execute("SELECT * FROM users WHERE username = ?", (username,))
        row = cursor.fetchone()
        conn.close()

        if not row:
            return None

        # Проверяем пароль
        if not pwd_context.verify(password, row[3]):
            return None

        return {
            "id": row[0],
            "username": row[1],
            "email": row[2],
            "created_at": row[4]
        }

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


def create_access_token(data: dict, expires_delta: timedelta = None):
    """Создание JWT токена"""
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.utcnow() + expires_delta
    else:
        expire = datetime.utcnow() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)

    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)
    return encoded_jwt


def verify_token(token: str) -> Optional[dict]:
    """Проверка JWT токена"""
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        return payload
    except jwt.PyJWTError:
        return None