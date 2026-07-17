"""
Pydantic модели для API запросов и ответов
"""

from pydantic import BaseModel
from typing import Optional
from datetime import datetime


class LogEntry(BaseModel):
    """Одна запись лога от Android-приложения"""
    card_id: int
    question_type: str
    card_text_length: int
    has_formulas: bool
    difficulty_score: float
    response_time_ms: int
    hour_of_day: int
    day_of_week: int
    fatigue_level: float
    cards_reviewed_in_session: int
    total_reviews_before: int
    correct_rate_before: float
    streak_correct_before: int
    days_since_last_review: int
    linked_cards_count: int
    linked_cards_mastery: float
    was_correct: bool
    timestamp: Optional[int] = None


class UploadLogsResponse(BaseModel):
    """Ответ на загрузку логов"""
    status: str
    saved: int
    message: Optional[str] = None


class ModelInfo(BaseModel):
    """Информация о версии модели"""
    version: int
    url: str
    size_mb: float
    created_at: str
    accuracy: Optional[float] = None
    mae: Optional[float] = None
    samples: Optional[int] = None


class TrainingStatus(BaseModel):
    """Статус обучения модели"""
    is_running: bool
    progress: Optional[float] = None
    message: Optional[str] = None


class TrainResponse(BaseModel):
    """Ответ на запуск обучения"""
    status: str
    message: str

class UserRegister(BaseModel):
    username: str
    password: str
    email: Optional[str] = None

class UserLogin(BaseModel):
    username: str
    password: str

class UserResponse(BaseModel):
    id: int
    username: str
    email: Optional[str] = None
    created_at: str

class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user_id: int
    username: str

class AuthResponse(BaseModel):
    status: str
    user: UserResponse
    access_token: str
    token_type: str