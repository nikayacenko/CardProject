"""
Управление ML-моделями: версионирование, хранение
"""

import os
import json
import shutil
from pathlib import Path
from typing import Optional
from datetime import datetime
from typing import Optional, Dict, Any, List
from core.config import settings


class ModelManager:
    """Управление версиями ML-моделей"""

    def __init__(self):
        self.models_dir = Path(settings.MODELS_DIR)
        self.models_dir.mkdir(parents=True, exist_ok=True)

    def get_latest_version(self) -> Optional[dict]:
        """Получить информацию о последней версии модели"""
        version_file = self.models_dir / "latest_version.json"
        if not version_file.exists():
            return None

        with open(version_file, 'r') as f:
            return json.load(f)

    def get_latest_model_path(self) -> Optional[str]:
        """Получить путь к последней модели"""
        version_info = self.get_latest_version()
        if not version_info:
            return None

        version_dir = self.models_dir / str(version_info["version"])
        model_path = version_dir / "forgetting_model.tflite"

        if not model_path.exists():
            return None

        return str(model_path)

    def save_model(self, source_path: str, metadata: dict) -> int:
        """
        Сохранить новую версию модели

        Returns:
            Номер версии
        """
        # Определяем следующий номер версии
        existing = []
        for d in self.models_dir.iterdir():
            if d.is_dir() and d.name.isdigit():
                existing.append(int(d.name))

        new_version = max(existing) + 1 if existing else 1

        # Создаём папку версии
        version_dir = self.models_dir / str(new_version)
        version_dir.mkdir()

        # Копируем модель
        dest_path = version_dir / "forgetting_model.tflite"
        shutil.copy2(source_path, dest_path)

        # Сохраняем метаданные
        metadata["version"] = new_version
        metadata["created_at"] = datetime.now().isoformat()
        metadata["size_mb"] = round(dest_path.stat().st_size / (1024 * 1024), 2)

        with open(version_dir / "metadata.json", "w") as f:
            json.dump(metadata, f, indent=2)

        # Обновляем latest
        with open(self.models_dir / "latest_version.json", "w") as f:
            json.dump(metadata, f, indent=2)

        return new_version

    def get_all_versions(self) -> list:
        """Получить список всех версий"""
        versions = []
        for d in self.models_dir.iterdir():
            if d.is_dir() and d.name.isdigit():
                metadata_file = d / "metadata.json"
                if metadata_file.exists():
                    with open(metadata_file, 'r') as f:
                        data = json.load(f)
                        versions.append(data)

        return sorted(versions, key=lambda x: x.get("version", 0))