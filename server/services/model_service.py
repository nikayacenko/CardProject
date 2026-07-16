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

    def get_latest_version(self) -> Optional[Dict[str, Any]]:
        """Получить информацию о последней модели"""
        # Сначала проверяем БД
        db_info = self.db.get_latest_model_version()
        if db_info:
            return {
                'version': db_info['version'],
                'url': f"/api/model/download",
                'size_mb': db_info['size_mb'],
                'created_at': db_info['created_at'],
                'accuracy': db_info.get('accuracy'),
                'mae': db_info.get('mae'),
                'samples': db_info.get('samples')
            }

        # Если в БД нет — проверяем файловую систему
        fs_info = self.manager.get_latest_version()
        if fs_info:
            return fs_info

        return None

    def get_latest_model_path(self) -> Optional[str]:
        """Получить путь к файлу модели"""
        # Сначала проверяем БД
        db_info = self.db.get_latest_model_version()
        if db_info and os.path.exists(db_info['file_path']):
            return db_info['file_path']

        # Если нет — проверяем файловую систему
        fs_path = self.manager.get_latest_model_path()
        if fs_path and os.path.exists(fs_path):
            return fs_path

        return None

    def get_training_status(self) -> Dict[str, Any]:
        """Получить статус обучения"""
        return self._training_status

    def train_model(self):  # ← УБРАТЬ async (это обычная функция)
        """Запустить обучение модели (синтетические данные)"""
        if self._training_status['is_running']:
            return

        self._training_status['is_running'] = True
        self._training_status['message'] = 'Генерация данных...'

        def train_task():
            try:
                import sys
                from pathlib import Path
                sys.path.insert(0, str(Path(__file__).parent.parent))
                from ml.train import train_synthetic_model

                self._training_status['message'] = 'Обучение модели...'
                self._training_status['progress'] = 30

                # Запускаем обучение
                result = train_synthetic_model(
                    num_samples=2000,
                    output_dir=settings.MODELS_DIR
                )

                self._training_status['message'] = 'Сохранение модели...'
                self._training_status['progress'] = 80

                # Сохраняем в БД
                version_info = {
                    'version': 1,
                    'file_path': result['model_path'],
                    'size_mb': Path(result['model_path']).stat().st_size / (1024 * 1024),
                    'accuracy': result['metrics'].get('accuracy', 0),
                    'mae': result['metrics'].get('mae', 0),
                    'samples': result['metadata'].get('training_samples', 0)
                }

                self.db.save_model_version(
                    version=version_info['version'],
                    file_path=version_info['file_path'],
                    metadata=version_info
                )

                self._training_status['is_running'] = False
                self._training_status['progress'] = 100
                self._training_status['message'] = 'Обучение завершено успешно'

                print(f"✅ Модель обучена (версия {version_info['version']})")

            except Exception as e:
                self._training_status['is_running'] = False
                self._training_status['message'] = f'Ошибка: {str(e)}'
                print(f"❌ Ошибка обучения: {e}")
                import traceback
                traceback.print_exc()

        # Запускаем в отдельном потоке
        thread = threading.Thread(target=train_task, daemon=True)
        thread.start()
        self._training_thread = thread

    def fine_tune_model(self):  # ← УБРАТЬ async
        """Запустить дообучение модели на реальных данных"""
        if self._training_status['is_running']:
            return

        self._training_status['is_running'] = True
        self._training_status['message'] = 'Загрузка данных...'

        def fine_tune_task():
            try:
                import sys
                from pathlib import Path
                sys.path.insert(0, str(Path(__file__).parent.parent))
                from ml.fine_tune import fine_tune_model

                # Проверяем количество логов
                count = self.db.get_logs_count()
                if count < 50:
                    self._training_status['is_running'] = False
                    self._training_status['message'] = f'Недостаточно данных (нужно 50, есть {count})'
                    return

                self._training_status['message'] = 'Дообучение модели...'
                self._training_status['progress'] = 30

                # Ищем существующую модель
                model_path = self.get_latest_model_path()
                if not model_path:
                    # Если нет модели — сначала обучаем синтетическую
                    self._training_status['message'] = 'Базовая модель не найдена, обучение с нуля...'
                    self.train_model()
                    # Ждём завершения
                    import time
                    while self._training_status['is_running']:
                        time.sleep(1)
                    model_path = self.get_latest_model_path()

                self._training_status['message'] = 'Дообучение...'
                self._training_status['progress'] = 50

                result = fine_tune_model(
                    model_path=model_path,
                    output_dir=settings.MODELS_DIR,
                    epochs=5,
                    learning_rate=0.0001
                )

                self._training_status['is_running'] = False
                self._training_status['progress'] = 100
                if result.get('status') == 'success':
                    self._training_status['message'] = f"Дообучение завершено (версия {result.get('version')})"
                else:
                    self._training_status['message'] = result.get('message', 'Ошибка дообучения')

            except Exception as e:
                self._training_status['is_running'] = False
                self._training_status['message'] = f'Ошибка: {str(e)}'
                print(f"❌ Ошибка дообучения: {e}")
                import traceback
                traceback.print_exc()

        thread = threading.Thread(target=fine_tune_task, daemon=True)
        thread.start()
        self._training_thread = thread