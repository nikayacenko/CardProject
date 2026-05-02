# train_model.py
import tensorflow as tf
import numpy as np
import pandas as pd
import os
import platform
from sklearn.model_selection import train_test_split
import json
import random
from datetime import datetime

# ==================================================
# КОНФИГУРАЦИЯ
# ==================================================

def get_android_assets_path():
    """Автоматическое определение пути к assets Android проекта"""

    # Текущая директория (где запущен скрипт)
    current_dir = os.path.dirname(os.path.abspath(__file__))

    # Поднимаемся на уровень выше (в корень проекта)
    project_dir = os.path.dirname(current_dir)

    # Путь к assets в Android проекте
    android_assets = os.path.join(project_dir, 'app', 'src', 'main', 'assets')

    return android_assets

# Путь для сохранения модели в Android проект
ANDROID_ASSETS_PATH = get_android_assets_path()

# ==================================================
# КЛАССЫ ДАННЫХ (соответствуют ReviewLog в Kotlin)
# ==================================================

class QuestionType:
    FACT = 'FACT'
    DEFINITION = 'DEFINITION'
    PROOF = 'PROOF'

    @classmethod
    def get_all(cls):
        return [cls.FACT, cls.DEFINITION, cls.PROOF]

    @classmethod
    def encode(cls, qtype):
        mapping = {
            cls.FACT: 0.1,
            cls.DEFINITION: 0.4,
            cls.PROOF: 0.9
        }
        return mapping.get(qtype, 0.5)

# ==================================================
# ГЕНЕРАЦИЯ СИНТЕТИЧЕСКИХ ДАННЫХ
# ==================================================

