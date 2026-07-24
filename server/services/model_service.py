"""
Сервис для управления ML-моделями
"""

import os
import json
import logging
import threading
from pathlib import Path
from typing import Optional, Dict, Any
from datetime import datetime

from core.config import settings
from core.database import Database
from ml.model_manager import ModelManager
from services.log_service import LogService

# Настройка логгера
logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG)


class ModelService:
    """Сервис для управления ML-моделями"""

    def __init__(self):
        self.db = Database()
        self.manager = ModelManager()
        self.log_service = LogService()
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
        user_model_path = Path(settings.MODELS_DIR) / f"user_{user_id}" / "latest_version.json"

        if user_model_path.exists():
            with open(user_model_path, 'r') as f:
                return json.load(f)

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
                # 🔥 ПРОВЕРЯЕМ РАЗНЫЕ ВАРИАНТЫ ПУТЕЙ
                possible_paths = [
                    user_dir / str(info.get("version", 1)) / "forgetting_model.tflite",
                    user_dir / "forgetting_model.tflite",
                    Path(info.get("file_path", "")),
                    ]

                for path in possible_paths:
                    if path and path.exists():
                        return str(path)

        # Если не нашли в папке пользователя, пробуем базовую
        return self.get_base_model_path()
    def get_latest_model_path(self, user_id: int) -> Optional[str]:
        """Получить путь к модели пользователя"""
        user_dir = Path(settings.MODELS_DIR) / f"user_{user_id}"
        version_file = user_dir / "latest_version.json"

        print(f"🔍 [get_latest_model_path] user_dir={user_dir}")
        print(f"🔍 [get_latest_model_path] version_file={version_file}")
        print(f"🔍 [get_latest_model_path] exists={version_file.exists()}")

        if version_file.exists():
            with open(version_file, 'r') as f:
                info = json.load(f)
                print(f"🔍 [get_latest_model_path] info={info}")

                # Пробуем разные пути
                possible_paths = [
                    user_dir / str(info.get("version", 1)) / "forgetting_model.tflite",
                    user_dir / "forgetting_model.tflite",
                    Path(info.get("file_path", "")),
                    ]

                for path in possible_paths:
                    print(f"🔍 [get_latest_model_path] проверяем: {path}")
                    if path and path.exists():
                        print(f"✅ [get_latest_model_path] найден: {path}")
                        return str(path)

                print(f"⚠️ [get_latest_model_path] ни один путь не существует")

        print(f"🔍 [get_latest_model_path] ищем базовую модель")
        return self.get_base_model_path()

    def get_training_status(self, user_id: int) -> Dict[str, Any]:
        """Получить статус обучения для пользователя"""
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

    def train_model(self, user_id: int):
        """Запустить обучение модели (синтетические данные) для пользователя"""
        status = self.get_training_status(user_id)
        if status.get('is_running', False):
            return

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

                user_dir = Path(settings.MODELS_DIR) / f"user_{user_id}"
                user_dir.mkdir(parents=True, exist_ok=True)

                self.set_training_status(user_id, {
                    'is_running': True,
                    'progress': 30,
                    'message': 'Обучение модели...'
                })

                result = train_synthetic_model(
                    num_samples=2000,
                    output_dir=str(user_dir)
                )

                self.set_training_status(user_id, {
                    'is_running': True,
                    'progress': 80,
                    'message': 'Сохранение модели...'
                })

                version_info = {
                    'version': 1,
                    'file_path': result['model_path'],
                    'size_mb': Path(result['model_path']).stat().st_size / (1024 * 1024),
                    'accuracy': result['metrics'].get('accuracy', 0),
                    'mae': result['metrics'].get('mae', 0),
                    'samples': result['metadata'].get('training_samples', 0)
                }

                version_file = user_dir / "latest_version.json"
                with open(version_file, 'w') as f:
                    json.dump(version_info, f, indent=2)

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

                logger.info(f"✅ Модель обучена для пользователя {user_id}")

            except Exception as e:
                self.set_training_status(user_id, {
                    'is_running': False,
                    'progress': 0,
                    'message': f'Ошибка: {str(e)}'
                })
                logger.error(f"❌ Ошибка обучения для пользователя {user_id}: {e}")
                import traceback
                traceback.print_exc()

        thread = threading.Thread(target=train_task, daemon=True)
        thread.start()
        self._training_thread = thread

    def fine_tune_model(self, user_id: int):
        """
        Запустить дообучение модели на данных пользователя
        """
        print(f"🔵 [FINE-TUNE] Начало для user_id={user_id}")

        status = self.get_training_status(user_id)

        # 🔥 Проверяем, не зависло ли обучение
        if status.get('is_running', False):
            # Если обучение запущено больше 5 минут - сбрасываем
            print(f"⚠️ [FINE-TUNE] Обучение уже запущено, сбрасываем...")
            self.set_training_status(user_id, {
                'is_running': False,
                'progress': 0,
                'message': 'Статус сброшен (перезапуск)'
            })

        self.set_training_status(user_id, {
            'is_running': True,
            'progress': 0,
            'message': 'Загрузка данных...'
        })

        def fine_tune_task():
            from pathlib import Path
            import sys
            import time

            try:
                print(f"🔵 [FINE-TUNE-TASK] Запуск для user_id={user_id}")

                # 1. Проверяем количество логов
                count = self.log_service.get_count(user_id)
                print(f"📊 [FINE-TUNE] У пользователя {user_id} логов: {count}")

                if count < 5:
                    print(f"⚠️ [FINE-TUNE] Недостаточно данных: {count} < 50")
                    self.set_training_status(user_id, {
                        'is_running': False,
                        'progress': 0,
                        'message': f'Недостаточно данных (нужно 50, есть {count})'
                    })
                    return

                user_dir = Path(settings.MODELS_DIR) / f"user_{user_id}"
                user_dir.mkdir(parents=True, exist_ok=True)
                print(f"📁 [FINE-TUNE] Папка пользователя: {user_dir}")

                # 2. Ищем модель
                model_path = self.get_latest_model_path(user_id)
                print(f"🔍 [FINE-TUNE] Найден путь модели: {model_path}")

                if not model_path:
                    print(f"⚠️ [FINE-TUNE] Модель не найдена, создаём базовую...")
                    self.set_training_status(user_id, {
                        'is_running': True,
                        'progress': 30,
                        'message': 'Создание базовой модели...'
                    })

                    # 🔥 СОЗДАЁМ БАЗОВУЮ МОДЕЛЬ (НЕ ЧЕРЕЗ train_model)
                    sys.path.insert(0, str(Path(__file__).parent.parent))
                    from ml.train import train_synthetic_model

                    result = train_synthetic_model(
                        num_samples=2000,
                        output_dir=str(user_dir)
                    )

                    # Обновляем latest_version.json
                    version_info = {
                        'version': 1,
                        'file_path': result['model_path'],
                        'size_mb': Path(result['model_path']).stat().st_size / (1024 * 1024),
                        'accuracy': result['metrics'].get('accuracy', 0),
                        'mae': result['metrics'].get('mae', 0),
                        'samples': result['metadata'].get('training_samples', 0)
                    }

                    version_file = user_dir / "latest_version.json"
                    with open(version_file, 'w') as f:
                        json.dump(version_info, f, indent=2)

                    model_path = self.get_latest_model_path(user_id)
                    print(f"🔍 [FINE-TUNE] Базовая модель создана: {model_path}")

                if not model_path:
                    error_msg = "Не удалось создать базовую модель"
                    print(f"❌ [FINE-TUNE] {error_msg}")
                    self.set_training_status(user_id, {
                        'is_running': False,
                        'progress': 0,
                        'message': error_msg
                    })
                    return

                self.set_training_status(user_id, {
                    'is_running': True,
                    'progress': 60,
                    'message': 'Дообучение...'
                })

                # 3. Запускаем дообучение
                print(f"🧠 [FINE-TUNE] Запуск дообучения с моделью: {model_path}")

                sys.path.insert(0, str(Path(__file__).parent.parent))
                from services.fine_tune_service import fine_tune_model as fine_tune_func

                result = fine_tune_func(
                    model_path=model_path,
                    output_dir=str(user_dir),
                    user_id=user_id,
                    epochs=5,
                    learning_rate=0.0001
                )

                print(f"📊 [FINE-TUNE] Результат: {result}")

                if result.get('status') == 'success':
                    self.set_training_status(user_id, {
                        'is_running': False,
                        'progress': 100,
                        'message': f"Дообучение завершено (версия {result.get('version')})"
                    })
                    print(f"✅ [FINE-TUNE] Успешно для user_id={user_id}")
                else:
                    self.set_training_status(user_id, {
                        'is_running': False,
                        'progress': 0,
                        'message': result.get('message', 'Ошибка дообучения')
                    })
                    print(f"❌ [FINE-TUNE] Ошибка: {result.get('message')}")

            except Exception as e:
                error_msg = f"Ошибка: {str(e)}"
                print(f"❌ [FINE-TUNE] {error_msg}")
                import traceback
                traceback.print_exc()
                self.set_training_status(user_id, {
                    'is_running': False,
                    'progress': 0,
                    'message': error_msg
                })

        thread = threading.Thread(target=fine_tune_task, daemon=True)
        thread.start()
        self._training_thread = thread
        print(f"🔵 [FINE-TUNE] Поток запущен для user_id={user_id}")