from fastapi import APIRouter
from app.services.prediction_service import prediction_service

router = APIRouter()


@router.get("")
def health_check():
    return {
        "status": "UP",
        "service": "prediction-engine",
        "model_loaded": prediction_service.is_ready()
    }