class SyntheticDataGenerator:
    """Генератор реалистичных данных для обучения модели"""

    def __init__(self, num_samples=2000):
        self.num_samples = num_samples
        np.random.seed(42)
        random.seed(42)

    def generate(self):
        """Генерация полного датасета"""

        print("🔄 Генерация синтетических данных...")
        # 1. Сначала генерируем типы
        question_types = self._generate_question_type()

        # 2. Генерируем длину текста, привязанную к типу
        text_lengths = self._generate_text_length_by_type(question_types)

        hours = self._generate_hour_of_day()
        days = self._generate_day_of_week()
        positions = self._generate_session_position()
        fatigue_levels = self._generate_fatigue_based_on_features(hours, days, positions)
        # Базовые параметры
        data = {
            'questionType': question_types,
            'cardTextLength': text_lengths,
            'hasFormulas': self._generate_has_formulas(),
            'hourOfDay': hours,
            'dayOfWeek': days,
            'fatigueLevel': fatigue_levels,
            'cardsReviewedInSession': positions,
            'totalReviewsBefore': self._generate_total_reviews(),
            'correctRateBefore': self._generate_correct_rate(),
            'streakCorrectBefore': self._generate_streak(),
            'daysSinceLastReview': self._generate_days_since_last(),
            'linkedCardsCount': self._generate_linked_count(),
            'linkedCardsMastery': self._generate_linked_mastery(),
        }
        df_temp = pd.DataFrame(data)
        data['responseTimeMs'] = self._generate_response_time(df_temp)
        df = pd.DataFrame(data)
        df['questionTypeEncoded'] = df['questionType'].apply(QuestionType.encode)
        # Генерируем целевую переменную (было ли правильно)
        df['wasCorrect'] = self._generate_was_correct(df)

        return df

    def _generate_fatigue_based_on_features(self, hours, days, positions):
        """Генерация усталости на основе других признаков"""
        fatigue_levels = []

        for hour, day, position in zip(hours, days, positions):
            # Базовый уровень
            base_fatigue = np.random.uniform(0.1, 0.4)

            # Корректировка по часу
            if hour >= 22 or hour <= 5:
                base_fatigue += 0.3
            elif hour >= 19:
                base_fatigue += 0.2
            elif hour >= 14:
                base_fatigue += 0.1

            # Корректировка по дню недели
            if day >= 6:
                base_fatigue -= 0.1

            # Корректировка по позиции
            if position > 40:
                base_fatigue += 0.3
            elif position > 25:
                base_fatigue += 0.2
            elif position > 10:
                base_fatigue += 0.1

            # Шум
            noise = np.random.uniform(-0.1, 0.1)
            fatigue = max(0.0, min(1.0, base_fatigue + noise))
            fatigue_levels.append(fatigue)

        return np.array(fatigue_levels)

    def _generate_text_length_by_type(self, types):
        """Длина текста в зависимости от типа"""
        lengths = []
        for qtype in types:
            if qtype == QuestionType.FACT:
                # Короткие и средние тексты
                if random.random() < 0.7:
                    length = random.randint(10, 60)
                else:
                    length = random.randint(60, 150)
            elif qtype == QuestionType.DEFINITION:
                # Средние и длинные
                if random.random() < 0.6:
                    length = random.randint(60, 200)
                else:
                    length = random.randint(200, 400)
            else:  # PROOF
                # Длинные
                length = random.randint(250, 800)
            lengths.append(length)
        return np.array(lengths)

    def _generate_response_time(self, df_temp):
        """Время ответа (8-12 секунд в среднем)"""
        times = []
        for i in range(self.num_samples):
            row = df_temp.iloc[i]
            text_length = row['cardTextLength'] if not np.isnan(row['cardTextLength']) else 100

            # Базовое время по типу вопроса
            qtype = row['questionType']
            if qtype == QuestionType.FACT:
                base_time = 5000
            elif qtype == QuestionType.DEFINITION:
                base_time = 8000
            else:  # PROOF
                base_time = 12000

            # Время на чтение (~8 мс/символ)
            reading_time = text_length * 8

            # Формулы увеличивают время
            if row['hasFormulas'] == 1:
                base_time *= 1.3

            # Усталость замедляет
            fatigue_multiplier = 1.0 + (row['fatigueLevel'] * 0.4)

            # Шум
            noise = np.random.normal(1.0, 0.25)

            final_time = (base_time + reading_time) * fatigue_multiplier * noise
            times.append(min(30000, max(2000, final_time)))

        return np.array(times)


    def _generate_question_type(self):
        """Обновленное распределение типов (без FORMULA)"""
        types = QuestionType.get_all()
        probabilities = [0.45, 0.35, 0.20] # FACT, DEFINITION, PROOF, TRANSLATION
        return np.random.choice(types, self.num_samples, p=probabilities)

    def _generate_has_formulas(self):
        """Наличие формул (30% карточек с формулами)"""
        return np.random.choice([0, 1], self.num_samples, p=[0.7, 0.3])

    def _generate_hour_of_day(self):
        """Час дня (пик активности 10-22)"""
        hours = []
        for _ in range(self.num_samples):
            # Больше активности днем
            if random.random() < 0.7:
                hour = random.randint(10, 22)
            else:
                hour = random.randint(0, 23)
            hours.append(hour)
        return np.array(hours)

    def _generate_day_of_week(self):
        """День недели (1-7, больше активности в будни)"""
        days = []
        for _ in range(self.num_samples):
            if random.random() < 0.6:
                day = random.randint(1, 5)  # Будни
            else:
                day = random.randint(6, 7)  # Выходные
            days.append(day)
        return np.array(days)

    def _generate_session_position(self):
        """Позиция в сессии (0-50 карточек)"""
        return np.random.randint(0, 50, self.num_samples)

    def _generate_total_reviews(self):
        """Общее количество повторений (0-20)"""
        return np.random.randint(0, 20, self.num_samples)

    def _generate_correct_rate(self):
        """Процент успеха (0-1)"""
        return np.random.uniform(0.3, 0.95, self.num_samples)

    def _generate_streak(self):
        """Текущая серия (0-10)"""
        return np.random.randint(0, 10, self.num_samples)

    def _generate_days_since_last(self):
        """Дней с прошлого повторения (0-30)"""
        return np.random.randint(0, 30, self.num_samples)

    def _generate_linked_count(self):
        """Количество связанных карточек (0-10)"""
        return np.random.randint(0, 10, self.num_samples)

    def _generate_linked_mastery(self):
        """Выученность связанных карточек (0-1)"""
        return np.random.uniform(0, 1, self.num_samples)

    def _generate_was_correct(self, df):
        """Правильные ответы (цель 65-75% успеха)"""
        was_correct = []

        for i in range(self.num_samples):
            row = df.iloc[i]

            # Базовый уровень 70% (оптимальный для интервального повторения)
            prob = 0.70

            # Штрафы
            if row['daysSinceLastReview'] > 14:
                prob -= 0.12
            if row['fatigueLevel'] > 0.7:
                prob -= 0.10
            if row['hasFormulas'] == 1:
                prob -= 0.10
            if row['cardTextLength'] > 400:
                prob -= 0.08
            if row['questionType'] == QuestionType.PROOF:
                prob -= 0.08
            elif row['questionType'] == QuestionType.DEFINITION:
                prob -= 0.03

            # Бонусы
            if row['correctRateBefore'] > 0.8:
                prob += 0.08
            if row['streakCorrectBefore'] > 3:
                prob += 0.05
            if row['linkedCardsMastery'] > 0.7:
                prob += 0.05

            # Ограничиваем диапазон (40-85%)
            prob = max(0.40, min(0.85, prob))

            # Генерируем результат
            result = 1 if random.random() < prob else 0
            was_correct.append(result)

        return np.array(was_correct)

