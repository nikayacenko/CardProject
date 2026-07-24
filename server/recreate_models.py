# recreate_models.py
import sys
import json
import shutil
import tensorflow as tf
from pathlib import Path

print(f"🔍 TensorFlow version: {tf.__version__}")
print("=" * 60)
print("🔄 ПЕРЕСОЗДАНИЕ МОДЕЛИ ДЛЯ TF 2.16")
print("=" * 60)

# Пути
current_dir = Path(__file__).parent
parent_dir = current_dir.parent
ml_training_dir = parent_dir / 'ml_training'

print(f"📁 Текущая директория (server): {current_dir}")
print(f"📁 Родительская директория (CardProject): {parent_dir}")
print(f"📁 Путь к ml_training: {ml_training_dir}")
print(f"📁 Существует: {ml_training_dir.exists()}")

# Добавляем пути
sys.path.insert(0, str(ml_training_dir))
sys.path.insert(0, str(current_dir))

import os
if ml_training_dir.exists():
    print(f"📁 Файлы в ml_training: {os.listdir(ml_training_dir)}")
else:
    print(f"❌ Папка ml_training не найдена по пути: {ml_training_dir}")
    sys.exit(1)

from core.config import settings

try:
    from train_model import SyntheticDataGenerator, DataPreprocessor
    print("✅ Импорт успешен")
except ImportError as e:
    print(f"❌ Ошибка импорта: {e}")
    sys.exit(1)

# 1. Очищаем папку моделей
models_dir = Path(settings.MODELS_DIR)
if models_dir.exists():
    shutil.rmtree(models_dir)
models_dir.mkdir(parents=True, exist_ok=True)

print(f"\n📁 Папка моделей: {models_dir}")

# 2. Генерируем данные
print("\n📊 Генерация данных...")
generator = SyntheticDataGenerator(3000)
df = generator.generate()
print(f"   ✅ Сгенерировано {len(df)} записей")

# 3. Подготовка признаков
print("\n🔄 Подготовка признаков...")
preprocessor = DataPreprocessor()
X = preprocessor.prepare_features(df)
y = preprocessor.prepare_targets(df)

from sklearn.model_selection import train_test_split
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)
X_train, X_val, y_train, y_val = train_test_split(X_train, y_train, test_size=0.2, random_state=42)

print(f"   📊 Train: {X_train.shape}, Val: {X_val.shape}, Test: {X_test.shape}")

# 4. Создаем модель (БЕЗ BatchNormalization)
print("\n🧠 Создание модели...")
model = tf.keras.Sequential([
    tf.keras.layers.Dense(64, activation='relu', input_shape=(20,),
                          kernel_regularizer=tf.keras.regularizers.l2(0.001)),
    tf.keras.layers.Dropout(0.3),

    tf.keras.layers.Dense(32, activation='relu',
                          kernel_regularizer=tf.keras.regularizers.l2(0.001)),
    tf.keras.layers.Dropout(0.3),

    tf.keras.layers.Dense(16, activation='relu'),
    tf.keras.layers.Dense(3, activation='sigmoid')
])

model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
    loss='mse',
    metrics=['mae']
)

print("✅ Модель создана")

# 5. Обучение
print("\n🚀 Обучение модели...")
history = model.fit(
    X_train, y_train,
    validation_data=(X_val, y_val),
    epochs=30,
    batch_size=32,
    verbose=1
)

# 6. Оценка
print("\n📊 Оценка модели...")
loss, mae = model.evaluate(X_test, y_test, verbose=0)
accuracy = (1 - mae) * 100
print(f"   📉 Loss: {loss:.4f}")
print(f"   📏 MAE: {mae:.4f}")
print(f"   🎯 Точность: {accuracy:.1f}%")

# 7. Сохраняем Keras
print("\n💾 Сохранение модели...")
keras_path = models_dir / "forgetting_model.keras"
model.save(str(keras_path))
print(f"   ✅ Keras: {keras_path}")

# 8. КОНВЕРТАЦИЯ ДЛЯ TF 2.16
print("\n🔄 Конвертация в TFLite (TF 2.16 + Keras 3)...")
tflite_path = models_dir / "forgetting_model.tflite"

try:
    import tempfile

    # Keras 3 требует явного экспорта SavedModel через model.export
    with tempfile.TemporaryDirectory() as tmpdir:
        exported_path = Path(tmpdir) / "exported_tf_model"

        print("   📦 Экспорт SavedModel через Keras 3 API...")
        model.export(str(exported_path), format="tf_saved_model")

        print("   🔄 Конвертация SavedModel -> TFLite...")
        converter = tf.lite.TFLiteConverter.from_saved_model(str(exported_path))

        # Оптимизации (по желанию)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]

        tflite_model = converter.convert()

        with open(tflite_path, 'wb') as f:
            f.write(tflite_model)

    print(f"   ✅ TFLite сохранен: {tflite_path}")
    print(f"   📦 Размер: {tflite_path.stat().st_size / 1024:.1f} KB")

