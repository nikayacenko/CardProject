"""
ML Spaced Repetition Server
FastAPI сервер для сбора логов и управления ML-моделью
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

from api.routes import router
from core.config import settings

app = FastAPI(
    title="ML Spaced Repetition Server",
    description="Сервер для сбора логов и дообучения ML-модели интервальных повторений",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

# Настройка CORS для Android-приложения
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # В продакшене лучше ограничить доменом
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Подключаем роутеры с префиксом /api
app.include_router(router, prefix="/api")

@app.get("/")
async def root():
    """Проверка работоспособности сервера"""
    return {
        "status": "ok",
        "message": "ML Spaced Repetition Server is running",
        "version": "1.0.0",
        "docs": "/docs"
    }

if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=settings.DEBUG
    )