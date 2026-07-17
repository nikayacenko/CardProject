"""
API эндпоинты
"""
from fastapi import APIRouter, HTTPException, Header
from fastapi.responses import FileResponse
from typing import List
import os
from services.user_service import UserService
from .models import (
    LogEntry, UploadLogsResponse, ModelInfo,
    TrainingStatus, TrainResponse,
    UserRegister, UserLogin, AuthResponse, UserResponse  # ← добавить AuthResponse
)
from services.log_service import LogService
from services.model_service import ModelService
from core.config import settings
from datetime import datetime
router = APIRouter()
log_service = LogService()
model_service = ModelService()
user_service = UserService()



@router.post("/logs/upload", response_model=UploadLogsResponse)
async def upload_logs(
        logs: List[LogEntry],
        authorization: str = Header(...)
):
    """
    Загрузить логи с Android-приложения
    """
    try:
        # Проверяем токен
        if not authorization.startswith("Bearer "):
            raise HTTPException(status_code=401, detail="Неверный формат токена")

        token = authorization.replace("Bearer ", "")
        payload = user_service.verify_token(token)

        if not payload:
            raise HTTPException(status_code=401, detail="Неверный токен")

        user_id = int(payload.get("sub", 0))

        if len(logs) > settings.MAX_LOGS_PER_REQUEST:
            return UploadLogsResponse(
                status="error",
                saved=0,
                message=f"Слишком много логов (макс. {settings.MAX_LOGS_PER_REQUEST})"
            )

        saved = log_service.save_logs(logs, user_id)
        return UploadLogsResponse(
            status="ok",
            saved=saved,
            message=f"Сохранено {saved} записей"
        )

    except HTTPException:
        raise
    except Exception as e:
        return UploadLogsResponse(
            status="error",
            saved=0,
            message=str(e)
        )


@router.get("/logs/count")
async def get_logs_count(authorization: str = Header(...)):
    """
    Получить количество логов пользователя
    """
    try:
        if not authorization.startswith("Bearer "):
            raise HTTPException(status_code=401, detail="Неверный формат токена")

        token = authorization.replace("Bearer ", "")
        payload = user_service.verify_token(token)

        if not payload:
            raise HTTPException(status_code=401, detail="Неверный токен")

        user_id = int(payload.get("sub", 0))
        count = log_service.get_count(user_id)

        return {"count": count}

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/model/latest", response_model=ModelInfo)
async def get_latest_model(authorization: str = Header(...)):
    """
    Получить информацию о последней версии модели для пользователя
    """
    try:
        if not authorization.startswith("Bearer "):
            raise HTTPException(status_code=401, detail="Неверный формат токена")

        token = authorization.replace("Bearer ", "")
        payload = user_service.verify_token(token)

        if not payload:
            raise HTTPException(status_code=401, detail="Неверный токен")

        user_id = int(payload.get("sub", 0))

        # Получаем модель пользователя
        version_info = model_service.get_latest_version(user_id)

        if not version_info:
            # Если у пользователя нет своей модели, возвращаем базовую
            version_info = model_service.get_base_model_info()

            if not version_info:
                raise HTTPException(status_code=404, detail="Модель не найдена")

        return ModelInfo(
            version=version_info["version"],
            url=f"/api/model/download",
            size_mb=version_info.get("size_mb", 0),
            created_at=version_info.get("created_at", ""),
            accuracy=version_info.get("accuracy"),
            mae=version_info.get("mae"),
            samples=version_info.get("samples")
        )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/model/download")
async def download_model(authorization: str = Header(...)):
    """
    Скачать последнюю модель пользователя в формате .tflite
    """
    try:
        if not authorization.startswith("Bearer "):
            raise HTTPException(status_code=401, detail="Неверный формат токена")

        token = authorization.replace("Bearer ", "")
        payload = user_service.verify_token(token)

        if not payload:
            raise HTTPException(status_code=401, detail="Неверный токен")

        user_id = int(payload.get("sub", 0))

        # Получаем модель пользователя
        model_path = model_service.get_latest_model_path(user_id)

        if not model_path or not os.path.exists(model_path):
            # Если нет, отдаём базовую модель
            model_path = model_service.get_base_model_path()

            if not model_path or not os.path.exists(model_path):
                raise HTTPException(status_code=404, detail="Файл модели не найден")

        return FileResponse(
            path=model_path,
            filename="forgetting_model.tflite",
            media_type="application/octet-stream"
        )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/model/train", response_model=TrainResponse)
async def trigger_training(authorization: str = Header(...)):
    """
    Запустить обучение модели для пользователя
    """
    try:
        if not authorization.startswith("Bearer "):
            raise HTTPException(status_code=401, detail="Неверный формат токена")

        token = authorization.replace("Bearer ", "")
        payload = user_service.verify_token(token)

        if not payload:
            raise HTTPException(status_code=401, detail="Неверный токен")

        user_id = int(payload.get("sub", 0))

        if model_service.is_training(user_id):
            return TrainResponse(
                status="already_running",
                message="Обучение уже запущено"
            )

        # Запускаем в отдельном потоке
        import threading
        thread = threading.Thread(target=model_service.train_model, args=(user_id,))
        thread.start()

        return TrainResponse(
            status="started",
            message="Обучение запущено. Проверьте статус через /model/status"
        )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/model/fine-tune", response_model=TrainResponse)