except Exception as e:
    print(f"   ❌ Ошибка экспорта Keras 3: {e}")

    # Запасной вариант: Прямая конвертация Keras -> TFLite без ConcreteFunction
    try:
        print("   🔄 Запасной вариант: from_keras_model...")
        converter = tf.lite.TFLiteConverter.from_keras_model(model)
        tflite_model = converter.convert()

        with open(tflite_path, 'wb') as f:
            f.write(tflite_model)

        print(f"   ✅ TFLite сохранен (from_keras_model): {tflite_path}")
    except Exception as e2:
        print(f"   ❌ Запасной вариант не сработал: {e2}")
        sys.exit(1)

    try:
        # Способ 2: Конвертация через SavedModel
        print("   🔄 Способ 2: Конвертация через SavedModel...")

        import tempfile
        with tempfile.TemporaryDirectory() as tmpdir:
            saved_model_path = Path(tmpdir) / "saved_model"
            model.save(str(saved_model_path))

            converter = tf.lite.TFLiteConverter.from_saved_model(str(saved_model_path))
            tflite_model = converter.convert()

            with open(tflite_path, 'wb') as f:
                f.write(tflite_model)

        print(f"   ✅ TFLite сохранен (через SavedModel): {tflite_path}")
        print(f"   📦 Размер: {tflite_path.stat().st_size / 1024:.1f} KB")

    except Exception as e2:
        print(f"   ❌ Способ 2 не удался: {e2}")

        try:
            # Способ 3: Без оптимизаций
            print("   🔄 Способ 3: Без оптимизаций...")

            converter = tf.lite.TFLiteConverter.from_keras_model(model)
            converter.optimizations = []
            tflite_model = converter.convert()

            with open(tflite_path, 'wb') as f:
                f.write(tflite_model)

            print(f"   ✅ TFLite сохранен (без оптимизаций): {tflite_path}")
            print(f"   📦 Размер: {tflite_path.stat().st_size / 1024:.1f} KB")

        except Exception as e3:
            print(f"   ❌ Все способы не удались: {e3}")
            sys.exit(1)

# 9. Метаданные
metadata = {
    'model_name': 'Forgetting Probability Model',
    'version': 1,
    'created_at': str(Path(settings.MODELS_DIR)),
    'input_features': 20,
    'output_features': 3,
    'test_mae': float(mae),
    'test_accuracy': float(accuracy),
}

with open(models_dir / 'model_metadata.json', 'w') as f:
    json.dump(metadata, f, indent=2)

# 10. latest_version.json
latest_info = {
    'version': 1,
    'file_path': str(tflite_path),
    'size_mb': round(tflite_path.stat().st_size / (1024 * 1024), 2),
    'accuracy': accuracy,
    'mae': mae,
    'samples': len(X_train),
    'created_at': "2026-07-22T08:00:00"
}

with open(models_dir / "latest_version.json", 'w') as f:
    json.dump(latest_info, f, indent=2)

print(f"\n✅ latest_version.json создан")

# 11. Модель для пользователя
print("\n📁 Создание модели для пользователя 4...")
user_id = 4
user_dir = models_dir / f"user_{user_id}"
user_dir.mkdir(parents=True, exist_ok=True)

for file_name in ["forgetting_model.keras", "forgetting_model.tflite", "model_metadata.json"]:
    src = models_dir / file_name
    dst = user_dir / file_name
    if src.exists():
        shutil.copy2(src, dst)
        print(f"   ✅ {file_name} скопирован")

version_dir = user_dir / "1"
version_dir.mkdir(parents=True, exist_ok=True)

for file_name in ["forgetting_model.keras", "forgetting_model.tflite", "model_metadata.json"]:
    src = user_dir / file_name
    dst = version_dir / file_name
    if src.exists():
        shutil.copy2(src, dst)

with open(user_dir / "latest_version.json", 'w') as f:
    json.dump(latest_info, f, indent=2)

print(f"   ✅ Модель для пользователя {user_id} создана")

# 12. Проверка
print("\n🧪 Проверка TFLite...")
try:
    interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print(f"   ✅ Модель загружается корректно!")
    print(f"   📥 Вход: {input_details[0]['shape']} ({input_details[0]['dtype']})")
    print(f"   📤 Выход: {output_details[0]['shape']} ({output_details[0]['dtype']})")

except Exception as e:
    print(f"   ❌ Ошибка загрузки: {e}")

print("\n" + "=" * 60)
print("✅ ГОТОВО!")
print("=" * 60)
print(f"\n📁 Пути:")
print(f"   Базовая модель: {models_dir}")
print(f"   Модель пользователя: {user_dir}")
print(f"   TFLite: {tflite_path}")