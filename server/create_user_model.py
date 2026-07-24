# create_user_model.py
import sys
import json
from pathlib import Path

# Добавляем текущую папку в путь для импорта
sys.path.insert(0, str(Path(__file__).parent))

from core.config import settings
from ml.train import train_synthetic_model

user_id = 4  # ID пользователя nika

print("=" * 60)
print(f"🚀 СОЗДАНИЕ МОДЕЛИ ДЛЯ ПОЛЬЗОВАТЕЛЯ {user_id}")
print("=" * 60)

# Папка пользователя
user_dir = Path(settings.MODELS_DIR) / f"user_{user_id}"
user_dir.mkdir(parents=True, exist_ok=True)
print(f"📁 Папка пользователя: {user_dir}")

# Обучаем модель
print("\n🔄 Начинаем обучение...")
result = train_synthetic_model(
    num_samples=2000,
    output_dir=str(user_dir)
)

print(f"\n✅ Модель создана!")
print(f"   📁 Путь: {result['model_path']}")
print(f"   📊 MAE: {result['metrics']['mae']:.4f}")
print(f"   🎯 Точность: {(1 - result['metrics']['mae']) * 100:.1f}%")

# Проверяем latest_version.json
latest_file = user_dir / "latest_version.json"
if latest_file.exists():
    with open(latest_file, 'r') as f:
        data = json.load(f)
        print(f"\n📋 latest_version.json:")
        print(json.dumps(data, indent=2))
else:
    print("\n❌ latest_version.json не создан!")

print(f"\n✅ Модель для пользователя {user_id} создана!")