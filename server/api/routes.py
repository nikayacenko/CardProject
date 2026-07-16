"""
API эндпоинты
"""

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse
from typing import List
import os

from .models import (
    LogEntry, UploadLogsResponse, ModelInfo,
    TrainingStatus, TrainResponse
)
from services.log_service import LogService
from services.model_service import ModelService
from core.config import settings

router = APIRouter()
log_service = LogService()
model_service = ModelService()


@router.post("/logs/upload", response_model=UploadLogsResponse)
async def upload_logs(logs: List[LogEntry]):
    """
    Загрузить логи с Android-приложения
    """
    try:
        if len(logs) > settings.MAX_LOGS_PER_REQUEST:
            return UploadLogsResponse(
                status="error",
                saved=0,
                message=f"Слишком много логов (макс. {settings.MAX_LOGS_PER_REQUEST})"
            )

        saved = log_service.save_logs(logs)
        return UploadLogsResponse(
            status="ok",
            saved=saved,
            message=f"Сохранено {saved} записей"
        )
    except Exception as e:
        return UploadLogsResponse(
            status="error",
            saved=0,
            message=str(e)
        )


@router.get("/logs/count")
async def get_logs_count():
    """
    Получить количество собранных логов
    """
    return {"count": log_service.get_count()}


@router.get("/model/latest", response_model=ModelInfo)
async def get_latest_model():
    """
    Получить информацию о последней версии модели
    """
    version_info = model_service.get_latest_version()
    if not version_info:
        raise HTTPException(status_code=404, detail="Модель не найдена")
    return version_info


@router.get("/model/download")
async def download_model():
    """
    Скачать последнюю модель в формате .tflite
    """
    model_path = model_service.get_latest_model_path()
    if not model_path or not os.path.exists(model_path):
        raise HTTPException(status_code=404, detail="Файл модели не найден")

    return FileResponse(
        path=model_path,
        filename="forgetting_model.tflite",
        media_type="application/octet-stream"
    )


@router.post("/model/train", response_model=TrainResponse)
async def trigger_training():
    """
    Запустить обучение модели (может быть долгим)
    """
    if model_service.is_training():
        return TrainResponse(
            status="already_running",
            message="Обучение уже запущено"
        )

    # Запускаем синхронную функцию (не await!)
    model_service.train_model()  # ← УБРАТЬ await

    return TrainResponse(
        status="started",
        message="Обучение запущено. Проверьте статус через /model/status"
    )


@router.post("/model/fine-tune", response_model=TrainResponse)
async def trigger_fine_tune():
    """
    Запустить дообучение модели на новых данных
    """
    if model_service.is_training():
        return TrainResponse(
            status="already_running",
            message="Обучение уже запущено"
        )

    model_service.fine_tune_model()

    return TrainResponse(
        status="started",
        message="Дообучение запущено"
    )


@router.get("/model/status", response_model=TrainingStatus)
async def get_training_status():
    """
    Получить статус обучения
    """
    return model_service.get_training_status()


@router.get("/health")
async def health_check():
    """
    Проверка здоровья сервера
    """
    return {
        "status": "healthy",
        "logs_count": log_service.get_count(),
        "model_version": model_service.get_latest_version()
    }