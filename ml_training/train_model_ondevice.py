# ml_training/train_model_ondevice.py
import tensorflow as tf
import numpy as np
import os
import shutil
from train_model import SyntheticDataGenerator, DataPreprocessor

def create_training_model():
    """Создание модели с поддержкой on-device training"""

    print("=" * 60)
    print("🚀 СОЗДАНИЕ МОДЕЛИ С ПОДДЕРЖКОЙ ON-DEVICE TRAINING")
    print("=" * 60)

    # 1. Генерируем синтетические данные
    print("\n📊 Генерация синтетических данных...")
    generator = SyntheticDataGenerator(3000)
    df = generator.generate()
    preprocessor = DataPreprocessor()
    X = preprocessor.prepare_features(df)
    y = preprocessor.prepare_targets(df)
    print(f"   ✅ Сгенерировано {len(X)} образцов")

    # 2. Создаём модель
    print("\n🧠 Создание модели...")

    # Используем простую модель
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

    # 3. Обучаем на синтетике
    print("\n📚 Начальное обучение...")
    history = model.fit(
        X, y,
        epochs=30,
        batch_size=32,
        validation_split=0.2,
        verbose=1
    )
    print(f"   ✅ Обучение завершено. Loss: {history.history['loss'][-1]:.4f}")

    # 4. Сохраняем обычную модель для инференса
    print("\n💾 Сохранение модели для инференса...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model_infer = converter.convert()

    os.makedirs("models", exist_ok=True)
    infer_path = "models/model_for_inference.tflite"
    with open(infer_path, 'wb') as f:
        f.write(tflite_model_infer)
    print(f"   ✅ Модель для инференса: {infer_path}")

    # 5. 🔥 СОЗДАЁМ МОДЕЛЬ ДЛЯ ОБУЧЕНИЯ
    print("\n🔧 Создание модели для on-device обучения...")

    # Создаём модель для обучения (без Dropout)
    train_model = tf.keras.Sequential([
        tf.keras.layers.Dense(64, activation='relu', input_shape=(20,),
                              kernel_regularizer=tf.keras.regularizers.l2(0.001)),
        tf.keras.layers.Dense(32, activation='relu',
                              kernel_regularizer=tf.keras.regularizers.l2(0.001)),
        tf.keras.layers.Dense(16, activation='relu'),
        tf.keras.layers.Dense(3, activation='sigmoid')
    ])

    # Копируем веса
    weights = model.get_weights()
    new_weights = [
        weights[0],   # Dense 64 kernel
        weights[1],   # Dense 64 bias
        weights[2],   # Dense 32 kernel
        weights[3],   # Dense 32 bias
        weights[4],   # Dense 16 kernel
        weights[5],   # Dense 16 bias
        weights[6],   # Dense 3 kernel
        weights[7]    # Dense 3 bias
    ]
    train_model.set_weights(new_weights)
    print("   ✅ Веса скопированы")

    # 6. 🔥 СОЗДАЁМ ОБЪЕКТ ДЛЯ TFLite С ПОДДЕРЖКОЙ ОБУЧЕНИЯ
    print("\n🔄 Создание TFLite с поддержкой обучения...")

    # Создаём класс с tf.Variable для обучения
    class TrainableModel(tf.Module):
        def __init__(self, keras_model):
            super().__init__()

            # Получаем веса из Keras модели
            weights = keras_model.get_weights()

            # Создаём переменные для TFLite
            self.w1 = tf.Variable(weights[0], name='dense/kernel', dtype=tf.float32)
            self.b1 = tf.Variable(weights[1], name='dense/bias', dtype=tf.float32)
            self.w2 = tf.Variable(weights[2], name='dense_1/kernel', dtype=tf.float32)
            self.b2 = tf.Variable(weights[3], name='dense_1/bias', dtype=tf.float32)
            self.w3 = tf.Variable(weights[4], name='dense_2/kernel', dtype=tf.float32)
            self.b3 = tf.Variable(weights[5], name='dense_2/bias', dtype=tf.float32)
            self.w4 = tf.Variable(weights[6], name='dense_3/kernel', dtype=tf.float32)
            self.b4 = tf.Variable(weights[7], name='dense_3/bias', dtype=tf.float32)

            # Оптимизатор
            self.optimizer = tf.keras.optimizers.Adam(learning_rate=0.0005)

        @tf.function(input_signature=[])
        def initialize(self):
            """Инициализация"""
            # Триггерим создание переменных
            dummy = tf.zeros([1, 20], dtype=tf.float32)
            _ = self._forward(dummy)
            return {"status": tf.constant(True)}

        def _forward(self, x):
            x = tf.nn.relu(tf.matmul(x, self.w1) + self.b1)
            x = tf.nn.relu(tf.matmul(x, self.w2) + self.b2)
            x = tf.nn.relu(tf.matmul(x, self.w3) + self.b3)
            x = tf.sigmoid(tf.matmul(x, self.w4) + self.b4)
            return x

        @tf.function(input_signature=[
            tf.TensorSpec(shape=[1, 20], dtype=tf.float32, name="x")
        ])
        def predict(self, x):
            return {"output": self._forward(x)}

        @tf.function(input_signature=[
            tf.TensorSpec(shape=[1, 20], dtype=tf.float32, name="x"),
            tf.TensorSpec(shape=[1, 3], dtype=tf.float32, name="y")
        ])
        def train_step(self, x, y):
            with tf.GradientTape() as tape:
                predictions = self._forward(x)
                loss = tf.reduce_mean(tf.square(y - predictions))
                # L2 регуляризация
                l2_loss = 0.001 * (tf.nn.l2_loss(self.w1) + tf.nn.l2_loss(self.w2) +
                                   tf.nn.l2_loss(self.w3) + tf.nn.l2_loss(self.w4))
                total_loss = loss + l2_loss

            # Вычисляем градиенты
            trainable_vars = [self.w1, self.b1, self.w2, self.b2,
                              self.w3, self.b3, self.w4, self.b4]
            gradients = tape.gradient(total_loss, trainable_vars)

            # Обрезаем градиенты
            gradients, _ = tf.clip_by_global_norm(gradients, 1.0)

            # Применяем градиенты
            self.optimizer.apply_gradients(zip(gradients, trainable_vars))

            return {"loss": total_loss}

    # Создаём trainable модель
    trainable = TrainableModel(train_model)

    # 7. Сохраняем как SavedModel
    print("\n💾 Сохранение SavedModel...")
    export_dir = "saved_model_training"
    if os.path.exists(export_dir):
        shutil.rmtree(export_dir)

    # 🔥 ВАЖНО: сначала инициализируем
    trainable.initialize()

    tf.saved_model.save(
        trainable,
        export_dir,
        signatures={
            'init': trainable.initialize,
            'predict': trainable.predict,
            'train_step': trainable.train_step,
        }
    )
    print(f"   ✅ SavedModel сохранён: {export_dir}")

    # 8. Конвертируем в TFLite
    print("\n🔄 Конвертация в TFLite...")

    converter = tf.lite.TFLiteConverter.from_saved_model(export_dir)
    converter.experimental_enable_resource_variables = True

    # Используем SELECT_TF_OPS для поддержки переменных
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS,
    ]

    tflite_model_train = converter.convert()

    # Сохраняем
    train_path = "models/model_with_training.tflite"
    with open(train_path, 'wb') as f:
        f.write(tflite_model_train)

    print(f"   ✅ TFLite для обучения: {train_path}")
    print(f"   📊 Размер: {len(tflite_model_train) / 1024 / 1024:.2f} МБ")

    # 9. Проверяем
    print("\n🔍 Проверка TFLite модели...")
    try:
        interpreter = tf.lite.Interpreter(model_path=train_path)
        interpreter.allocate_tensors()

        signature_defs = interpreter.get_signature_list()
        print(f"   ✅ Сигнатуры: {list(signature_defs.keys())}")

        # Проверяем каждую сигнатуру
        for sig_name in signature_defs:
            print(f"   - {sig_name}: {signature_defs[sig_name]}")

    except Exception as e:
        print(f"   ⚠️ Ошибка проверки: {e}")

    # 10. Копируем в Android assets
    print("\n📁 Копирование в Android assets...")
    android_assets = os.path.join(os.path.dirname(os.path.dirname(__file__)),
                                  "app", "src", "main", "assets")
    os.makedirs(android_assets, exist_ok=True)

    shutil.copy2(infer_path, os.path.join(android_assets, "forgetting_model.tflite"))
    shutil.copy2(train_path, os.path.join(android_assets, "model_with_training.tflite"))
    print(f"   ✅ Скопировано в {android_assets}")

    print("\n" + "=" * 60)
    print("✅ МОДЕЛИ СОЗДАНЫ!")
    print("=" * 60)

if __name__ == '__main__':
    create_training_model()