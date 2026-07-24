"""
Обучение ML-модели на синтетических данных
"""

import os
import sys
import json
import pandas as pd
import numpy as np
import tensorflow as tf
from sklearn.model_selection import train_test_split
from datetime import datetime
from pathlib import Path
from typing import Dict, Any

# Добавляем путь к ml_training для импорта
sys.path.insert(0, str(Path(__file__).parent.parent.parent / 'ml_training'))

# Импортируем из существующего train_model.py
from train_model import SyntheticDataGenerator, DataPreprocessor, ModelTrainer


def train_synthetic_model(
        num_samples: int = 2000,
        output_dir: str = None,
        save_to_android: bool = False
) -> Dict[str, Any]:
    """
    Обучение модели на синтетических данных

    Args:
        num_samples: количество генерируемых записей
        output_dir: директория для сохранения модели
        save_to_android: сохранять ли в папку assets Android-проекта

    Returns:
        Dict с метриками и путями к файлам
    """

    print("=" * 60)
    print("🚀 ОБУЧЕНИЕ ML-МОДЕЛИ НА СИНТЕТИЧЕСКИХ ДАННЫХ")
    print("=" * 60)

    # 1. Генерация данных
    print(f"\n📊 Генерация {num_samples} записей...")
    generator = SyntheticDataGenerator(num_samples)
    df = generator.generate()

    print(f"   ✅ Сгенерировано {len(df)} записей")
    print(f"   ✅ Правильных ответов: {df['wasCorrect'].sum()} ({df['wasCorrect'].mean()*100:.1f}%)")

    # 2. Подготовка признаков
    print("\n🔄 Подготовка признаков...")
    preprocessor = DataPreprocessor()
    X = preprocessor.prepare_features(df)
    y = preprocessor.prepare_targets(df)

    print(f"   📊 Форма признаков: {X.shape}")
    print(f"   📊 Форма целевых: {y.shape}")

    # 3. Разделение на train/val/test
    X_temp, X_test, y_temp, y_test = train_test_split(
        X, y, test_size=0.15, random_state=42
    )
    X_train, X_val, y_train, y_val = train_test_split(
        X_temp, y_temp, test_size=0.15, random_state=42
    )

    print(f"\n🎯 Обучающая выборка: {len(X_train)}")
    print(f"🎯 Валидационная: {len(X_val)}")
    print(f"🎯 Тестовая: {len(X_test)}")

    # 4. Обучение
    print("\n🧠 Обучение модели...")
    trainer = ModelTrainer()
    model, history = trainer.train(
        X_train, y_train,
        X_val, y_val,
        epochs=50
    )

    # 5. Оценка
    print("\n📊 Оценка модели...")
    metrics = trainer.evaluate(X_test, y_test)

    print(f"   📉 Loss: {metrics['loss']:.4f}")
    print(f"   📏 MAE: {metrics['mae']:.4f}")
    print(f"   📐 MSE: {metrics['mse']:.4f}")
    print(f"   🎯 Точность: {(1 - metrics['mae']) * 100:.1f}%")

    # 6. Сохранение
    print("\n💾 Сохранение модели...")

    # Определяем директорию вывода
    if output_dir is None:
        output_dir = Path(__file__).parent.parent / 'static' / 'models'
    else:
        output_dir = Path(output_dir)

    output_dir.mkdir(parents=True, exist_ok=True)

    # Сохраняем Keras модель
    keras_path = output_dir / 'forgetting_model.keras'
    trainer.save_model(str(keras_path))

    # Конвертируем в TFLite
    tflite_path = output_dir / 'forgetting_model.tflite'

    # Загружаем модель
    model = tf.keras.models.load_model(str(keras_path))

    # Конвертируем
    converter = tf.lite.TFLiteConverter.from_keras_model(model)

    # 🔥 ТОЛЬКО ОПТИМИЗАЦИЯ
    converter.optimizations = [tf.lite.Optimize.DEFAULT]

    # 🔥 ТОЛЬКО СТАНДАРТНЫЕ ОПЕРАЦИИ (БЕЗ SELECT_TF_OPS)
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
    ]

    # Конвертируем
    tflite_model = converter.convert()

    # Сохраняем
    with open(tflite_path, 'wb') as f:
        f.write(tflite_model)

    print(f"   ✅ TFLite сохранён: {tflite_path}")
    print(f"   📦 Размер: {tflite_path.stat().st_size / 1024:.1f} KB")

    # Метаданные
    metadata = {
        'model_name': 'Forgetting Probability Model',
        'version': 1,
        'created_at': datetime.now().isoformat(),
        'input_features': 20,
        'output_features': 3,
        'training_samples': len(X_train),
        'validation_samples': len(X_val),
        'test_samples': len(X_test),
        'test_mae': float(metrics['mae']),
        'test_accuracy': float((1 - metrics['mae']) * 100),
        'question_types': {
            'FACT': 0.1,
            'DEFINITION': 0.3,
            'PROOF': 0.9
        }
    }

    metadata_path = output_dir / 'model_metadata.json'
    with open(metadata_path, 'w') as f:
        json.dump(metadata, f, indent=2)

    print(f"\n✅ Модель сохранена:")
    print(f"   • Keras: {keras_path}")
    print(f"   • TFLite: {tflite_path}")
    print(f"   • Metadata: {metadata_path}")

    return {
        'model_path': str(tflite_path),
        'keras_path': str(keras_path),
        'metadata': metadata,
        'metrics': metrics
    }


if __name__ == '__main__':
    train_synthetic_model()