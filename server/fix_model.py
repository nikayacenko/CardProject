# fix_model.py
import json
from pathlib import Path

user_id = 4

# Путь к папке пользователя
user_dir = Path("models") / f"user_{user_id}"
user_dir.mkdir(parents=True, exist_ok=True)

print(f"📁 Папка пользователя: {user_dir}")

# Проверяем, есть ли файлы модели
model_file = user_dir / "forgetting_model.tflite"
if not model_file.exists():
    print(f"❌ Модель не найдена: {model_file}")
    exit(1)

print(f"✅ Модель найдена: {model_file}")

# Создаём latest_version.json
latest_info = {
    'version': 1,
    'file_path': str(model_file),
    'size_mb': round(model_file.stat().st_size / (1024 * 1024), 2),
    'accuracy': 81.1,
    'mae': 0.1887,
    'samples': 2000,
    'created_at': "2026-07-21T13:19:31"
}

latest_file = user_dir / "latest_version.json"
with open(latest_file, 'w') as f:
    json.dump(latest_info, f, indent=2)

print(f"✅ latest_version.json создан:")
print(json.dumps(latest_info, indent=2))

# Также создаём папку с версией (для совместимости)
version_dir = user_dir / "1"
version_dir.mkdir(parents=True, exist_ok=True)

# Копируем модель в папку версии
import shutil
shutil.copy2(model_file, version_dir / "forgetting_model.tflite")
shutil.copy2(user_dir / "forgetting_model.keras", version_dir / "forgetting_model.keras")

print(f"✅ Модель скопирована в папку версии: {version_dir}")