# ==================================================
# ПОДГОТОВКА ДАННЫХ ДЛЯ МОДЕЛИ
# ==================================================

class DataPreprocessor:
    """Подготовка признаков для модели"""

    @staticmethod
    def prepare_features(data):
        """Преобразование DataFrame в массив признаков (20 признаков)"""

        features = np.array([
            # 1. Нормализованная длина текста
            data['cardTextLength'] / 1000,

            # 2. Наличие формул (0/1)
            data['hasFormulas'],

            # 3. Тип вопроса (закодированный)
            data['questionType'].apply(QuestionType.encode),

            # 4. Время ответа (нормализовано)
            data['responseTimeMs'] / 30000,

            # 5. Час дня
            data['hourOfDay'] / 24,

            # 6. День недели
            data['dayOfWeek'] / 7,

            # 7. Уровень усталости
            data['fatigueLevel'],

            # 8. Позиция в сессии
            data['cardsReviewedInSession'] / 50,

            # 9. Всего повторений
            data['totalReviewsBefore'] / 20,

            # 10. Процент успеха
            data['correctRateBefore'],

            # 11. Серия успехов
            data['streakCorrectBefore'] / 10,

            # 12. Дней с прошлого раза
            data['daysSinceLastReview'] / 30,

            # 13. Количество связей
            data['linkedCardsCount'] / 10,

            # 14. Выученность связей
            data['linkedCardsMastery'],

            # 15. Взаимодействие усталость × время
            data['fatigueLevel'] * (data['responseTimeMs'] / 30000),

            # 16. Взаимодействие успех × интервал
            data['correctRateBefore'] * (data['daysSinceLastReview'] / 30),

            # 17-20. Заполнители (резерв)
            np.zeros(len(data)),
            np.zeros(len(data)),
            np.zeros(len(data)),
            np.zeros(len(data))
        ]).T

        return features.astype(np.float32)

    @staticmethod
    def prepare_targets(data):
        """Подготовка целевых переменных"""

        # Цель 1: Вероятность забывания (1 - wasCorrect)
        forgetting = 1 - data['wasCorrect'].values

        # Цель 2: Оптимальный интервал (нормализованный)
        interval = []
        for _, row in data.iterrows():
            if row['wasCorrect'] == 1:
                # Правильный ответ - увеличиваем интервал
                base = min(30, 1 + row['totalReviewsBefore'] * 0.5)
                # Корректировка на сложность
                if row['fatigueLevel'] > 0.7:
                    base *= 0.8
                if row['hasFormulas'] == 1:
                    base *= 0.9
            else:
                # Неправильный ответ - короткий интервал
                base = 1.0

            # Нормализация до 0-1
            interval.append(base / 30)

        interval = np.array(interval)

        # Цель 3: Уверенность модели (базовое значение)
        confidence = np.ones(len(data)) * 0.8

        return np.column_stack([forgetting, interval, confidence])

# ==================================================
# СОЗДАНИЕ МОДЕЛИ
# ==================================================

def create_model():
    """Создание нейросетевой модели"""

    model = tf.keras.Sequential([
        # Входной слой (20 признаков)
        tf.keras.layers.Dense(64, activation='relu', input_shape=(20,),
                              kernel_regularizer=tf.keras.regularizers.l2(0.001)),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.3),

        # Скрытый слой 1
        tf.keras.layers.Dense(32, activation='relu',
                              kernel_regularizer=tf.keras.regularizers.l2(0.001)),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.3),

        # Скрытый слой 2
        tf.keras.layers.Dense(16, activation='relu'),

        # Выходной слой (3 значения)
        tf.keras.layers.Dense(3, activation='sigmoid')
    ])

    # Компиляция модели
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss='mse',
        metrics=['mae', 'mse']
    )

    return model

