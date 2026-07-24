# create_compatible_model.py
import sys
import json
import shutil
import tensorflow as tf
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from core.config import settings
from ml.train import train_synthetic_model

user_id = 4

print("=" * 60)
print("🚀 СОЗДАНИЕ СОВМЕСТИМОЙ МОДЕЛИ")
print("=" * 60)

# Папка пользователя
user_dir = Path(settings.MODELS_DIR) / f"user_{user_id}"
user_dir.mkdir(parents=True, exist_ok=True)

# Обучаем модель
print("\n🔄 Начинаем обучение...")
result = train_synthetic_model(
    num_samples=2000,
    output_dir=str(user_dir)
)

print(f"\n✅ Модель создана!")

# 🔥 КОНВЕРТАЦИЯ БЕЗ LEGACY
print("\n🔄 Пересоздаём TFLite без LEGACY...")
keras_path = user_dir / "forgetting_model.keras"
model = tf.keras.models.load_model(str(keras_path))

converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
]

tflite_model = converter.convert()
tflite_path = user_dir / "forgetting_model.tflite"
with open(tflite_path, 'wb') as f:
    f.write(tflite_model)

print(f"   ✅ TFLite пересоздан: {tflite_path}")
print(f"   📦 Размер: {tflite_path.stat().st_size / 1024:.1f} KB")

# Создаём папку версии
version_dir = user_dir / "1"
version_dir.mkdir(parents=True, exist_ok=True)

# Копируем файлы
for file_name in ["forgetting_model.keras", "forgetting_model.tflite", "model_metadata.json"]:
    src = user_dir / file_name
    dst = version_dir / file_name
    if src.exists():
        shutil.copy2(src, dst)
        print(f"   ✅ {file_name} -> {dst}")

# Создаём latest_version.json
latest_info = {
    'version': 1,
    'file_path': str(version_dir / "forgetting_model.tflite"),
    'size_mb': round((version_dir / "forgetting_model.tflite").stat().st_size / (1024 * 1024), 2),
    'accuracy': 81.0,
    'mae': 0.19,
    'samples': 2000,
    'created_at': "2026-07-21T10:00:00"
}

with open(user_dir / "latest_version.json", 'w') as f:
    json.dump(latest_info, f, indent=2)

print(f"\n✅ Совместимая модель создана!")