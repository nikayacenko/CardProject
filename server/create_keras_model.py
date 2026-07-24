# create_keras_model.py
import sys
import json
import shutil
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from core.config import settings
from ml.train import train_synthetic_model

user_id = 4

print("=" * 60)
print(f"🚀 СОЗДАНИЕ KERAS МОДЕЛИ ДЛЯ ПОЛЬЗОВАТЕЛЯ {user_id}")
print("=" * 60)

# Папка пользователя
user_dir = Path(settings.MODELS_DIR) / f"user_{user_id}"
user_dir.mkdir(parents=True, exist_ok=True)

# Папка версии
version_dir = user_dir / "1"
version_dir.mkdir(parents=True, exist_ok=True)

print(f"📁 Папка пользователя: {user_dir}")

# Обучаем модель
print("\n🔄 Начинаем обучение...")
result = train_synthetic_model(
    num_samples=2000,
    output_dir=str(user_dir)
)

print(f"\n✅ Модель создана!")

# Копируем файлы в папку версии
for file_name in ["forgetting_model.keras", "forgetting_model.tflite", "model_metadata.json"]:
    src = user_dir / file_name
    dst = version_dir / file_name
    if src.exists():
        shutil.copy2(src, dst)
        print(f"   ✅ {file_name} -> {dst}")
    else:
        print(f"   ❌ {file_name} не найден")

# Создаём latest_version.json
latest_info = {
    'version': 1,
    'file_path': str(version_dir / "forgetting_model.tflite"),
    'size_mb': 0.5,
    'accuracy': (1 - result['metrics']['mae']) * 100,
    'mae': result['metrics']['mae'],
    'samples': result['metadata']['training_samples'],
    'created_at': "2026-07-21T13:19:31"
}

with open(user_dir / "latest_version.json", 'w') as f:
    json.dump(latest_info, f, indent=2)

print(f"\n📋 latest_version.json создан")

print(f"\n✅ Модель для пользователя {user_id} создана!")