# ==================================================
# ОБУЧЕНИЕ МОДЕЛИ
# ==================================================

class ModelTrainer:
    """Класс для обучения модели"""

    def __init__(self):
        self.model = None
        self.history = None

    def train(self, X_train, y_train, X_val, y_val, epochs=50):
        """Обучение модели"""

        # Создание модели
        self.model = create_model()

        # Callbacks для улучшения обучения
        callbacks = [
            tf.keras.callbacks.EarlyStopping(
                monitor='val_loss',
                patience=10,
                restore_best_weights=True
            ),
            tf.keras.callbacks.ReduceLROnPlateau(
                monitor='val_loss',
                factor=0.5,
                patience=5,
                min_lr=0.00001
            )
        ]

        # Обучение
        self.history = self.model.fit(
            X_train, y_train,
            validation_data=(X_val, y_val),
            epochs=epochs,
            batch_size=32,
            callbacks=callbacks,
            verbose=1
        )

        return self.model, self.history

    def evaluate(self, X_test, y_test):
        """Оценка модели"""
        loss, mae, mse = self.model.evaluate(X_test, y_test, verbose=0)
        return {'loss': loss, 'mae': mae, 'mse': mse}

    def save_model(self, path):
        """Сохранение модели в формате Keras"""
        self.model.save(path)
        print(f"✅ Модель сохранена: {path}")

    def convert_to_tflite(self, model_path, tflite_path):
        """Конвертация в TensorFlow Lite"""

        # Загрузка модели
        model = tf.keras.models.load_model(model_path)

        # Конвертация
        converter = tf.lite.TFLiteConverter.from_keras_model(model)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]

        tflite_model = converter.convert()

        # Сохранение
        with open(tflite_path, 'wb') as f:
            f.write(tflite_model)

        print(f"✅ TFLite модель сохранена: {tflite_path}")
        return tflite_model

# ==================================================
# ОСНОВНАЯ ФУНКЦИЯ
# ==================================================

