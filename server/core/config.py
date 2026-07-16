"""
Настройки сервера
"""

import os
from pathlib import Path

# Путь к корню сервера
BASE_DIR = Path(__file__).parent.parent


class Settings:
    """Настройки приложения"""

    # Сервер
    HOST = os.getenv("HOST", "0.0.0.0")
    PORT = int(os.getenv("PORT", 8000))
    DEBUG = os.getenv("DEBUG", "True").lower() == "true"

    # База данных
    DB_PATH = os.getenv("DB_PATH", str(BASE_DIR / "data" / "logs.db"))

    # Модели
    MODELS_DIR = os.getenv("MODELS_DIR", str(BASE_DIR / "static" / "models"))

    # Пути к скриптам обучения (относительно корня проекта)
    TRAINING_SCRIPT = os.getenv(
        "TRAINING_SCRIPT",
        str(BASE_DIR.parent / "ml_training" / "train_model.py")
    )
    FINE_TUNE_SCRIPT = os.getenv(
        "FINE_TUNE_SCRIPT",
        str(BASE_DIR.parent / "ml_training" / "fine_tune.py")
    )

    # Лимиты
    MAX_LOGS_PER_REQUEST = 1000

    # Создание папок при инициализации
    @classmethod
    def ensure_dirs(cls):
        """Создать необходимые папки"""
        os.makedirs(os.path.dirname(cls.DB_PATH), exist_ok=True)
        os.makedirs(cls.MODELS_DIR, exist_ok=True)


settings = Settings()
settings.ensure_dirs()