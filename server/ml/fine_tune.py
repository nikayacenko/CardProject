"""
Дообучение ML-модели на реальных данных
"""

import os
import json
import pandas as pd
import numpy as np
import tensorflow as tf
from sklearn.model_selection import train_test_split
from datetime import datetime
from pathlib import Path
from typing import Optional
from typing import Dict, Any, Optional
from core.database import Database
from core.config import settings


def fine_tune_model(
        model_path: Optional[str] = None,
        output_dir: Optional[str] = None,
        epochs: int = 10,
        learning_rate: float = 0.0001
) -> Dict[str, Any]:
    """
    Дообучение модели на реальных логах пользователей

    Args:
        model_path: путь к существующей модели (.keras)
        output_dir: директория для сохранения новой модели
        epochs: количество эпох дообучения (меньше, чем при обучении)
        learning_rate: скорость обучения (меньше, чем при обучении)

    Returns:
        Dict с результатами
    """

    print("=" * 60)
    print("🔄 ДООБУЧЕНИЕ ML-МОДЕЛИ НА РЕАЛЬНЫХ ДАННЫХ")
    print("=" * 60)

    # 1. Загружаем логи из БД
    print("\n📊 Загрузка логов из БД...")
    db = Database()
    logs = db.get_logs_for_training(limit=5000)

    if len(logs) < 50:
        print(f"   ⚠️ Недостаточно данных для дообучения (нужно минимум 50, получено {len(logs)})")
        return {'status': 'skipped', 'message': 'Недостаточно данных'}

    print(f"   ✅ Загружено {len(logs)} записей")

    # 2. Преобразуем в DataFrame
    df = pd.DataFrame(logs)
    df['question_type_encoded'] = df['question_type'].map({
        'FACT': 0.1,
        'DEFINITION': 0.4,
        'PROOF': 0.9
    })

    # 3. Подготавливаем признаки (как в train_model.py)
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

    # 4. Загружаем существующую модель
    if model_path is None:
        # Ищем последнюю модель
        models_dir = Path(settings.MODELS_DIR)
        versions = sorted([int(d.name) for d in models_dir.iterdir() if d.is_dir() and d.name.isdigit()])
        if not versions:
            print("   ❌ Нет существующей модели для дообучения")
            return {'status': 'error', 'message': 'Нет базовой модели'}

        latest_version = max(versions)
        model_path = models_dir / str(latest_version) / 'forgetting_model.keras'
        if not model_path.exists():
            print(f"   ❌ Модель не найдена: {model_path}")
            return {'status': 'error', 'message': 'Модель не найдена'}

    print(f"\n📥 Загрузка модели: {model_path}")
    model = tf.keras.models.load_model(str(model_path))

    # 5. Замораживаем первые слои (сохраняем базовые знания)
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

    # 7. Сохраняем новую модель
    if output_dir is None:
        output_dir = Path(settings.MODELS_DIR)
    else:
        output_dir = Path(output_dir)

    # Определяем новую версию
    existing_versions = [int(d.name) for d in output_dir.iterdir() if d.is_dir() and d.name.isdigit()]
    new_version = max(existing_versions) + 1 if existing_versions else 1

    version_dir = output_dir / str(new_version)
    version_dir.mkdir(parents=True, exist_ok=True)

    keras_path = version_dir / 'forgetting_model.keras'
    model.save(str(keras_path))

    # Конвертируем в TFLite
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    tflite_path = version_dir / 'forgetting_model.tflite'
    with open(tflite_path, 'wb') as f:
        f.write(tflite_model)

    # Метаданные
    metadata = {
        'version': new_version,
        'created_at': datetime.now().isoformat(),
        'type': 'fine_tuned',
        'base_model': str(model_path),
        'samples_used': len(logs),
        'final_loss': float(history.history['loss'][-1]),
        'final_mae': float(history.history['mae'][-1]),
        'size_mb': round(tflite_path.stat().st_size / (1024 * 1024), 2)
    }

    with open(version_dir / 'metadata.json', 'w') as f:
        json.dump(metadata, f, indent=2)

    # Сохраняем в БД
    db.save_model_version(
        version=new_version,
        file_path=str(tflite_path),
        metadata=metadata
    )

    print(f"\n💾 Модель сохранена как версия {new_version}")
    print(f"   • Keras: {keras_path}")
    print(f"   • TFLite: {tflite_path}")

    return {
        'status': 'success',
        'version': new_version,
        'model_path': str(tflite_path),
        'metadata': metadata,
        'history': history.history
    }


if __name__ == '__main__':
    fine_tune_model()