def main():
    """Главная функция обучения"""

    print("="*60)
    print("🚀 ОБУЧЕНИЕ ML-МОДЕЛИ ДЛЯ ИНТЕРВАЛЬНОГО ПОВТОРЕНИЯ")
    print("="*60)
    print()

    # 1. Создание папок
    print("📁 Создание необходимых папок...")
    os.makedirs("data", exist_ok=True)
    os.makedirs("models", exist_ok=True)
    os.makedirs(ANDROID_ASSETS_PATH, exist_ok=True)

    print(f"   📂 Папка данных: {os.path.abspath('data')}")
    print(f"   📂 Папка моделей: {os.path.abspath('models')}")
    print(f"   📂 Android assets: {ANDROID_ASSETS_PATH}")
    print()

    # 2. Загрузка или генерация данных
    data_path = 'trash_data/review_logs.csv'

    if os.path.exists(data_path):
        print(f"📂 Загрузка реальных данных из: {data_path}")
        df = pd.read_csv(data_path)
        print(f"   ✅ Загружено {len(df)} записей")
    else:
        print("⚠️  Реальные данные не найдены!")
        response = input("   Сгенерировать синтетические данные? (y/n): ")

        if response.lower() == 'y':
            generator = SyntheticDataGenerator(2000)
            df = generator.generate()

            # Сохранение
            df.to_csv('data/synthetic_data4.csv', index=False, sep=',', decimal='.')
            print(f"   ✅ Сгенерировано {len(df)} записей")
            print(f"   💾 Сохранено: {os.path.abspath('data/synthetic_data.csv')}")
        else:
            print("❌ Обучение невозможно без данных!")
            return

    print()
    print(f"📊 Статистика данных:")
    print(f"   - Всего записей: {len(df)}")
    print(f"   - Правильных ответов: {df['wasCorrect'].sum()} ({df['wasCorrect'].mean()*100:.1f}%)")
    print(f"   - С формулами: {df['hasFormulas'].sum()} ({df['hasFormulas'].mean()*100:.1f}%)")
    print()

    # 3. Подготовка данных для модели
    print("🔄 Подготовка признаков...")
    preprocessor = DataPreprocessor()
    X = preprocessor.prepare_features(df)
    y = preprocessor.prepare_targets(df)

    print(f"   📊 Форма признаков: {X.shape}")
    print(f"   📊 Форма целевых переменных: {y.shape}")
    print()

    # 4. Разделение на train/val/test
    print("🔄 Разделение данных...")
    X_temp, X_test, y_temp, y_test = train_test_split(
        X, y, test_size=0.15, random_state=42
    )
    X_train, X_val, y_train, y_val = train_test_split(
        X_temp, y_temp, test_size=0.15, random_state=42
    )

    print(f"   🎯 Обучающая выборка: {len(X_train)}")
    print(f"   🎯 Валидационная выборка: {len(X_val)}")
    print(f"   🎯 Тестовая выборка: {len(X_test)}")
    print()

    # 5. Создание и обучение модели
    print("🧠 Создание модели...")
    trainer = ModelTrainer()

    print("🚀 Начало обучения...")
    print("-"*60)

    model, history = trainer.train(
        X_train, y_train,
        X_val, y_val,
        epochs=50
    )

    print("-"*60)
    print("✅ Обучение завершено!")
    print()

    # 6. Оценка модели
    print("📊 Оценка модели...")
    metrics = trainer.evaluate(X_test, y_test)
    print(f"   📉 Loss: {metrics['loss']:.4f}")
    print(f"   📏 MAE: {metrics['mae']:.4f}")
    print(f"   📐 MSE: {metrics['mse']:.4f}")
    print(f"   🎯 Точность: {(1 - metrics['mae']) * 100:.1f}%")
    print()

    # 7. Сохранение модели
    print("💾 Сохранение модели...")

    # Сохраняем в Keras формате
    keras_path = 'models/forgetting_model.keras'
    trainer.save_model(keras_path)

    # Конвертируем в TFLite
    tflite_path = 'models/forgetting_model.tflite'
    trainer.convert_to_tflite(keras_path, tflite_path)

    # Копируем в Android assets
    android_tflite_path = os.path.join(ANDROID_ASSETS_PATH, 'forgetting_model.tflite')
    with open(android_tflite_path, 'wb') as f:
        with open(tflite_path, 'rb') as src:
            f.write(src.read())
    print(f"   ✅ Скопировано в Android: {android_tflite_path}")
    print()

    # 8. Сохранение истории обучения
    print("📈 Сохранение истории обучения...")
    history_path = 'models/training_history.json'
    with open(history_path, 'w') as f:
        json.dump({
            'loss': history.history['loss'],
            'val_loss': history.history['val_loss'],
            'mae': history.history['mae'],
            'val_mae': history.history['val_mae']
        }, f, indent=2)

    print(f"   ✅ История сохранена: {history_path}")
    print()

    # 9. Создание файла с информацией о модели
    print("📝 Создание метаданных модели...")
    metadata = {
        'model_name': 'Forgetting Probability Model',
        'version': '1.0',
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

    metadata_path = 'models/model_metadata.json'
    with open(metadata_path, 'w') as f:
        json.dump(metadata, f, indent=2)

    print(f"   ✅ Метаданные сохранены: {metadata_path}")
    print()

    # 10. Финальный отчет
    print("="*60)
    print("🎉 ОБУЧЕНИЕ ЗАВЕРШЕНО УСПЕШНО!")
    print("="*60)
    print(f"""
    📊 ИТОГИ:
    ---------
    • Датасет: {len(df)} записей
    • Точность модели: {(1 - metrics['mae']) * 100:.1f}%
    • MAE: {metrics['mae']:.4f}
    
    📁 ФАЙЛЫ:
    ---------
    • Модель Keras: {os.path.abspath('models/forgetting_model.keras')}
    • Модель TFLite: {os.path.abspath('models/forgetting_model.tflite')}
    • Android модель: {android_tflite_path}
    • История: {os.path.abspath('models/training_history.json')}
    • Метаданные: {os.path.abspath('models/model_metadata.json')}
    
    🚀 ДАЛЬНЕЙШИЕ ДЕЙСТВИЯ:
    ------------------------
    1. Пересоберите Android проект
    2. Модель автоматически загрузится из assets
    3. Проверьте логи: должно быть "✅ Модель TensorFlow Lite загружена"
    """)

# ==================================================
# ЗАПУСК
# ==================================================

if __name__ == '__main__':
    main()