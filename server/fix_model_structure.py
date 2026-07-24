# fix_model_structure.py
import shutil
from pathlib import Path
import json

user_id = 4

# Пути
user_dir = Path("models") / f"user_{user_id}"
version_dir = user_dir / "1"

print(f"📁 Папка пользователя: {user_dir}")
print(f"📁 Папка версии: {version_dir}")

# Создаём папку версии
version_dir.mkdir(parents=True, exist_ok=True)

# Копируем файлы модели в папку версии
files_to_copy = [
    "forgetting_model.keras",
    "forgetting_model.tflite",
    "model_metadata.json"
]

for file_name in files_to_copy:
    src = user_dir / file_name
    dst = version_dir / file_name
    if src.exists():
        shutil.copy2(src, dst)
        print(f"   ✅ {file_name} -> {dst}")
    else:
        print(f"   ❌ {file_name} не найден")

# Создаём latest_version.json в папке пользователя
latest_info = {
    'version': 1,
    'file_path': str(version_dir / "forgetting_model.tflite"),
    'size_mb': 0.5,
    'accuracy': 81.1,
    'mae': 0.1887,
    'samples': 2000,
    'created_at': "2026-07-21T13:19:31"
}

with open(user_dir / "latest_version.json", 'w') as f:
    json.dump(latest_info, f, indent=2)

print(f"\n📋 latest_version.json обновлён:")
print(json.dumps(latest_info, indent=2))

print(f"\n✅ Структура исправлена!")