# generate_cards_adb.py
import sqlite3
import time
import subprocess
import os

def find_adb():
    """Поиск adb в стандартных местах"""
    possible_paths = [
        r"C:\Users\yacen\AppData\Local\Android\Sdk\platform-tools\adb.exe",
        r"C:\Android\Sdk\platform-tools\adb.exe",
        r"C:\Program Files\Android\Android Studio\platform-tools\adb.exe",
        r"C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe",
        # Путь из вашей переменной окружения ANDROID_HOME
        os.path.join(os.environ.get('ANDROID_HOME', ''), 'platform-tools', 'adb.exe'),
        os.path.join(os.environ.get('ANDROID_SDK_ROOT', ''), 'platform-tools', 'adb.exe'),
    ]

    for path in possible_paths:
        if os.path.exists(path):
            print(f"✅ ADB найден: {path}")
            return path

    # Если не нашли, пробуем просто 'adb' (должно быть в PATH)
    return 'adb'

def run_adb_command(args):
    """Запуск ADB команды с полным путем"""
    adb_path = find_adb()
    cmd = [adb_path] + args
    print(f"🔄 Выполняю: {' '.join(cmd)}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"❌ Ошибка: {result.stderr}")
    else:
        print(f"✅ Успешно: {result.stdout}")
    return result

def generate_cards_direct():
    print("="*60)
    print("🚀 ГЕНЕРАЦИЯ КАРТОЧЕК ЧЕРЕЗ ADB")
    print("="*60)

    # Проверяем наличие ADB
    adb_path = find_adb()
    print(f"📌 Использую ADB: {adb_path}")

    # Проверяем подключение устройства
    result = run_adb_command(['devices'])
    if 'device' not in result.stdout or 'List of devices attached' in result.stdout and 'device' not in result.stdout.split('\n')[1]:
        print("❌ Устройство не подключено!")
        print("   Подключите телефон или запустите эмулятор")
        print("   Проверьте: adb devices")
        return

    # Временный файл для работы
    local_db = 'temp_card_database.db'
    device_path = '/data/data/com.example.cardproject/databases/card_database'

    try:
        # 1. Скачиваем БД
        print("\n📥 Скачиваю базу данных с устройства...")
        run_adb_command(['pull', device_path, local_db])

        if not os.path.exists(local_db):
            print("❌ Не удалось скачать БД. Проверьте права доступа.")
            print("   Возможно нужно выполнить: adb shell run-as com.example.cardproject cat /data/data/com.example.cardproject/databases/card_database > card_database.db")
            return

        print(f"✅ БД скачана: {os.path.abspath(local_db)} ({os.path.getsize(local_db)} байт)")

        # 2. Работаем с локальной копией
        print("\n🔄 Добавляю данные в БД...")
        conn = sqlite3.connect(local_db)
        cursor = conn.cursor()

        now = int(time.time() * 1000)

        # Проверяем существующие колоды
        cursor.execute("SELECT id, name FROM decks ORDER BY id DESC")
        existing = cursor.fetchall()
        print(f"📚 Существующие колоды: {existing}")

        # Создаем колоду
        cursor.execute('''
            INSERT INTO decks (
                name, description, coverColor, learningMode, cardCount, createdAt
            ) VALUES (?, ?, ?, ?, ?, ?)
        ''', ('Английский язык - ADB', 'Слова для начинающих',
              '#4285F4', 'LONG_TERM', 0, now))

        deck_id = cursor.lastrowid
        print(f"✅ Создана колода ID: {deck_id}")

        # Слова
        words = [
            ("Hello", "Привет"), ("Goodbye", "До свидания"),
            ("Thank you", "Спасибо"), ("Please", "Пожалуйста"),
            ("Yes", "Да"), ("No", "Нет"),
            ("Cat", "Кошка"), ("Dog", "Собака"),
            ("House", "Дом"), ("Car", "Машина"),
            ("Food", "Еда"), ("Go", "Идти"),
            ("Come", "Приходить"), ("See", "Видеть"),
            ("Want", "Хотеть"), ("Have", "Иметь"),
            ("Good", "Хороший"), ("Bad", "Плохой"),
            ("Big", "Большой"), ("Small", "Маленький")
        ]

        for front, back in words:
            word_count = len(front.split()) + len(back.split())
            cursor.execute('''
                INSERT INTO cards (
                    deckId, front, back, createdAt, difficultyScore, isFormula,
                    wordCount, questionType, averageResponseTimeMs, totalReviews,
                    lastResponseTimeMs, successRate, linkedCardIds, lastFiveResults,
                    algorithmType, masteryLevel, lastPredictedProbability,
                    easeFactor, interval, reviewStage, consecutiveCorrect
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', (deck_id, front, back, now, 0.3, 0, word_count,
                  'FACT', 0, 0, 0, 0.0, '', '', 'ML', 0.0, 0.0, 2.5, 1, 0, 0))

        # Обновляем счетчик
        cursor.execute('UPDATE decks SET cardCount = ? WHERE id = ?', (len(words), deck_id))

        # Теги
        tags = [('английский', deck_id), ('слова', deck_id)]
        cursor.executemany('INSERT INTO tag (name, deckId) VALUES (?, ?)', tags)

        conn.commit()

        # Проверяем вставку
        cursor.execute("SELECT COUNT(*) FROM cards WHERE deckId = ?", (deck_id,))
        card_count = cursor.fetchone()[0]
        print(f"✅ Добавлено {card_count} карточек")

        conn.close()

        # 3. Загружаем обратно на устройство
        print("\n📤 Загружаю обновленную БД на устройство...")
        run_adb_command(['push', local_db, device_path])

        # 4. Устанавливаем правильные права
        print("\n🔧 Устанавливаю права доступа...")
        run_adb_command(['shell', 'chmod', '666', device_path])

        print("\n✅ ГОТОВО! База данных обновлена")
        print("👉 Перезапустите приложение на телефоне")

    except Exception as e:
        print(f"❌ Ошибка: {e}")
        import traceback
        traceback.print_exc()

    finally:
        # Удаляем временный файл
        if os.path.exists(local_db):
            os.remove(local_db)
            print(f"🧹 Временный файл удален")

if __name__ == '__main__':
    generate_cards_direct()