# services/fine_tune_service.py - исправленная версия

"""
Дообучение ML-модели на реальных данных пользователя
"""

import json
import pandas as pd
import numpy as np
import tensorflow as tf
from pathlib import Path
from typing import Optional, Dict, Any
from datetime import datetime

from core.config import settings
from services.log_service import LogService


def fine_tune_model(
        model_path: Optional[str] = None,
        output_dir: Optional[str] = None,
        user_id: Optional[int] = None,
        epochs: int = 5,
        learning_rate: float = 0.0001
) -> Dict[str, Any]:
    """
    Дообучение модели на реальных логах пользователя
    """
    print("=" * 60)
    print("🔄 ДООБУЧЕНИЕ ML-МОДЕЛИ НА РЕАЛЬНЫХ ДАННЫХ")
    print("=" * 60)

    # 1. Загружаем логи пользователя
    print("\n📊 Загрузка логов из БД...")
    log_service = LogService()

    if user_id is not None:
        logs = log_service.get_logs_for_user(user_id, limit=5000)
    else:
        logs = log_service.get_logs_for_training(limit=5000)

    if len(logs) < 5:  # временно для теста (было 50)
        print(f"   ⚠️ Недостаточно данных (нужно 50, есть {len(logs)})")
        return {'status': 'skipped', 'message': f'Недостаточно данных (нужно 50, есть {len(logs)})'}

    print(f"   ✅ Загружено {len(logs)} записей")

    # 2. Преобразуем в DataFrame
    df = pd.DataFrame(logs, columns=[
        'card_text_length', 'has_formulas', 'question_type',
        'response_time_ms', 'hour_of_day', 'day_of_week', 'fatigue_level',
        'cards_reviewed_in_session', 'total_reviews_before', 'correct_rate_before',
        'streak_correct_before', 'days_since_last_review', 'linked_cards_count',
        'linked_cards_mastery', 'was_correct'
    ])

    # Кодируем тип вопроса
    df['question_type_encoded'] = df['question_type'].map({
        'FACT': 0.1,
        'DEFINITION': 0.4,
        'PROOF': 0.9
    }).fillna(0.5)

    # 3. Подготавливаем признаки
    def prepare_features(data):
        features = np.array([
            data['card_text_length'] / 1000,
            data['has_formulas'],
            data['question_type_encoded'],
            data['response_time_ms'] / 30000,
            data['hour_of_day'] / 24,
            data['day_of_week'] / 7,
            data['fatigue_level'],
            data['cards_reviewed_in_session'] / 50,
            data['total_reviews_before'] / 20,
            data['correct_rate_before'],
            data['streak_correct_before'] / 10,
            data['days_since_last_review'] / 30,
            data['linked_cards_count'] / 10,
            data['linked_cards_mastery'],
            data['fatigue_level'] * (data['response_time_ms'] / 30000),
            data['correct_rate_before'] * (data['days_since_last_review'] / 30),
            np.zeros(len(data)),
            np.zeros(len(data)),
            np.zeros(len(data)),
            np.zeros(len(data))
        ]).T
        return features.astype(np.float32)

    def prepare_targets(data):
        forgetting = 1 - data['was_correct'].values

        interval = []
        for _, row in data.iterrows():
            if row['was_correct'] == 1:
                base = min(30, 1 + row['total_reviews_before'] * 0.5)
                if row['fatigue_level'] > 0.7:
                    base *= 0.8
                if row['has_formulas'] == 1:
                    base *= 0.9
            else:
                base = 1.0
            interval.append(base / 30)

        confidence = np.ones(len(data)) * 0.8
        return np.column_stack([forgetting, interval, confidence])

    X = prepare_features(df)
    y = prepare_targets(df)

    print(f"   📊 Признаки: {X.shape}")
    print(f"   📊 Правильных ответов: {df['was_correct'].sum()} ({df['was_correct'].mean()*100:.1f}%)")

    # 4. Загружаем существующую модель
    # 🔥 ИСПРАВЛЕНО: ищем .keras файл, а не .tflite
    keras_path = None

    if model_path is not None:
        # Если передан путь, пробуем найти .keras файл рядом
        model_path = Path(model_path)
        if model_path.suffix == '.tflite':
            # Заменяем .tflite на .keras
            keras_path = model_path.parent / 'forgetting_model.keras'
            if not keras_path.exists():
                keras_path = model_path.parent.parent / 'forgetting_model.keras'
        else:
            keras_path = model_path

    # Если не нашли, ищем в папке пользователя
    if keras_path is None or not keras_path.exists():
        if user_id is not None:
            user_dir = Path(settings.MODELS_DIR) / f"user_{user_id}"
            possible_paths = [
                user_dir / "forgetting_model.keras",
                user_dir / "1" / "forgetting_model.keras",
                user_dir / ".." / "forgetting_model.keras",
                ]
            for path in possible_paths:
                if path.exists():
                    keras_path = path
                    print(f"   ✅ Найден Keras файл: {keras_path}")
                    break

    # Если не нашли, ищем в общей папке
    if keras_path is None or not keras_path.exists():
        models_dir = Path(settings.MODELS_DIR)
        for version_dir in sorted([d for d in models_dir.iterdir() if d.is_dir() and d.name.isdigit()], reverse=True):
            path = version_dir / "forgetting_model.keras"
            if path.exists():
                keras_path = path
                print(f"   ✅ Найден Keras файл: {keras_path}")
                break

    if keras_path is None or not keras_path.exists():
        print(f"   ❌ Keras модель не найдена!")
        return {'status': 'error', 'message': 'Keras модель не найдена'}

    print(f"\n📥 Загрузка модели: {keras_path}")

    # 🔥 ИСПРАВЛЕНО: загружаем .keras файл
    try:
        model = tf.keras.models.load_model(str(keras_path))
        print("   ✅ Модель загружена успешно")
    except Exception as e:
        print(f"   ❌ Ошибка загрузки: {e}")
        return {'status': 'error', 'message': f'Ошибка загрузки модели: {e}'}

    # 5. Замораживаем первые слои
    print("🔒 Заморозка первых слоёв...")
    for i, layer in enumerate(model.layers[:3]):
        layer.trainable = False
        print(f"   • Слой {i} ({layer.name}) заморожен")

    # 6. Дообучаем
    print(f"\n🚀 Дообучение ({epochs} эпох, lr={learning_rate})...")
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=learning_rate),
        loss='mse',
        metrics=['mae']
    )

    history = model.fit(
        X, y,
        epochs=epochs,
        batch_size=16,
        validation_split=0.2,
        verbose=1
    )

    print(f"\n✅ Дообучение завершено")
    print(f"   📉 Финальная loss: {history.history['loss'][-1]:.4f}")
    print(f"   📏 Финальная MAE: {history.history['mae'][-1]:.4f}")

    # 7. Сохраняем модель
    if output_dir is None:
        output_dir = Path(settings.MODELS_DIR)
    else:
        output_dir = Path(output_dir)

    # Определяем версию
    existing_versions = []
    for d in output_dir.iterdir():
        if d.is_dir() and d.name.isdigit():
            existing_versions.append(int(d.name))
    new_version = max(existing_versions) + 1 if existing_versions else 1

    version_dir = output_dir / str(new_version)
    version_dir.mkdir(parents=True, exist_ok=True)

    # Сохраняем Keras
    keras_path_new = version_dir / 'forgetting_model.keras'
    model.save(str(keras_path_new))

    # Конвертируем в TFLite
    print("🔄 Конвертация в TFLite...")
    tflite_path = version_dir / 'forgetting_model.tflite'

    try:
        # Способ 1: Конвертация с заморозкой всех слоёв
        print("   🔄 Способ 1: Стандартная конвертация...")

        # 🔥 ВАЖНО: Сначала замораживаем все слои
        for layer in model.layers:
            layer.trainable = False
            if isinstance(layer, tf.keras.layers.BatchNormalization):
                print(f"   🔒 Заморожен BatchNormalization: {layer.name}")

        # Создаём новый конвертер
        converter = tf.lite.TFLiteConverter.from_keras_model(model)

        # 🔥 ТОЛЬКО СТАНДАРТНЫЕ ОПЕРАЦИИ (без оптимизаций)
        converter.optimizations = []
        converter.target_spec.supported_ops = [
            tf.lite.OpsSet.TFLITE_BUILTINS,
        ]

        tflite_model = converter.convert()

        with open(tflite_path, 'wb') as f:
            f.write(tflite_model)

        print(f"   ✅ TFLite сохранен: {tflite_path}")
        print(f"   📦 Размер: {tflite_path.stat().st_size / 1024:.1f} KB")

    except Exception as e:
        print(f"   ❌ Способ 1 не удался: {e}")

        try:
            # Способ 2: Через SavedModel
            print("   🔄 Способ 2: Конвертация через SavedModel...")

            import tempfile
            with tempfile.TemporaryDirectory() as tmpdir:
                saved_model_path = Path(tmpdir) / "saved_model"

                # Сохраняем модель с замороженными слоями
                for layer in model.layers:
                    layer.trainable = False

                model.save(str(saved_model_path))

                # Конвертируем из SavedModel
                converter = tf.lite.TFLiteConverter.from_saved_model(str(saved_model_path))
                converter.optimizations = []

                tflite_model = converter.convert()

                with open(tflite_path, 'wb') as f:
                    f.write(tflite_model)

            print(f"   ✅ TFLite сохранен (через SavedModel): {tflite_path}")
            print(f"   📦 Размер: {tflite_path.stat().st_size / 1024:.1f} KB")

        except Exception as e2:
            print(f"   ❌ Способ 2 не удался: {e2}")

            try:
                # Способ 3: Просто копируем существующую TFLite модель
                print("   🔄 Способ 3: Копирование существующей модели...")

                # Ищем исходную TFLite модель
                source_tflite = None

                # Пробуем найти в папке версии
                version_dir_parent = Path(model_path).parent.parent
                possible_paths = [
                    version_dir_parent / "forgetting_model.tflite",
                    Path(model_path).parent / "forgetting_model.tflite",
                    Path(model_path).parent.parent / "forgetting_model.tflite",
                    ]

                for path in possible_paths:
                    if path.exists():
                        source_tflite = path
                        break

                if source_tflite:
                    import shutil
                    shutil.copy2(source_tflite, tflite_path)
                    print(f"   ✅ TFLite скопирован из: {source_tflite}")
                    print(f"   📦 Размер: {tflite_path.stat().st_size / 1024:.1f} KB")
                else:
                    print(f"   ❌ Исходная TFLite модель не найдена")
                    tflite_path = None

            except Exception as e3:
                print(f"   ❌ Все способы конвертации не удались: {e3}")
                tflite_path = None

    # Метаданные
    metadata = {
        'version': new_version,
        'created_at': datetime.now().isoformat(),
        'type': 'fine_tuned',
        'user_id': user_id,
        'base_model': str(keras_path),
        'samples_used': len(logs),
        'final_loss': float(history.history['loss'][-1]),
        'final_mae': float(history.history['mae'][-1]),
        'size_mb': round(Path(keras_path_new).stat().st_size / (1024 * 1024), 2)
    }

    with open(version_dir / 'metadata.json', 'w') as f:
        json.dump(metadata, f, indent=2)

    # Обновляем latest_version.json в папке пользователя
    if user_id is not None:
        user_dir = Path(settings.MODELS_DIR) / f"user_{user_id}"
        user_dir.mkdir(parents=True, exist_ok=True)

        latest_info = {
            'version': new_version,
            'file_path': str(tflite_path) if tflite_path else str(keras_path_new),
            'size_mb': metadata['size_mb'],
            'accuracy': (1 - metadata['final_mae']) * 100,
            'mae': metadata['final_mae'],
            'samples': metadata['samples_used'],
            'created_at': metadata['created_at']
        }

        with open(user_dir / 'latest_version.json', 'w') as f:
            json.dump(latest_info, f, indent=2)

    print(f"\n💾 Модель сохранена как версия {new_version}")
    print(f"   • Keras: {keras_path_new}")
    if tflite_path:
        print(f"   • TFLite: {tflite_path}")

    return {
        'status': 'success',
        'version': new_version,
        'model_path': str(tflite_path) if tflite_path else str(keras_path_new),
        'metadata': metadata,
        'history': history.history
    }

# services/fine_tune_service.py - исправленная конвертация

def convert_to_tflite_compatible(model, tflite_path):
    """Конвертация с совместимостью со старыми версиями"""
    import tensorflow as tf

    converter = tf.lite.TFLiteConverter.from_keras_model(model)

    # Базовые оптимизации
    converter.optimizations = [tf.lite.Optimize.DEFAULT]

    # 🔥 ВАЖНО: Поддержка старых версий
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.TFLITE_BUILTINS_LEGACY,
    ]

    # Отключаем несовместимые фичи
    converter._experimental_disable_per_channel = True
    converter.experimental_new_converter = True

    # Конвертируем
    tflite_model = converter.convert()

    with open(tflite_path, 'wb') as f:
        f.write(tflite_model)

    return tflite_path