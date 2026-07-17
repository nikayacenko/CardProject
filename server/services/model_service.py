"""
Сервис для управления ML-моделями
"""

import os
import json
import subprocess
import threading
from pathlib import Path
from typing import Optional, Dict, Any
from datetime import datetime

from core.config import settings
from core.database import Database
from ml.model_manager import ModelManager


class ModelService:
    """Сервис для управления ML-моделями"""

    def __init__(self):
        self.db = Database()
        self.manager = ModelManager()
        self._training_status = {
            'is_running': False,
            'progress': 0,
            'message': 'Готов к работе'
        }
        self._training_thread = None

    def is_training(self) -> bool:
        """Идёт ли обучение"""
        return self._training_status.get('is_running', False)

    def get_latest_version(self, user_id: int) -> Optional[Dict[str, Any]]:
        """Получить последнюю версию модели для пользователя"""
        # Проверяем, есть ли у пользователя своя модель
        user_model_path = Path(settings.MODELS_DIR) / f"user_{user_id}" / "latest_version.json"

        if user_model_path.exists():
            with open(user_model_path, 'r') as f:
                return json.load(f)

        # Если нет, возвращаем базовую модель
        return self.get_base_model_info()

    def get_base_model_info(self) -> Optional[Dict[str, Any]]:
        """Получить информацию о базовой модели"""
        base_model_path = Path(settings.MODELS_DIR) / "latest_version.json"

        if base_model_path.exists():
            with open(base_model_path, 'r') as f:
                return json.load(f)

        return None

    def get_base_model_path(self) -> Optional[str]:
        """Получить путь к базовой модели"""
        info = self.get_base_model_info()
        if not info:
            return None

        version_dir = Path(settings.MODELS_DIR) / str(info.get("version", 1))
        model_path = version_dir / "forgetting_model.tflite"

        return str(model_path) if model_path.exists() else None

    def get_latest_model_path(self, user_id: int) -> Optional[str]:
        """Получить путь к модели пользователя"""
        user_dir = Path(settings.MODELS_DIR) / f"user_{user_id}"
        version_file = user_dir / "latest_version.json"

        if version_file.exists():
            with open(version_file, 'r') as f:
                info = json.load(f)
                model_path = user_dir / str(info.get("version", 1)) / "forgetting_model.tflite"
                if model_path.exists():
                    return str(model_path)

        # Если у пользователя нет модели — возвращаем базовую
        return self.get_base_model_path()

    def get_training_status(self, user_id: int) -> Dict[str, Any]:
        """Получить статус обучения для пользователя"""
        # Используйте словарь для хранения статусов по user_id
        if not hasattr(self, '_training_statuses'):
            self._training_statuses = {}

        return self._training_statuses.get(user_id, {
            'is_running': False,
            'message': 'Готов к работе'
        })

    def set_training_status(self, user_id: int, status: Dict[str, Any]):
        """Установить статус обучения для пользователя"""
        if not hasattr(self, '_training_statuses'):
            self._training_statuses = {}

        self._training_statuses[user_id] = status

    def is_training(self, user_id: int) -> bool:
        """Проверить, идёт ли обучение для пользователя"""
        status = self.get_training_status(user_id)
        return status.get('is_running', False)

    def get_latest_version(self, user_id: int) -> Optional[Dict[str, Any]]:
        """Получить информацию о модели пользователя"""
        user_dir = Path(settings.MODELS_DIR) / f"user_{user_id}"

        if user_dir.exists():
            version_file = user_dir / "latest_version.json"
            if version_file.exists():
                with open(version_file, 'r') as f:
                    return json.load(f)

        # Если нет — возвращаем базовую
        return self.get_base_model_info()

    def train_model(self, user_id: int):
        """
        Запустить обучение модели (синтетические данные) для пользователя
        """
        # Проверяем статус для этого пользователя
        status = self.get_training_status(user_id)
        if status.get('is_running', False):
            return

        # Устанавливаем статус для пользователя
        self.set_training_status(user_id, {
            'is_running': True,
            'progress': 0,
            'message': 'Генерация данных...'
        })

        def train_task():
            try:
                import sys
                from pathlib import Path
                sys.path.insert(0, str(Path(__file__).parent.parent))
                from ml.train import train_synthetic_model

                # 🔥 СОЗДАЁМ ПАПКУ ДЛЯ ПОЛЬЗОВАТЕЛЯ
                user_dir = Path(settings.MODELS_DIR) / f"user_{user_id}"
                user_dir.mkdir(parents=True, exist_ok=True)

                self.set_training_status(user_id, {
                    'is_running': True,
                    'progress': 30,
                    'message': 'Обучение модели...'
                })

                # 🔥 ОБУЧАЕМ МОДЕЛЬ В ПАПКУ ПОЛЬЗОВАТЕЛЯ
                result = train_synthetic_model(
                    num_samples=2000,
                    output_dir=str(user_dir)  # ← сохраняем в папку пользователя
                )

                self.set_training_status(user_id, {
                    'is_running': True,
                    'progress': 80,
                    'message': 'Сохранение модели...'
                })

                # 🔥 СОХРАНЯЕМ ВЕРСИЮ В ПАПКУ ПОЛЬЗОВАТЕЛЯ
                version_info = {
                    'version': 1,
                    'file_path': result['model_path'],
                    'size_mb': Path(result['model_path']).stat().st_size / (1024 * 1024),
                    'accuracy': result['metrics'].get('accuracy', 0),
                    'mae': result['metrics'].get('mae', 0),
                    'samples': result['metadata'].get('training_samples', 0)
                }

                # Сохраняем метаданные в папку пользователя
                version_file = user_dir / "latest_version.json"
                with open(version_file, 'w') as f:
                    json.dump(version_info, f, indent=2)

                # 🔥 ТАКЖЕ СОХРАНЯЕМ В ОБЩУЮ БД (для совместимости)
                self.db.save_model_version(
                    version=version_info['version'],
                    file_path=version_info['file_path'],
                    metadata=version_info
                )

                self.set_training_status(user_id, {
                    'is_running': False,
                    'progress': 100,
                    'message': 'Обучение завершено успешно'
                })

                print(f"✅ Модель обучена для пользователя {user_id} (версия {version_info['version']})")

            except Exception as e:
                self.set_training_status(user_id, {
                    'is_running': False,
                    'progress': 0,
                    'message': f'Ошибка: {str(e)}'
                })
                print(f"❌ Ошибка обучения для пользователя {user_id}: {e}")
                import traceback
                traceback.print_exc()

        # Запускаем в отдельном потоке
        thread = threading.Thread(target=train_task, daemon=True)
        thread.start()
        self._training_thread = thread

    def fine_tune_model(self, user_id: int):
        """
        Запустить дообучение модели на данных пользователя
        """
        status = self.get_training_status(user_id)
        if status.get('is_running', False):
            return

        self.set_training_status(user_id, {
            'is_running': True,
            'progress': 0,
            'message': 'Загрузка данных...'
        })

        def fine_tune_task():
            try:
                import sys
                from pathlib import Path
                sys.path.insert(0, str(Path(__file__).parent.parent))
                from ml.fine_tune import fine_tune_model

                # 🔥 ПРОВЕРЯЕМ ЛОГИ ПОЛЬЗОВАТЕЛЯ
                count = self.db.get_count(user_id)
                if count < 50:
                    self.set_training_status(user_id, {
                        'is_running': False,
                        'progress': 0,
                        'message': f'Недостаточно данных (нужно 50, есть {count})'
                    })
                    return

                user_dir = Path(settings.MODELS_DIR) / f"user_{user_id}"
                user_dir.mkdir(parents=True, exist_ok=True)

                self.set_training_status(user_id, {
                    'is_running': True,
                    'progress': 30,
                    'message': 'Дообучение модели...'
                })

                # 🔥 НАХОДИМ ПОСЛЕДНЮЮ МОДЕЛЬ ПОЛЬЗОВАТЕЛЯ
                model_path = self.get_latest_model_path(user_id)

                if not model_path:
                    # Если нет модели — обучаем с нуля
                    self.set_training_status(user_id, {
                        'is_running': True,
                        'progress': 50,
                        'message': 'Базовая модель не найдена, обучение с нуля...'
                    })
                    self.train_model(user_id)
                    # Ждём завершения
                    import time
                    while self.get_training_status(user_id).get('is_running', False):
                        time.sleep(1)
                    model_path = self.get_latest_model_path(user_id)

                self.set_training_status(user_id, {
                    'is_running': True,
                    'progress': 60,
                    'message': 'Дообучение...'
                })

                # 🔥 ДООБУЧАЕМ МОДЕЛЬ
                result = fine_tune_model(
                    model_path=model_path,
                    output_dir=str(user_dir),
                    epochs=5,
                    learning_rate=0.0001
                )

                self.set_training_status(user_id, {
                    'is_running': False,
                    'progress': 100,
                    'message': f"Дообучение завершено (версия {result.get('version')})"
                })

                print(f"✅ Дообучение завершено для пользователя {user_id}")

            except Exception as e:
                self.set_training_status(user_id, {
                    'is_running': False,
                    'progress': 0,
                    'message': f'Ошибка: {str(e)}'
                })
                print(f"❌ Ошибка дообучения для пользователя {user_id}: {e}")
                import traceback
                traceback.print_exc()

        thread = threading.Thread(target=fine_tune_task, daemon=True)
        thread.start()
        self._training_thread = thread