async def trigger_fine_tune(authorization: str = Header(...)):
    """
    Запустить дообучение модели на данных пользователя
    """
    try:
        if not authorization.startswith("Bearer "):
            raise HTTPException(status_code=401, detail="Неверный формат токена")

        token = authorization.replace("Bearer ", "")
        payload = user_service.verify_token(token)

        if not payload:
            raise HTTPException(status_code=401, detail="Неверный токен")

        user_id = int(payload.get("sub", 0))

        # Проверяем, есть ли достаточно логов
        count = log_service.get_count(user_id)
        if count < 50:
            return TrainResponse(
                status="insufficient_data",
                message=f"Недостаточно данных. Нужно минимум 50 логов, у вас {count}"
            )

        if model_service.is_training(user_id):
            return TrainResponse(
                status="already_running",
                message="Обучение уже запущено"
            )

        # Запускаем в отдельном потоке
        import threading
        thread = threading.Thread(target=model_service.fine_tune_model, args=(user_id,))
        thread.start()

        return TrainResponse(
            status="started",
            message="Дообучение запущено. Проверьте статус через /model/status"
        )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/model/status", response_model=TrainingStatus)
async def get_training_status(authorization: str = Header(...)):
    """
    Получить статус обучения для пользователя
    """
    try:
        if not authorization.startswith("Bearer "):
            raise HTTPException(status_code=401, detail="Неверный формат токена")

        token = authorization.replace("Bearer ", "")
        payload = user_service.verify_token(token)

        if not payload:
            raise HTTPException(status_code=401, detail="Неверный токен")

        user_id = int(payload.get("sub", 0))

        status = model_service.get_training_status(user_id)
        return TrainingStatus(
            is_running=status.get("is_running", False),
            progress=status.get("progress"),
            message=status.get("message", "Готов к работе")
        )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/health")
async def health_check():
    """
    Проверка здоровья сервера
    """
    try:
        return {
            "status": "healthy",
            "logs_count": log_service.get_total_count(),
            "users_count": user_service.get_users_count(),
            "model_version": model_service.get_latest_version(),
            "timestamp": str(datetime.now())
        }
    except Exception as e:
        return {
            "status": "degraded",
            "error": str(e)
        }

@router.get("/")
async def root():
    """
    Корневой эндпоинт
    """
    return {
        "status": "ok",
        "message": "ML Spaced Repetition Server is running",
        "version": "1.0.0",
        "docs": "/docs"
    }

from core.auth import AuthService, create_access_token, verify_token

auth_service = AuthService()

@router.post("/auth/register", response_model=AuthResponse)
async def register(user: UserRegister):
    """
    Регистрация нового пользователя
    """
    try:
        print(f"🔴 ПОЛУЧЕН ЗАПРОС НА РЕГИСТРАЦИЮ: {user.username}")
        print(f"🔴 Email: {user.email}")
        print(f"🔴 Пароль: {'*' * len(user.password)}")

        result = user_service.create_user(
            username=user.username,
            password=user.password,
            email=user.email
        )

        print(f"🔴 Результат create_user: {result}")

        if not result:
            print(f"❌ Пользователь {user.username} уже существует")
            raise HTTPException(status_code=400, detail="Пользователь уже существует")

        print(f"✅ Пользователь {user.username} создан с ID: {result['id']}")

        token = user_service.create_access_token(
            user_id=result["id"],
            username=result["username"]
        )

        print(f"✅ Токен создан: {token[:20]}...")

        response = AuthResponse(
            status="success",
            user=UserResponse(
                id=result["id"],
                username=result["username"],
                email=result.get("email"),
                created_at=result["created_at"]
            ),
            access_token=token,
            token_type="bearer"
        )

        print(f"✅ Ответ готов: {response}")
        return response

    except HTTPException:
        raise
    except Exception as e:
        print(f"❌ ОШИБКА: {e}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/auth/login", response_model=AuthResponse)
async def login(user: UserLogin):
    """
    Вход пользователя
    """
    try:
        print(f"🔴 ПОЛУЧЕН ЗАПРОС НА ВХОД: {user.username}")
        print(f"🔴 Пароль: {'*' * len(user.password)}")

        user_data = user_service.authenticate_user(
            username=user.username,
            password=user.password
        )

        print(f"🔴 Результат authenticate_user: {user_data}")

        if not user_data:
            print(f"❌ Неверный логин или пароль для {user.username}")
            raise HTTPException(status_code=401, detail="Неверный логин или пароль")

        print(f"✅ Пользователь {user.username} аутентифицирован")

        token = user_service.create_access_token(
            user_id=user_data["id"],
            username=user_data["username"]
        )

        print(f"✅ Токен создан: {token[:20]}...")

        response = AuthResponse(
            status="success",
            user=UserResponse(
                id=user_data["id"],
                username=user_data["username"],
                email=user_data.get("email"),
                created_at=user_data["created_at"]
            ),
            access_token=token,
            token_type="bearer"
        )

        print(f"✅ Ответ готов")
        return response

    except HTTPException:
        raise
    except Exception as e:
        print(f"❌ ОШИБКА ВХОДА: {e}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/auth/me", response_model=UserResponse)
async def get_current_user(authorization: str = Header(...)):
    """
    Получение информации о текущем пользователе
    """
    try:
        # Извлекаем токен из заголовка "Bearer <token>"
        if not authorization.startswith("Bearer "):
            raise HTTPException(status_code=401, detail="Неверный формат токена")

        token = authorization.replace("Bearer ", "")
        payload = user_service.verify_token(token)

        if not payload:
            raise HTTPException(status_code=401, detail="Неверный токен")

        user_id = int(payload.get("sub", 0))
        user_data = user_service.get_user_by_id(user_id)

        if not user_data:
            raise HTTPException(status_code=404, detail="Пользователь не найден")

        return UserResponse(
            id=user_data["id"],
            username=user_data["username"],
            email=user_data.get("email"),
            created_at=user_data["created_at"]
        )

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))